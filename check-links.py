#!/usr/bin/env python3
"""Cross-checks Thymeleaf links/forms against the Spring MVC routes.

Extracts every @GetMapping/@PostMapping/... path from src/main/java and every
th:href / th:action / href / action from src/main/resources/templates, then reports links that
point at a path no controller serves.

Usage:  python3 check-links.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JAVA = ROOT / "src/main/java"
TPL = ROOT / "src/main/resources/templates"

# Paths that are not Spring MVC routes at all.
SKIP_PREFIX = ("/css", "/js", "/images", "/uploads", "/actuator", "/favicon", "/error")
SKIP_EXACT = {"/", "/login", "/logout"}

# @GetMapping, @PostMapping, ..., optionally with an argument list.
ANNOT = re.compile(r'@(Get|Post|Put|Delete|Patch|Request)Mapping(?:\s*\(([^)]*)\))?')
CLASS_DECL = re.compile(
    r'@(Get|Post|Put|Delete|Patch|Request)Mapping(?:\s*\(([^)]*)\))?[^;]*?class\s+\w+'
)
STRING = re.compile(r'"([^"]*)"')
LINK_ATTR = re.compile(r'(?:th:href|th:action|href|action)\s*=\s*"([^"]*)"')
VAR_SEGMENT = re.compile(r"\{[^}]*\}")


def _paths_in(args):
    """The path patterns declared by one mapping annotation's argument list."""
    if args is None:
        return [""]
    found = STRING.findall(args)
    if not found:
        return [""]
    # {"", "/dashboard"} or "/a", "/b" -> split every literal, strip the braces
    out = []
    for literal in found:
        body = literal.strip().strip("{}").strip()
        for part in body.split(","):
            part = part.strip()
            if part:
                out.append(part)
    return out or [""]


def _join(base, path):
    full = (base.rstrip("/") + "/" + path.lstrip("/")) if base else path
    full = re.sub(r"/{2,}", "/", full)
    return full.rstrip("/") or "/"


def collect_routes():
    routes = set()
    for path in JAVA.rglob("*.java"):
        text = path.read_text()
        # Only the mapping that annotates the class declaration is a class-level base path.
        class_base = ""
        cm = CLASS_DECL.search(text)
        if cm:
            paths = _paths_in(cm.group(2) if cm.lastindex and cm.lastindex >= 2 else None)
            class_base = _join("", paths[0]) if paths else ""
        for m in ANNOT.finditer(text):
            for literal in _paths_in(m.group(2)):
                routes.add(_join(class_base, literal))
    return routes


def collect_links():
    links = []
    for path in TPL.rglob("*.html"):
        for i, line in enumerate(path.read_text().splitlines(), 1):
            for m in LINK_ATTR.finditer(line):
                links.append((path, i, m.group(1)))
    return links


def normalise(target):
    """Reduces a Thymeleaf link to the comparable path pattern."""
    for prefix in ("@{", "~{"):
        if target.startswith(prefix):
            target = target[len(prefix):]
    if not target.startswith("/"):
        return None
    # Drop link-expression parameters and any in-page anchor.
    for sep in ("(", "?", "#"):
        idx = target.find(sep)
        if idx != -1:
            target = target[:idx]
    # Path variables match any single segment, so they cannot hide a real mismatch.
    target = VAR_SEGMENT.sub("*", target)
    # The closing brace of the @{...} expression itself.
    if target.endswith("}"):
        target = target[:-1]
    return target.rstrip("/") or "/"


def matches(candidate, route):
    rc, rr = candidate.split("/"), route.split("/")
    if len(rc) != len(rr):
        return False
    return all(c == "*" or r == "*" or c == r for c, r in zip(rc, rr))


def main():
    routes = collect_routes()
    problems = 0
    for path, line, target in collect_links():
        if target.startswith(("http://", "https://", "mailto:", "${", "javascript:")):
            continue
        norm = normalise(target)
        if norm is None or norm in SKIP_EXACT or norm.startswith(SKIP_PREFIX):
            continue
        if any(matches(norm, r) for r in routes):
            continue
        problems += 1
        print(f"BROKEN  {path.relative_to(ROOT)}:{line}  {norm}")
    print(f"\nroutes: {len(routes)}   broken links: {problems}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

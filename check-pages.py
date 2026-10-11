#!/usr/bin/env python3
"""Logs in over HTTP with a cookie jar and probes a list of paths.

Used to verify that protected Thymeleaf pages actually render, which a plain request
cannot do because it is redirected to the login page.

Usage: python3 check-pages.py <email> <password> <path> [<path> ...]
"""
import html
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

BASE = "http://localhost:8080"


def build_opener():
    jar = CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def get(opener, path):
    req = urllib.request.Request(BASE + path, headers={"User-Agent": "page-check"})
    try:
        with opener.open(req, timeout=25) as resp:
            return resp.status, resp.read().decode("utf-8", "replace"), resp.geturl()
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode("utf-8", "replace"), path


def login(opener, email, password):
    status, body, _ = get(opener, "/login")
    if status != 200:
        raise SystemExit(f"login page returned {status}, cannot extract the CSRF token")

    match = re.search(r'name="_csrf"[^>]*value="([^"]+)"', body) or re.search(
        r'value="([^"]+)"[^>]*name="_csrf"', body)
    if not match:
        raise SystemExit("no CSRF token on the login page")

    payload = urllib.parse.urlencode(
        {"email": email, "password": password, "_csrf": html.unescape(match.group(1))}
    ).encode()
    req = urllib.request.Request(
        BASE + "/login", data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded"})
    with opener.open(req, timeout=25) as resp:
        return resp.status, resp.geturl()


def main():
    if len(sys.argv) < 4:
        raise SystemExit(__doc__)
    email, password, paths = sys.argv[1], sys.argv[2], sys.argv[3:]

    opener = build_opener()
    status, landed = login(opener, email, password)
    if "/login" in landed:
        raise SystemExit(f"login FAILED: still on {landed}")
    print(f"logged in as {email} -> {landed} (HTTP {status})\n")

    failures = 0
    for path in paths:
        status, body, _ = get(opener, path)
        flag = "OK  " if status == 200 else "FAIL"
        if status != 200:
            failures += 1
        # Surface the server-side cause instead of just the status code.
        cause = ""
        if status != 200:
            match = re.search(r"Caused by:[^\n]*", body)
            if match:
                cause = match.group(0).strip()[:150]
            else:
                title = re.search(r"<h1[^>]*>(.*?)</h1>", body, re.S)
                if title:
                    cause = re.sub(r"<[^>]+>", " ", title.group(1)).strip()[:100]
        size = len(body)
        print(f"{flag} {status}  {size:>7}b  {path}" + (f"   {cause}" if cause else ""))

    print(f"\n{len(paths) - failures}/{len(paths)} rendered")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
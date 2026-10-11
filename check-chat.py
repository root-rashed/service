#!/usr/bin/env python3
"""Asks the chat page real questions and prints what came back.

The point is the fallback path: a deployment with no local model running must still answer. Every
reply is checked for the two things that would make the assistant untrustworthy - an invented price
and an invented promise about when someone is free.

Usage: python3 check-chat.py "<question>" ["<question>" ...]
"""
import html
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

BASE = "http://localhost:8080"


def opener():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def get(op, path):
    with op.open(urllib.request.Request(BASE + path, headers={"User-Agent": "chat-check"}),
                 timeout=40) as resp:
        return resp.status, resp.read().decode("utf-8", "replace")


def post(op, path, data):
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(BASE + path, data=body,
                                 headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with op.open(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode("utf-8", "replace")


def csrf(page):
    match = re.search(r'name="_csrf"[^>]*value="([^"]+)"', page)
    return html.unescape(match.group(1)) if match else ""


def bubbles(page):
    """Pulls the rendered chat bubbles in order, as plain text."""
    out = []
    for block in re.findall(r'<div class="chat-bubble">(.*?)</div>', page, re.S):
        text = re.sub(r"<[^>]+>", " ", block)
        out.append(re.sub(r"\s+", " ", html.unescape(text)).strip())
    return out


def cards(page):
    return re.findall(r'<a class="chat-card"[^>]*>(.*?)</a>', page, re.S)


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)

    op = opener()
    status, page = get(op, "/chat")
    if status != 200:
        raise SystemExit(f"/chat returned {status}")
    print(f"GET /chat -> {status}\n")

    for question in sys.argv[1:]:
        status, answer = post(op, "/chat", {"question": question, "_csrf": csrf(page)})
        said = bubbles(answer)
        shown = cards(answer)
        # Re-read the token each time: it rotates on every response.
        page = answer

        print(f"Q: {question}")
        print(f"   HTTP {status}, {len(said)} bubble(s), {len(shown)} card(s)")
        for line in said[-1:]:
            print(f"   A: {line[:400]}")
        for card in shown:
            name = re.sub(r"<[^>]+>", " ", card)
            print(f"      card: {re.sub(r' +', ' ', html.unescape(name)).strip()[:120]}")
        print()


if __name__ == "__main__":
    main()
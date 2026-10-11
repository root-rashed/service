#!/usr/bin/env python3
"""Logs in, crawls the admin list pages for real record ids, then opens the detail pages.

The detail templates only fail on real data (a null field the list page never touches), so they
need actual ids rather than a hand-written guess.

Usage: python3 check-detail-pages.py <email> <password>
"""
import html
import re
import sys
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

BASE = "http://localhost:8080"


def build_opener():
    return urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(CookieJar()))


def get(opener, path):
    req = urllib.request.Request(BASE + path, headers={"User-Agent": "page-check"})
    try:
        with opener.open(req, timeout=25) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode("utf-8", "replace")


def login(opener, email, password):
    status, body = get(opener, "/login")
    token = re.search(r'name="_csrf"[^>]*value="([^"]+)"', body).group(1)
    payload = urllib.parse.urlencode(
        {"email": email, "password": password, "_csrf": html.unescape(token)}).encode()
    req = urllib.request.Request(
        BASE + "/login", data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded"})
    opener.open(req, timeout=25)


def find_ids(opener, list_path, pattern):
    """Collects record ids from the row links of an admin list page."""
    _, body = get(opener, list_path)
    return re.findall(pattern, body)


def main():
    email, password = sys.argv[1], sys.argv[2]
    opener = build_opener()
    login(opener, email, password)

    targets = []
    user_ids = find_ids(opener, "/admin/users", r"/admin/users/([0-9a-f]{24})")
    targets += [f"/admin/users/{i}" for i in user_ids[:3]]
    worker_ids = find_ids(opener, "/admin/workers", r"/admin/workers/([0-9a-f]{24})")
    targets += [f"/admin/workers/{i}" for i in worker_ids[:3]]
    booking_ids = find_ids(opener, "/admin/bookings", r"/admin/bookings/([0-9a-f]{24})")
    targets += [f"/admin/bookings/{i}" for i in booking_ids[:3]]

    if not targets:
        print("No records in the database yet, so there are no detail pages to open.")
        return 0

    failures = 0
    for path in targets:
        status, body = get(opener, path)
        ok = status == 200
        failures += 0 if ok else 1
        cause = ""
        if not ok:
            match = re.search(r"Caused by:[^\n]*", body)
            cause = match.group(0).strip()[:150] if match else ""
        print(f"{'OK  ' if ok else 'FAIL'} {status} {len(body):>7}b  {path}"
              + (f"   {cause}" if cause else ""))

    print(f"\n{len(targets) - failures}/{len(targets)} rendered")
    return 1 if failures else 0


if __name__ == "__main__":
    import urllib.error
    sys.exit(main())
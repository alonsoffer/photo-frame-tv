#!/usr/bin/env python3
"""
Verify the Google Drive OAuth configuration for Photo Frame — locally, without the TV.

What this DOES verify (the parts a desktop test can prove):
  • Google Drive API is enabled in your project
  • The OAuth consent screen has the drive.readonly scope
  • Your account is allowed to grant it (i.e. you're a Test user, or the app is published)
  • An access token with drive.readonly actually works against the Drive API

What this does NOT verify:
  • The *Android* OAuth client (package name + SHA-1). That is validated only on the TV by
    Google Play Services at runtime. So: if this script PASSES but the TV still shows error 8,
    the remaining problem is the Android OAuth client registration — nothing else.

Requirements:
  • A **Desktop app** OAuth client (Google Cloud → Credentials → OAuth client ID → type
    "Desktop app"). The "TVs and Limited Input" client can't do this loopback flow.
    Download its JSON and pass the path below.
  • Uses only the Python standard library. A local browser (this machine has one).

Usage:
  python3 tools/verify_drive_config.py /path/to/desktop_client_secret.json
"""

import http.server
import json
import socket
import sys
import threading
import urllib.parse
import urllib.request
import webbrowser

SCOPE = "https://www.googleapis.com/auth/drive.readonly"
AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth"
TOKEN_URI = "https://oauth2.googleapis.com/token"
DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"


def load_client(path):
    with open(path) as f:
        data = json.load(f)
    node = data.get("installed") or data.get("web")
    if not node:
        sys.exit("ERROR: JSON is not an OAuth client secret (missing 'installed'/'web' key).")
    return node["client_id"], node["client_secret"]


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


class CodeCatcher(http.server.BaseHTTPRequestHandler):
    code = None
    error = None

    def do_GET(self):
        params = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        CodeCatcher.code = params.get("code", [None])[0]
        CodeCatcher.error = params.get("error", [None])[0]
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        msg = "You can close this tab and return to the terminal."
        self.wfile.write(f"<html><body><h2>{msg}</h2></body></html>".encode())

    def log_message(self, *_):
        pass  # keep the terminal clean


def post_form(url, fields):
    body = urllib.parse.urlencode(fields).encode()
    req = urllib.request.Request(url, data=body, method="POST")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)


def get_json(url, token):
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    client_id, client_secret = load_client(sys.argv[1])

    port = free_port()
    redirect_uri = f"http://localhost:{port}/"
    server = http.server.HTTPServer(("127.0.0.1", port), CodeCatcher)

    auth_url = AUTH_URI + "?" + urllib.parse.urlencode({
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "response_type": "code",
        "scope": SCOPE,
        "access_type": "offline",
        "prompt": "consent",
    })

    print("\n=== STEP 1: consent ===")
    print("Opening your browser to grant Drive access…")
    print("If it doesn't open, paste this URL into a browser ON THIS MACHINE:\n")
    print(auth_url + "\n")
    threading.Thread(target=lambda: webbrowser.open(auth_url), daemon=True).start()

    # Wait for the single redirect callback.
    server.handle_request()

    if CodeCatcher.error:
        sys.exit(f"\nFAIL: consent returned error: {CodeCatcher.error}\n"
                 "  → Usually: the drive.readonly scope isn't on the consent screen, or your\n"
                 "    account isn't a Test user. Fix on the OAuth consent screen and retry.")
    if not CodeCatcher.code:
        sys.exit("\nFAIL: no authorization code received.")
    print("PASS: consent granted, authorization code received.")

    print("\n=== STEP 2: exchange code for a Drive access token ===")
    status, tok = post_form(TOKEN_URI, {
        "client_id": client_id,
        "client_secret": client_secret,
        "code": CodeCatcher.code,
        "grant_type": "authorization_code",
        "redirect_uri": redirect_uri,
    })
    if status != 200 or "access_token" not in tok:
        sys.exit(f"FAIL: token exchange failed ({status}): {tok}")
    granted = tok.get("scope", "")
    print("PASS: got an access token.")
    print(f"  granted scope: {granted}")
    if "drive.readonly" not in granted and "auth/drive" not in granted:
        print("  WARNING: drive scope not present in the grant — check the consent screen.")

    print("\n=== STEP 3: call the Drive API ===")
    q = "mimeType='application/vnd.google-apps.folder' and trashed=false"
    url = DRIVE_FILES + "?" + urllib.parse.urlencode({
        "q": q, "pageSize": 10, "fields": "files(id,name)",
    })
    status, res = get_json(url, tok["access_token"])
    if status != 200:
        reason = res.get("error", {}).get("message", res)
        sys.exit(f"FAIL: Drive API call failed ({status}): {reason}\n"
                 "  → If this says the API is disabled, enable 'Google Drive API' in your project.")
    folders = res.get("files", [])
    print(f"PASS: Drive API responded. Found {len(folders)} top-level folder(s):")
    for fdr in folders:
        print(f"    • {fdr['name']}")

    print("\n========================================")
    print("RESULT: Your Drive-side config is CORRECT ✅")
    print("  (Drive API enabled, scope on consent screen, account allowed, token works.)")
    print("  If the TV still shows error 8, the ONLY remaining issue is the ANDROID OAuth")
    print("  client — package 'com.photoframe.tv' + the debug SHA-1. Recheck that entry.")
    print("========================================")


if __name__ == "__main__":
    main()

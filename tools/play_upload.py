#!/usr/bin/env python3
"""Upload a signed AAB to Google Play and roll it out to a track.

Uses the Google Play Android Developer API (androidpublisher v3) via
google-api-python-client. This automates the *upload + release*; two one-time
things it cannot do (no API exists for them) must be done once in the web UI:

  1. Create the app in Play Console (there is no "create app" API).
  2. Create a service account, download its JSON key, and grant it access in
     Play Console -> Users & permissions (so this script can authenticate).

See tools/PLAY_API_SETUP.md for those one-time steps.

Example:
  ./.venv/bin/python tools/play_upload.py \
      --service-account ~/play-sa.json \
      --package com.photoframe.tv \
      --aab app/build/outputs/bundle/release/app-release.aab \
      --track internal \
      --status draft \
      --notes "Initial release"

Tracks: internal | alpha | beta | production
Status: draft (upload but don't roll out) | completed (roll out 100%) |
        inProgress (staged rollout; use with --fraction)
"""
from __future__ import annotations

import argparse
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def main() -> int:
    p = argparse.ArgumentParser(description="Upload an AAB to Google Play and release it.")
    p.add_argument("--service-account", required=True, help="Path to the service-account JSON key.")
    p.add_argument("--package", required=True, help="applicationId, e.g. com.photoframe.tv")
    p.add_argument("--aab", required=True, help="Path to the signed release .aab")
    p.add_argument("--track", default="internal",
                   choices=["internal", "alpha", "beta", "production"])
    p.add_argument("--status", default="draft",
                   choices=["draft", "completed", "inProgress", "halted"],
                   help="draft = upload only; completed = roll out to 100%%.")
    p.add_argument("--fraction", type=float, default=None,
                   help="Staged-rollout fraction 0..1 (only with --status inProgress).")
    p.add_argument("--notes", default=None, help="Release notes (en-US).")
    args = p.parse_args()

    creds = service_account.Credentials.from_service_account_file(
        args.service_account, scopes=[SCOPE])
    # cache_discovery=False avoids a noisy warning when no file cache is available.
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
    edits = service.edits()

    try:
        edit_id = edits.insert(body={}, packageName=args.package).execute()["id"]
        print(f"• Opened edit {edit_id}")

        media = MediaFileUpload(args.aab, mimetype="application/octet-stream", resumable=True)
        bundle = edits.bundles().upload(
            packageName=args.package, editId=edit_id, media_body=media).execute()
        version_code = bundle["versionCode"]
        print(f"• Uploaded AAB — versionCode {version_code}")

        release: dict = {"status": args.status, "versionCodes": [str(version_code)]}
        if args.notes:
            release["releaseNotes"] = [{"language": "en-US", "text": args.notes}]
        if args.status == "inProgress":
            if args.fraction is None:
                sys.exit("--status inProgress requires --fraction (e.g. 0.1)")
            release["userFraction"] = args.fraction

        edits.tracks().update(
            packageName=args.package, editId=edit_id, track=args.track,
            body={"releases": [release]}).execute()
        print(f"• Set track '{args.track}' -> status '{args.status}'"
              + (f" ({args.fraction:.0%})" if args.status == "inProgress" else ""))

        edits.commit(packageName=args.package, editId=edit_id).execute()
        print(f"✓ Committed. versionCode {version_code} is now on the '{args.track}' track.")
        if args.status == "draft":
            print("  (status=draft: open Play Console and 'Start rollout' to release it.)")
        return 0

    except HttpError as e:
        # Surface Google's actual message — it's usually precise about what's wrong.
        print(f"✗ Google Play API error (HTTP {e.resp.status}):\n{e.content.decode(errors='replace')}",
              file=sys.stderr)
        if e.resp.status == 403:
            print("\nHint: 403 usually means the service account isn't granted access to this app in\n"
                  "Play Console -> Users & permissions, or the app/package doesn't exist yet.",
                  file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

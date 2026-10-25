# One-time setup to upload via the Play Developer API

`tools/play_upload.py` automates uploading the AAB and rolling out a track. Two things must be
done once in the web consoles first — there is **no API** to do them:

## A. Create the app in Play Console (once)
1. https://play.google.com/console → **Create app**
2. Name **Photo Frame**, type **App**, **Free** → Create.
3. (You do *not* need to upload anything here — the script does that. But the app must exist.)

## B. Create a service account and authorize it (once)
This gives the script an identity that can upload on your behalf.

1. **Google Cloud Console** → the project linked to your Play account →
   **IAM & Admin → Service Accounts → Create service account**.
   - Name e.g. `play-publisher`. No roles needed in Cloud. Click Done.
2. Open the new service account → **Keys → Add key → Create new key → JSON** → download it.
   Keep this file private (it is git-ignored). Note its path, e.g. `~/play-sa.json`.
3. **Play Console → Users & permissions → Invite new users** →
   paste the service account's email (looks like `play-publisher@<project>.iam.gserviceaccount.com`).
4. Grant it app access to **Photo Frame** with at least:
   - **Release to testing tracks** (for `--track internal/alpha/beta`), and/or
   - **Release to production** (for `--track production`).
   Also enable **Create and manage draft releases**. Save.
   - If you first linked the Cloud project in **Play Console → Setup → API access**, grant access there.
5. Access can take a few minutes to propagate.

## C. Run it
```bash
cd /home/alon/google_tv_slidshow
./.venv/bin/python tools/play_upload.py \
  --service-account ~/play-sa.json \
  --package com.photoframe.tv \
  --aab app/build/outputs/bundle/release/app-release.aab \
  --track internal \
  --status draft \
  --notes "Initial release"
```
- `--status draft` uploads without releasing — review it in Play Console, then Start rollout.
- `--status completed` rolls out to 100% of that track immediately.
- First-ever upload may still need to be accepted via the UI once (Play App Signing prompt); if the
  API rejects the very first bundle, upload it once in the console, then all later runs use this script.

> The AAB still has to pass Play's checks (store listing, Data safety, content rating must be
> completed before a Production rollout). Internal testing needs the least and is the place to start.

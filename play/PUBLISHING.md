# Publishing Photo Frame to the Google Play Store

This guide covers the steps to get the app onto Play. Some steps only you can do (they need your
Google account, a payment, and identity verification).

---

## ⚠️ Read this first: the Google Drive scope decision

The app uses the **restricted** OAuth scope `drive.readonly`. Google treats "restricted" scopes
specially for **publicly published** apps, and this is the single biggest gate — decide your path
before paying for a developer account:

| Path | What it means | Effort / cost |
| --- | --- | --- |
| **A. Personal / family use (recommended)** | Keep the OAuth consent screen in **"Testing"** and add yourself + family as test users. **Don't publish to Play** — just sideload the APK (see main README). Works today, free. **Catch:** in Testing mode, the sign-in refresh token expires every **7 days**, so you re-pair weekly. | Free, ~10 min |
| **B. Publish to Play, verified** | Full Google **OAuth verification** + an annual **CASA security assessment** (required for restricted scopes on public apps). Only then do tokens last indefinitely and anyone can install. | Weeks; assessment can cost money |
| **C. Publish to Play, private** | Publish as an **Internal/Closed test** track (or a Google Workspace **private app**) so only accounts you list can install. Restricted-scope verification is generally not required for internal testing tracks. | $25 dev account, minimal review |

For a photo frame on your own wall, **Path A** is almost always the right call — you already have a
working sideloaded app. Pursue **B** only if you want to distribute it to the public.

The rest of this guide assumes you've chosen to go to Play (Path B or C).

---

## 1. One-time account setup (only you can do this)
1. Create a **Google Play Developer account**: https://play.google.com/console/signup — **$25 one-time fee**, plus identity/address verification (can take a couple of days).
2. Accept the Developer Distribution Agreement.

## 2. Set your real OAuth credentials + turn off demo mode
The store build must use real Drive credentials, not demo mode. In `local.properties`:
```properties
DRIVE_CLIENT_ID=xxxx.apps.googleusercontent.com
DRIVE_CLIENT_SECRET=xxxx
DEMO_MODE=false
```
(See the main README for how to create the OAuth client.)

## 3. Build the release App Bundle
Play requires an **.aab** (App Bundle), not an APK:
```bash
./gradlew :app:bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

> Signing is already configured via `keystore.properties` + `upload-keystore.jks` (both git-ignored).
> **Keep `upload-keystore.jks` safe.** When you create the app in Play Console, enable **Play App
> Signing** (default) and this becomes your *upload key* — Google holds the real signing key, and a
> lost upload key can be reset. The auto-generated passwords are in `keystore.properties`.

## 4. Create the app in Play Console
1. Play Console → **Create app** → name "Photo Frame", type **App**, **Free**.
2. **Form factors:** enable **Android TV** (this is what makes it a TV listing).
3. **Store listing:** paste copy from `play/listing/store-listing.md`; upload graphics from
   `play/graphics/` (icon 512, feature graphic 1024×500, **TV banner 1280×720**, and at least one
   **TV screenshot** — capture one with `adb exec-out screencap -p > shot.png` on a device/emulator).
4. **App content:** fill Privacy policy URL (host `play/listing/privacy-policy.md` publicly, e.g.
   GitHub Pages), Data safety form (declares Drive read-only, no data sent to you), content rating,
   target audience, ads = No.
5. **Android TV declaration:** confirm it's designed for TV, D-pad operable, no touch required.

## 5. Upload and roll out
1. **Testing → Internal testing → Create release** (start here even for Path B — validate on the TV first).
2. Upload `app-release.aab`. Add release notes. Save → review → roll out to Internal testing.
3. Add tester Google accounts, share the opt-in link, install from Play on your TV to verify.
4. When ready for the public (Path B, after OAuth verification is approved): promote to
   **Production**. Google reviews TV apps against the Android TV quality guidelines before it goes live.

## Pre-launch checklist
- [ ] Chose a scope path above (A/B/C)
- [ ] `DEMO_MODE=false` + real OAuth credentials in the release build
- [ ] OAuth consent screen configured; verification started if going public (Path B)
- [ ] `app-release.aab` built and signed
- [ ] Privacy policy hosted at a public URL
- [ ] Icon, feature graphic, TV banner, ≥1 TV screenshot uploaded
- [ ] Data safety + content rating + target audience forms completed
- [ ] Internal test install verified on a real TV

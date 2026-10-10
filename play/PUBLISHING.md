# Publishing Photo Frame to the Google Play Store

This guide covers getting the app onto Play. Some steps only you can do (they need your Google
account, a payment, and identity verification).

---

## Good news about the Drive scope

The app uses **`drive.file`**, a **non-sensitive** scope. The app can only touch the "Photo Frame"
folder it creates in your Drive — never your other files. Because it's non-sensitive:

- **No Google OAuth verification is required.**
- **No CASA security assessment is required.**

This is the whole reason the app was built around `drive.file`. The only thing you must do on the
Google side is move the OAuth consent screen to **production** (step 2) so sign-in tokens don't
expire every 7 days. (A local-folder / USB setup needs no Google account at all.)

---

## 1. One-time account setup (only you can do this)
1. Create a **Google Play Developer account**: https://play.google.com/console/signup —
   **$25 one-time fee**, plus identity/address verification (can take a couple of days).
2. Accept the Developer Distribution Agreement.

## 2. Move the OAuth consent screen to production ⚠️ important
In **Google Cloud Console → APIs & Services → OAuth consent screen**:
- Make sure the app's scopes list **only** `.../auth/drive.file` (non-sensitive).
- Click **Publish app** → status **In production**.
- Because the scope is non-sensitive, Google does **not** send you through verification/CASA.

> Why it matters: while the consent screen is in **Testing**, refresh tokens expire after **7 days**
> and the frame disconnects weekly. In **production** they last indefinitely.

## 3. Build the release App Bundle
Play requires an **.aab** (App Bundle), not an APK:
```bash
./gradlew :app:bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

> Signing is configured via `keystore.properties` + `upload-keystore.jks` (both git-ignored).
> **Keep `upload-keystore.jks` safe.** In Play Console enable **Play App Signing** (default) and
> this becomes your *upload key* — Google holds the real signing key, and a lost upload key can be
> reset. The passwords are in `keystore.properties`.

Before building, confirm `local.properties` has `DEMO_MODE=false` and the real
`TV_CLIENT_ID` / `TV_CLIENT_SECRET`.

## 4. Create the app in Play Console
1. Play Console → **Create app** → name "Photo Frame", type **App**, **Free**.
2. **Form factors:** enable **Android TV** (this is what makes it a TV listing). The same APK also
   runs on phones/tablets as the management app; you may enable those form factors too.
3. **Store listing:** paste copy from `play/listing/store-listing.md`; upload graphics from
   `play/graphics/` (icon 512, feature graphic 1024×500, **TV banner 1280×720**, and at least one
   **TV screenshot** — capture with `adb exec-out screencap -p > shot.png` on the TV).
4. **App content:**
   - **Privacy policy URL:** the hosted `docs/privacy-policy.html` (GitHub Pages).
   - **Data safety form:** declares the app accesses the user's photos, stored in the user's own
     Google Drive, **not** sent to you or any third party; no data sold; no analytics/ads.
   - Content rating, target audience, Ads = **No**.
5. **Android TV declaration:** confirm it's designed for TV, D-pad operable, no touch required.

## 5. Upload and roll out
1. **Testing → Internal testing → Create release** (validate on the real TV first).
2. Upload `app-release.aab`, add release notes, roll out to Internal testing.
3. Add tester Google accounts, share the opt-in link, install from Play on your TV + phone.
4. When ready: promote to **Production**. Google reviews TV apps against the Android TV quality
   guidelines before it goes live.

## Pre-launch checklist
- [ ] Google Play developer account created ($25, verified)
- [ ] OAuth consent screen scopes = `drive.file` only, status **In production**
- [ ] `DEMO_MODE=false` + real `TV_CLIENT_ID`/`TV_CLIENT_SECRET` in the release build
- [ ] `app-release.aab` built and signed
- [ ] Privacy policy hosted at a public URL
- [ ] Icon, feature graphic, TV banner, ≥1 TV screenshot uploaded
- [ ] Data safety + content rating + target audience forms completed
- [ ] Internal test install verified on a real TV and phone

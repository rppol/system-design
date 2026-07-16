# LORA — offline Android APK

A raw-WebView wrapper (no Capacitor/frameworks) that packages the whole study
repo + the learning game into one offline APK. `scripts/build_android_assets.sh`
mirrors `src/main/java/com/rutik/systemdesign/` into `app/src/main/assets/www`
and vendors Mermaid, then Gradle builds a signed release.

## Get the APK

Every push to `main` builds and publishes a GitHub Release automatically. The
stable, always-latest download URL is:

```
https://github.com/rppol/system-design/releases/latest/download/systemdesign-daily.apk
```

To trigger/download manually instead:

```bash
gh workflow run android-apk.yml            # optional: force a build
gh release download --pattern systemdesign-daily.apk   # grab the latest APK
```

(Or use the web UI: Actions -> "Build Android APK", or the Releases page.)

## Build locally

No Gradle wrapper is committed to this repo (CI uses `gradle/actions/setup-gradle`,
which needs nothing extra). To build locally, install Gradle 8.10+ yourself (or
run `gradle wrapper` once inside `android/` to generate one), then:

```bash
gradle -p android :app:assembleDebug
```

## Install

1. Copy `systemdesign-daily.apk` to the phone (or open the URL in its browser).
2. Tap it; allow "install unknown apps" for that source when prompted.
3. Open **LORA** — it runs fully offline.

## Update

Re-installing a newer APK **over-installs** the old one and preserves all
progress: every build is signed with the same release keystore, so Android
treats it as an in-place update (localStorage survives). `versionCode` is the
git commit count and `versionName` is `YYYY.MM.DD-<shortsha>`, so newer builds
always sort ahead. For tap-to-update, point **Obtainium** at
`https://github.com/rppol/system-design` — it watches Releases and installs new
APKs for you.

## One-time Chrome -> APK progress migration

Progress lives in `localStorage`, which the browser and the APK do **not**
share. To carry existing browser progress into the app once:

1. In the Pages site (Chrome), open **Progress -> Export** and save the JSON.
2. In the APK, open **Progress -> Import** and pick that JSON file.

After that, keep using the APK; its progress is independent and preserved across
updates.

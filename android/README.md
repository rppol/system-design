# LORA — offline Android APK

A raw-WebView wrapper (no Capacitor/frameworks) that packages the whole study
repo + the learning game into one offline APK. `scripts/build_android_assets.sh`
mirrors `src/main/java/com/rutik/systemdesign/` into `app/src/main/assets/www`
and vendors Mermaid, then Gradle builds a signed release.

## Get the APK

[![Download the Android APK](https://img.shields.io/badge/Download-Android%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/rppol/system-design/releases/latest/download/systemdesign-daily.apk) [![Latest build](https://img.shields.io/github/v/release/rppol/system-design?style=for-the-badge&label=Latest&color=1f6feb)](https://github.com/rppol/system-design/releases/latest)

Every push to `main` triggers a build, and the newest push wins: builds are
cancel-in-progress, so a burst of commits publishes one release for the last of
them rather than one per commit (which is why release numbers skip). The button
above always downloads the newest build; the stable URL behind it is:

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

You need **JDK 17** (AGP 8.7.2 rejects newer JDKs), **Gradle 8.10+**, and an
Android SDK with `platforms;android-35` + `build-tools;35.0.0`. No Gradle
wrapper is committed (CI uses `gradle/actions/setup-gradle`, which needs
nothing extra); install Gradle yourself, or run `gradle wrapper` once inside
`android/` to generate one.

Then build in two steps — the asset step is not optional:

```bash
bash scripts/build_android_assets.sh     # banks + graphs + content + mermaid
gradle -p android :app:assembleDebug
```

`assets/www` is gitignored and always regenerated, and the question banks and
relatedness graphs are build artifacts too. Skipping the first command produces
an APK that opens to a blank screen (no `index.html` to load); running Gradle
against a stale payload produces one whose reader works but whose quiz is
empty. The script generates everything (via `scripts/build_banks.sh`) and
verifies it before Gradle ever runs.

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

## Reader chrome & back behavior (APK-only)

On the APK, `MainActivity.onPageFinished` stamps `html.is-apk` on the page so the
reader CSS can adapt for the phone WebView: the redundant close (✕) pill is
hidden (the system back gesture closes the reader), the top bar is slimmed, and
the moved controls (Prev/Next, Contents, Find, module list) live in a
thumb-reach **bottom action bar** that, with the slim head, auto-hides while you
scroll down and returns on scroll-up. This is presentation-only — a CSS hook, not
an `app.js` seam (the app never branches on `html.is-apk`), and it is inert on
web (every rule is `display:none` outside the APK phone width).

The hardware/gesture **Back** button walks in-app history while away from Home;
at `#/home` the callback disables itself so predictive back-to-home exits the
app. Back is enabled on any non-Home route even with no WebView history — so a
process-death restore that reopens a chapter never dead-ends: Back there routes
to `#/home` (which closes the reader) instead of calling `finish()`.

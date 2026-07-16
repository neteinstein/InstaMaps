# InstaMaps

Share an Instagram Reel or TikTok video, get a Google Maps pin back.

InstaMaps adds itself to the Android share sheet. Share a video that's advertising a place - a
restaurant, a landmark, a store - and it downloads the video on-device, reads the location off the
screen (storefront signs, text overlays, location stickers) with OCR, geocodes it, and hands you a
deep link straight into Google Maps. Everything runs on-device; there is no backend server.

## How it works

1. **Share target**: an `ACTION_SEND` intent filter (`text/plain`) puts InstaMaps in the OS share
   sheet for any app, including Instagram and TikTok.
2. **Download**: the shared link is resolved and the video is downloaded on-device with yt-dlp
   (via `youtubedl-android`), capped at 480p to keep the download small and decoding fast.
3. **Frame extraction**: `MediaMetadataRetriever` pulls keyframes from the video every couple of
   seconds (`OPTION_CLOSEST_SYNC` + `getScaledFrameAtTime`, so it never decodes a full-resolution
   frame or walks P/B-frames).
4. **OCR + entity extraction**: each frame goes through on-device Google ML Kit (Text Recognition,
   then Entity Extraction) to pull out addresses, place names, and location text.
5. **Geocoding**: the best candidate is resolved to a real place with the Google Places SDK.
6. **Deep link**: a `https://www.google.com/maps/search/?api=1&query=...` link is fired at the
   Google Maps app (falling back to a browser if Maps isn't installed).

The whole pipeline runs in a `WorkManager` background job, so it keeps running even after you
background the app to go back to Instagram/TikTok - a notification pops when the place is found,
tap it to jump straight into Maps.

## Requirements

- Android 8.1+ (API 27) device or emulator.
- A Google Places API key (**Places API (New)** enabled on a Google Cloud project) - entered into
  the app itself at runtime, not needed at build time. See [Setup](#setup).

## Setup

```bash
git clone https://github.com/neteinstein/InstaMaps.git
cd InstaMaps
```

Open the project in Android Studio, or build/run from the command line - no build-time secrets
file is required, the project compiles on a clean checkout with no extra configuration:

```bash
./gradlew assembleDebug
./gradlew installDebug   # with a device/emulator connected
```

InstaMaps needs a Google Places API key to geocode anything. Get one at the
[Google Cloud Console](https://console.cloud.google.com/google/maps-apis) with the
**Places API (New)** enabled, then open the app, tap the settings icon (top right of the main
screen), paste the key into the Places API Key field, and tap Save. The key is stored on-device
with Jetpack DataStore and included in Android's automatic app backup, so it carries over to your
other devices/a reinstall without re-entering it. Until a key is saved, the main screen shows a
warning with a shortcut straight to Settings instead of attempting to geocode.

## Usage

1. Open Instagram or TikTok, find a video advertising a place, tap Share.
2. Pick InstaMaps from the share sheet.
3. Watch the in-app progress animation, or background the app - either way, a notification fires
   once the place is found.
4. Tap the notification (or wait for the in-app auto-open) to land on the pin in Google Maps.

If the Places API key or a required runtime permission is missing, InstaMaps opens to the main
screen instead of processing the video, showing a warning per missing item with a button to fix
it (jump to Settings, grant the permission, or open the system app-settings page if it was
previously denied). Once everything's in place, sharing the same video again - or just waiting,
since the app also resumes automatically once you fix the last warning if you got there without
sharing - continues into the normal pipeline.

## Architecture

Kotlin, Jetpack Compose, MVVM, Coroutines, Koin DI, one Gradle module per feature, Clean
Architecture layering (`domain`/`data`/`presentation`/`di`) within each module. See
[`agents.md`](agents.md) for the full module map, tech stack, and development/testing standards.

## Releases

Every push to `main` (i.e. every merged PR) triggers `.github/workflows/release.yml`: it
re-validates the merged commit (`ktlintCheck`, `test`), builds a signed release APK, and publishes
it as a GitHub Release tagged `v1.0.<run number>` with auto-generated release notes. The APK asset
is renamed to `InstaMaps_version<version>.apk` (dots replaced with underscores, e.g.
`InstaMaps_version1_0_42.apk`), and its SHA-1 checksum is prepended to the release notes.

The workflow requires these repository secrets (**Settings → Secrets and variables → Actions**),
all needed only for signing the release APK - the Places API key is no longer a build-time secret,
see [Setup](#setup):

| Secret              | Value                                                                                |
|----------------------|---------------------------------------------------------------------------------------|
| `KEYSTORE_BASE64`   | Your signing keystore, base64-encoded (e.g. `base64 -i release.keystore \| pbcopy`)    |
| `KEYSTORE_PASSWORD` | Keystore password                                                                      |
| `KEY_ALIAS`         | Alias of the signing key inside the keystore                                          |
| `KEY_PASSWORD`      | Password for that key alias                                                           |

All four are required - the workflow fails fast with a clear error if any are missing, instead of
publishing an unsigned or non-functional release. Local `./gradlew assembleRelease` builds don't
need any of this: they fall back to debug signing (see `app/build.gradle.kts`).

## Contributing

Issues and pull requests are welcome. See [`agents.md`](agents.md) for coding standards, and the
PR template for what to include (description, how to test).

## License

[GNU GPL v3](LICENSE). InstaMaps bundles `youtubedl-android` (yt-dlp), which is GPL-3.0 licensed;
that copyleft obligation extends to this project.

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
   (via `youtubedl-android`), capped at 480p to keep the download small and decoding fast. For
   Instagram links, a saved login session (see [Instagram login](#instagram-login) below) is
   attached to the download to improve reliability against anonymous-request rate limiting.
3. **Frame extraction**: `MediaMetadataRetriever` pulls keyframes from the video every couple of
   seconds (`OPTION_CLOSEST_SYNC` + `getScaledFrameAtTime`, so it never decodes a full-resolution
   frame or walks P/B-frames).
4. **OCR + entity extraction**: each frame goes through on-device Google ML Kit (Text Recognition,
   then Entity Extraction) to pull out addresses, place names, and location text.
5. **Location resolution**: all collected text (caption + OCR) is sent to the Gemini Flash API,
   which identifies every place the video is actually about - usually just one, but a caption or
   OCR pass often surfaces more (the featured spot, a mentioned neighborhood, a chain's other
   locations) - ranked most-to-least likely.
6. **Pick & deep link**: the results screen lists every match found (name, address, and its own
   "Open in Google Maps" button), scrollable if there's more than one - picking one fires a
   `https://www.google.com/maps/search/?api=1&query=...` deep link at the Google Maps app (falling
   back to a browser if Maps isn't installed).

The whole pipeline runs in a `WorkManager` background job, so it keeps running even after you
background the app to go back to Instagram/TikTok - a notification pops once a place is found,
naming the top match (plus "+N more" if the video turned up several) and jumping straight into
Maps for it when tapped. Stay in the app instead and you get to see every match and choose.

## Requirements

- Android 8.1+ (API 27) device or emulator.
- A Gemini API key (free, from Google AI Studio) - entered into the app itself at runtime, not
  needed at build time. See [Getting a Gemini API key](#getting-a-gemini-api-key).
- An Instagram account, if you want to log in to improve download reliability for Instagram links
  - optional, not required. See [Instagram login](#instagram-login).

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

InstaMaps needs a Gemini API key to identify locations, plus a couple of runtime permissions.
The first time you open the app (and again any time one of these is later revoked), it shows an
onboarding screen explaining what InstaMaps does and why each item is needed, with a button right
next to each explanation to resolve it. See [Getting a Gemini API key](#getting-a-gemini-api-key)
below for the key itself - tap its card's button to jump to Settings, paste the key into the
Gemini API Key field, and tap Save. The key is stored on-device with Jetpack DataStore and
included in Android's automatic app backup, so it carries over to your other devices/a reinstall
without re-entering it. The app switches over to its normal main screen automatically the moment
every requirement is resolved.

## Getting a Gemini API key

InstaMaps calls the Gemini API directly from the device (no backend), so you need your own,
free API key:

1. Go to [Google AI Studio's API key page](https://aistudio.google.com/apikey).
2. Sign in with a Google account, accept the terms if prompted, and click **Create API key**.
3. Pick an existing Google Cloud project or let Google AI Studio create one for you - the free
   tier is enough for personal use of InstaMaps.
4. Copy the generated key (starts with `AIza...`).
5. In InstaMaps, open Settings - reachable from the top right of the main screen, or straight from
   the onboarding screen if you haven't added a key yet - paste the key into the Gemini API Key
   field, and tap Save.

Keep the key private - anyone with it can make Gemini API calls billed to your project. InstaMaps
stores it on-device only (Jetpack DataStore) and never sends it anywhere except directly to
Google's Gemini API. If the key ever leaks, revoke/regenerate it from the same
[API key page](https://aistudio.google.com/apikey) and paste the new one into Settings.

### Setting up prepaid billing (optional)

The Free Tier above is enough to try InstaMaps out, but its rate limits are low - if you use the
app a lot and start seeing Gemini rate-limit errors, upgrade your key's project to a Paid Tier by
linking a Google Cloud billing account and prepaying credits:

1. Go to the AI Studio [Billing](https://aistudio.google.com/billing) page (or the
   [API keys](https://aistudio.google.com/api-keys)/[Projects](https://aistudio.google.com/projects)
   page - anywhere you see a **Set up billing** button) and click **Set up billing** next to the
   project your key belongs to.
2. If you've never set up a Google billing account before, select your country, agree to the
   Terms of Service, then fill in your contact info and payment method. If you already have a
   billing account, you can pick it instead of creating a new one.
3. Prepay a minimum of $10 (or the equivalent in your currency) in credits - this is the default
   **Prepay** billing plan. Gemini API usage is deducted from that credit balance in near
   real-time; once it hits $0, every key under that billing account stops working until you buy
   more credits.
4. Optionally, turn on **auto-reload** on the Billing page so credits top up automatically before
   running out, and set a monthly auto-charge limit to cap how much can be auto-reloaded.

This is entirely optional - InstaMaps works fine on the Free Tier for casual, personal use. See
Google's [Gemini API billing guide](https://ai.google.dev/gemini-api/docs/billing) for full
details, current rate limits per tier, and pricing.

### Instagram login

Logging into Instagram is optional - InstaMaps downloads plenty of public content without it -
but improves reliability, since Instagram rate-limits and occasionally blocks anonymous
(logged-out) requests. Because it's optional, it's the one setup nudge that stays on the main
screen rather than the onboarding screen below, shown as a dismissible, deliberately non-red
"Connect Instagram" banner (it's a suggestion, not a warning); tapping it opens a screen with a
real `WebView` pointed at Instagram's own login page. InstaMaps never sees the password you type -
it only detects the session cookie Instagram sets once login succeeds, encrypts it on-device
(AndroidKeystore-backed AES-256-GCM), and stores it separately from your Gemini API key,
deliberately left out of Android's backup/device-transfer so a restored copy on another device
(where the encryption key wouldn't exist) can't leave a broken, undecryptable session behind.

If a specific shared video still needs a login (e.g. an age-gated or otherwise restricted post),
the main screen automatically shows a "Log in to Instagram" prompt instead of a generic error, and
retries that exact video as soon as you log in again.

## Usage

1. Open Instagram or TikTok, find a video advertising a place, tap Share.
2. Pick InstaMaps from the share sheet.
3. Watch the in-app progress animation - it rotates through short lines describing what it's
   currently doing (downloading, reading the screen, asking Gemini, ...) - or background the app;
   either way, a notification fires once the place is found.
4. Pick a result: if the video turned up more than one place, every match is listed (name,
   address, and its own CTA button), scrollable if there are several - tap the one you want to
   open it in Google Maps. Tapping the result notification instead jumps straight into Maps for
   the top match.

If something fails partway through - a download error, no location found in the video, a network
hiccup - the screen explains what went wrong with a **Retry** button that re-runs the exact same
video, instead of having to re-share it from Instagram/TikTok.

If the Gemini API key or a required runtime permission is missing, InstaMaps opens to an
onboarding screen instead of the main screen: a short animation of videos flowing in and a map
coming out explains what the app does, then each requirement gets its own card explaining why
it's needed with a button right there to resolve it (jump to Settings, grant the permission, or
open the system app-settings page if it was previously denied). The moment every requirement is
resolved the app switches over to the normal main screen on its own - no separate "continue" step,
and no need to re-share the video if you got there without one. Revoking a permission later (from
system Settings) brings this same screen back the next time you open InstaMaps.

### History

Every link you share - whether or not a place was found - is kept in a local history list. Tap the
history icon next to Settings (top right of the main screen) to see the last 50: tap an entry to
reopen the original video, or, if a place was found for it, tap **Open in Google Maps** to jump
straight to the top match.

### Updating InstaMaps

Settings has an **Update to latest** button that checks
[GitHub Releases](https://github.com/neteinstein/InstaMaps/releases) for a build newer than the
one you have installed. If one exists, InstaMaps downloads its APK and hands it straight to the
system installer - no need to uninstall first, manually grab the APK from GitHub, or use `adb
install`. If you're already on the latest release, it just tells you so.

Installing an APK from outside the Play Store requires Android's one-time "install unknown apps"
permission for InstaMaps specifically. If it isn't granted yet, pressing **Update to latest** shows
a warning instead of downloading anything, with a button that jumps straight to that permission's
page in system Settings - grant it there, come back, and press **Update to latest** again to
continue.

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
all needed only for signing the release APK - the Gemini API key is not a build-time secret,
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

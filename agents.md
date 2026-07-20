# Android Development Context

## Project Overview

InstaMaps receives a shared Instagram Reel or TikTok video link from the OS share sheet,
downloads the video on-device, extracts the location it advertises (a restaurant, a landmark, a
store) by reading on-screen text with OCR, identifies the location via the Gemini 1.5 Flash API, and deep-links
straight into Google Maps. The whole pipeline (download -> frame extraction -> OCR -> entity
extraction -> geocoding) runs on-device; there is no backend server.

## Architecture

Clean Architecture, one Gradle module per feature, MVVM in the presentation layer. Each feature
module is internally layered as:

- **`domain/`**: use cases, repository interfaces, plain Kotlin domain models. No Android
  framework or third-party SDK types leak into this layer - it must stay unit-testable with
  plain JUnit and no mocking framework required.
- **`data/`**: repository implementations that call into a specific SDK/framework
  (`MediaMetadataRetriever`, ML Kit, Gemini API, yt-dlp).
- **`di/`**: a Koin `module { }` wiring the module's use cases and repositories together.
- Compose UI + ViewModel, for modules that own a screen.

Modules only depend downward: `feature:*` modules depend on `core:common`, and UI-owning modules
also depend on `core:designsystem`. Feature modules never depend on each other directly - shared
logic belongs in `core:common` instead.

### Module map

| Module                  | Layers present     | Responsibility                                                                                            | Status |
|--------------------------|---------------------|------------------------------------------------------------------------------------------------------------|--------|
| `core:common`            | domain, di          | `AppError`, `DispatcherProvider`, `LatLng`, `safeCall` - shared primitives used across every module         | Active |
| `core:designsystem`      | Compose UI, theme   | Shared Compose theme/typography + reusable components (`PrimaryButton`, `LoadingIndicator`, `ErrorMessage`, `WarningBanner`) | Active |
| `core:settings`          | domain, data, di    | `AppSettingsRepository` - persists the user-entered Gemini API key in Jetpack DataStore Preferences (covered by the app's Auto Backup, so it syncs to a user's other devices) | Active |
| `core:instagramauth`     | domain, data, di    | `InstagramAuthRepository` - persists the Instagram session cookie captured by `feature:instagramauth`'s WebView login, encrypting it with an AndroidKeystore-backed AES-256-GCM key (`AndroidKeystoreInstagramSessionCipher`) before it touches Jetpack DataStore Preferences. Deliberately *excluded* from Auto Backup/device transfer (unlike `core:settings`) since the Keystore key never leaves the device and a restored file alone would be undecryptable - see `app`'s `data_extraction_rules.xml`/`full_backup_content.xml` | Active |
| `feature:maps`           | domain, di          | Builds the Google Maps deep link (`BuildMapsDeepLinkUseCase`) and launches it (`MapsLauncher`, with a browser fallback if the Maps app isn't installed) | Active |
| `feature:geocoding`      | domain, data, di    | `ResolveLocationUseCase` backed by the Gemini 1.5 Flash REST API (`GeminiLocationRepository`) - sends all collected text (caption + video OCR) to Gemini with a location-identification prompt and returns a `MapsDestination` ready for Google Maps | Active |
| `feature:videoprocessing`| domain, data, di    | Downloads the shared video (yt-dlp), extracts frames (`MediaMetadataRetriever`), OCRs them (ML Kit text recognition + entity extraction), turns raw text into `LocationCandidate`s. `YtDlpVideoDownloadRepository` attaches the persisted Instagram session cookie (`core:instagramauth`) to downloads when one is saved, and classifies a yt-dlp failure as `AppError.AuthenticationRequired` only when the source URL is actually an Instagram host, so a TikTok failure is never misattributed to a missing Instagram login | Active |
| `feature:share`          | domain, presentation, work, di | Parses the shared URL, runs the video pipeline via `feature:videoprocessing`/`feature:geocoding` to resolve a `MapsDestination`, drives it from a `WorkManager` `CoroutineWorker` (survives the app being backgrounded) with a result notification, and renders an animated Compose UI (`ShareScreen`) that mirrors the same job. The idle/main screen also renders readiness warnings (missing API key, missing runtime permissions) with per-item action buttons, and gates auto-starting the pipeline on a shared video until they're resolved. It also shows a non-blocking "Connect Instagram" nudge banner when no session is saved, and reacts to an `AppError.AuthenticationRequired` failure by surfacing a dedicated login prompt (`ShareUiState.AuthRequired`) that automatically retries the same video the moment a fresh session is saved | Active |
| `feature:settings`       | presentation, di    | The Settings screen (`SettingsScreen`/`SettingsViewModel`) the user pastes their Gemini API key into - reachable from the top-right button on `feature:share`'s main screen; delegates persistence to `core:settings` | Active |
| `feature:instagramauth`  | presentation, di    | The Instagram login screen (`InstagramLoginScreen`/`InstagramLoginViewModel`): a `WebView` pointed at Instagram's own login page - InstaMaps never sees the entered password, only detects the `sessionid` cookie once login succeeds, then hands it to `core:instagramauth` to persist | Active |
| `app`                    | presentation        | Composition root: `InstaMapsApplication` starts Koin with every feature/core module; `MainActivity` is the single UI entry point, handling launcher taps, the Instagram/TikTok share target, the result-notification deep-link trampoline into `MapsLauncher`, and switching between the main (`feature:share`), Settings (`feature:settings`), and Instagram login (`feature:instagramauth`) screens | Active |

`settings.gradle.kts` is the source of truth for which modules are part of the build - a module
commented out there is not compiled, tested, or linted, and should not be assumed to exist.

## Tech Stack

- **Language**: Kotlin, JVM target 17
- **Min SDK**: 27; **Compile SDK / Target SDK**: 36
- **UI**: Jetpack Compose (Material 3 via the Compose BOM) - no XML layouts, no ViewBinding
- **Architecture**: MVVM, `ViewModel` + Compose state/`StateFlow` (no LiveData)
- **Concurrency**: Kotlin Coroutines (`viewModelScope`, dispatchers behind `DispatcherProvider`
  so tests can inject a test dispatcher instead of patching `Dispatchers.Main`)
- **DI**: Koin (`koin-android`, `koin-androidx-compose`) - constructor injection into use cases
  and repositories, `koinViewModel()` in Compose. `InstaMapsApplication` (in `app`) is the single
  `startKoin { }` call site, wiring every feature/core module's Koin module together
- **Video download**: yt-dlp via `youtubedl-android`, forced to `-f "bv*+ba/b" -S "res:480"` to keep
  the download small and the on-device decode fast. Deliberately a format *sort* (`-S`), not a
  *filter* (`-f ...[height<=480]`): a filter hard-eliminates every format that doesn't match, which
  fails outright on Instagram Reels/TikTok since both serve near-universally portrait video, where
  `height` is the *larger* dimension - `res` is yt-dlp's orientation-correct metric, "calculated as
  the smallest dimension" (see `YtDlpVideoDownloadRepository`)
- **Instagram authentication**: a `WebView` (`feature:instagramauth`'s `InstagramLoginScreen`) loads
  Instagram's own login page directly - InstaMaps never sees the entered password, only detects the
  resulting `sessionid` cookie via `CookieManager` once login succeeds. The cookie is encrypted at
  rest with an AndroidKeystore-backed AES-256-GCM key (`AndroidKeystoreInstagramSessionCipher`,
  `core:instagramauth`) before being persisted in its own Jetpack DataStore Preferences file, which
  - unlike `core:settings`'s API-key store - is excluded from both cloud backup and device-to-device
  transfer (`app/src/main/res/xml/data_extraction_rules.xml` + `full_backup_content.xml`), since the
  Keystore key itself never leaves the device and a restored copy of the encrypted file alone would
  be undecryptable. `YtDlpVideoDownloadRepository` attaches the saved cookie to yt-dlp downloads
  (Netscape cookie-file format) to improve reliability against Instagram's anonymous-request rate
  limiting, and maps a yt-dlp failure to `AppError.AuthenticationRequired` only when the source URL
  is actually an Instagram host (`feature:share`'s `ShareViewModel` reacts to that specific error by
  prompting to log in again and automatically retrying once a fresh session is saved) - see
  `feature:videoprocessing`'s `ytDlpErrorToAppError`.
- **Frame extraction**: `MediaMetadataRetriever` with `OPTION_CLOSEST_SYNC` (snap to keyframes,
  skip P/B-frame decoding) and `getScaledFrameAtTime` (downscale during decode, not after), fed
  through a coroutine `Channel` producer/consumer pipeline so extraction never blocks on OCR
  (see `MediaMetadataRetrieverFrameExtractor`, `ExtractLocationCandidatesUseCase`)
- **OCR / entity extraction**: Google ML Kit, on-device Text Recognition + Entity Extraction
- **Location resolution**: Gemini 1.5 Flash REST API (via `java.net.HttpURLConnection` + `org.json`) - no additional SDK dependency
- **Background processing**: `WorkManager` (`CoroutineWorker`) runs the download -> OCR -> geocode
  pipeline so it survives the app being backgrounded right after a share (the common case, since
  the user was just in Instagram/TikTok); progress is observed via `getWorkInfoByIdFlow` for the
  in-app animated UI, and a result notification (tap to deep-link straight into Maps) fires
  regardless of whether the app is still in the foreground (see `ProcessSharedUrlWorker`,
  `ShareNotifier`, `ShareViewModel`)
- **Settings persistence**: Jetpack DataStore Preferences (`core:settings`) stores the
  user-entered Gemini API key under the app's `filesDir`, covered by the manifest's existing
  `android:allowBackup="true"` Auto Backup - no separate sync/backup plumbing needed for it to
  carry over to a user's other devices
- **Testing**: JUnit 4, Mockito-Kotlin, `kotlinx-coroutines-test`
- **Linting**: ktlint via `org.jlleitschuh.gradle.ktlint`
- **Coverage**: Kover (`org.jetbrains.kotlinx.kover`)

## Development Setup

No build-time secrets file is required - a clean checkout compiles and runs as-is. The Gemini API
key is entered at runtime: launch the app, tap the settings icon (top right of the main screen),
paste in a key, and tap Save. Get a key at the
[Google AI Studio](https://aistudio.google.com/) with Gemini 1.5 Flash enabled
enabled. Until a key is saved, the main screen shows a warning (with a button straight to
Settings) instead of attempting to geocode - see `feature:share`'s `ShareScreen`/`ShareViewModel`
and `feature:settings`.

Logging into Instagram is optional but recommended, not required to use the app: if no session is
saved, the main screen shows a dismissible "Connect Instagram" banner (tap it to reach
`feature:instagramauth`'s WebView login screen, pointed at Instagram's real login page - InstaMaps
never sees the password, only the resulting session cookie). Public/anonymous-accessible content
still downloads without logging in; if yt-dlp reports that a specific shared video needs a login,
the main screen automatically switches to a "Log in to Instagram" prompt instead and retries that
exact video the moment a fresh session is saved - see `feature:share`'s `ShareViewModel`
(`isInstagramAuthenticated`/`ShareUiState.AuthRequired`) and `core:instagramauth`.

```bash
# Build every active module
./gradlew assembleDebug

# Run unit tests (all modules)
./gradlew test

# Run a single module's tests
./gradlew :feature:videoprocessing:test

# Lint
./gradlew ktlintCheck
./gradlew ktlintFormat   # auto-fix

# Coverage (merged report across all modules, written to build/reports/kover/)
./gradlew koverXmlReport
./gradlew koverHtmlReport   # open build/reports/kover/html/index.html afterwards
```

## Testing Standards

- Unit tests live under each module's `src/test/`. Domain and data layers must be covered by
  plain JUnit tests - this project has no emulator/instrumentation tests, so anything that can't
  be reasonably unit-tested (e.g. framework glue that just calls `context.startActivity`, or
  `AndroidKeystoreInstagramSessionCipher`'s direct calls into the real `AndroidKeyStore` provider)
  should be kept thin rather than pulling in Robolectric. Keep the actual crypto/framework call
  behind a small interface (e.g. `InstagramSessionCipher`) so the class that *uses* it
  (`EncryptedInstagramAuthRepository`) can still be fully unit-tested with a fake.
- Target **80%+ line coverage** on `domain` and `data` packages. `di`, `presentation`, and `work`
  packages, plus `core:designsystem` (pure Compose UI / WorkManager-and-notification glue with no
  business logic worth measuring, none of it exercisable without instrumentation/Robolectric,
  which this project doesn't use), are excluded from the coverage denominator - see the
  `kover { reports { filters { excludes { ... } } } }` block in the root `build.gradle.kts`. When
  you add a new module or a new UI/framework-glue-only package, extend that exclude list rather
  than letting it silently drag the aggregate number down. `app` is excluded as a whole module
  (not added to the `dependencies { kover(project(...)) }` list at all) for the same reason: it is
  a pure composition root with no domain/data logic of its own.
- Use `UnconfinedTestDispatcher`/`StandardTestDispatcher` (from `kotlinx-coroutines-test`) for
  coroutine-based code. Prefer hand-rolled fakes over Mockito mocks for repository interfaces
  where practical - cheaper to read and keep in sync than a mock's stubbing chain.
- The CI coverage job currently reports (uploads the HTML/XML report, posts a job summary)
  rather than blocking merges. Once coverage on the built-out modules is consistently near the
  80% target, promote it to a hard gate with a `kover { reports { verify { rule { minBound(80) }
  } } }` block and a `koverVerify` CI step.

## Code Quality

- ktlint runs on every module (`ktlintCheck` in CI, blocking). Run `ktlintFormat` before
  committing instead of hand-fixing style issues.
- Before writing code against a third-party library/SDK (Koin, ML Kit, Gemini API, yt-dlp,
  AndroidX/Compose), verify the real API surface first - decompile the resolved artifact or read
  real source at the exact version in use, rather than relying on a recalled API shape. Library
  APIs shift between versions often enough that this has repeatedly caught real mismatches during
  this project's build-out (e.g. Gemini API model names, ML Kit entity extraction options, yt-dlp format strings). Library
  assumed `Place.Field.NAME`).
- Prefer constructor injection and interfaces at layer boundaries (`domain` defines the
  repository interface, `data` implements it) so use cases can be unit-tested with fakes instead
  of a DI container or Android framework classes.

## Documentation

Update `README.md` and this file whenever a change affects:

- The module list or dependency graph (new/removed/renamed module)
- The tech stack (new major library, replaced library, minSdk/compileSdk/targetSdk bump)
- The development workflow (new required tool, changed build/test/lint/coverage commands)

Stale docs are worse than no docs - if you're unsure whether a change is architecturally
significant enough to warrant a docs update, err on the side of updating.

## CI/CD

`.github/workflows/pr_checks.yml` runs four independent jobs in parallel on every PR targeting
`main`:

| Job        | What it runs                                                | Blocking? |
|------------|---------------------------------------------------------------|-----------|
| `lint`     | `./gradlew ktlintCheck`                                        | Yes |
| `compile`  | `./gradlew compileDebugKotlin compileDebugUnitTestKotlin`       | Yes |
| `test`     | `./gradlew test` (uploads test reports/results as an artifact) | Yes |
| `coverage` | `./gradlew koverXmlReport koverHtmlReport` (uploads the merged report, posts a coverage % job summary) | No (report-only for now, see Testing Standards) |

A PR can only be merged once `lint`, `compile`, and `test` pass.

`.github/workflows/release.yml` runs on every push to `main` (i.e. every merged PR) and on manual
`workflow_dispatch`. It re-runs `ktlintCheck` and `test` against the exact commit landing on
`main` - necessary because squash/rebase merges produce a commit that PR Checks never directly
built - then builds a release APK signed with a keystore decoded from the `KEYSTORE_BASE64` /
`KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` secrets, and publishes it as a GitHub Release
tagged `v1.0.<run number>`. `versionCode`/`versionName` are overridden at build time via
`APP_VERSION_CODE`/`APP_VERSION_NAME` env vars derived from the run number (see the
`signingConfigs`/`defaultConfig` blocks in `app/build.gradle.kts`). All four secrets are required;
the workflow fails fast if any are missing rather than shipping an unsigned or non-functional
build. The Gemini API key is not part of this workflow - it is a runtime, user-entered value (see
Development Setup), not a build-time secret. The built APK is renamed to
`InstaMaps_version<version>.apk` (dots replaced with underscores, e.g.
`InstaMaps_version1_0_42.apk`) before being uploaded as the release asset, and the APK's SHA-1
checksum is computed and prepended to the auto-generated release notes so it can be verified after
download.

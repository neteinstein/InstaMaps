# Android Development Context

## Project Overview

InstaMaps receives a shared Instagram Reel or TikTok video link from the OS share sheet,
downloads the video on-device, extracts the location it advertises (a restaurant, a landmark, a
store) by reading on-screen text with OCR, identifies the location via the Gemini Flash API, and deep-links
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
| `core:designsystem`      | Compose UI, theme   | Shared Compose theme/typography + reusable components (`PrimaryButton` - its `tone: ButtonTone` (DEFAULT/SUCCESS/ERROR) and `loading` params drive `feature:settings`'s Save-button validation feedback, with the SUCCESS/ERROR container/content colors also set as the *disabled* colors so the green/red confirmation persists even once the button disables itself, and `fillWidth` letting a caller opt out of the full-width default to sit inline next to another control -, `LoadingIndicator`, `ErrorMessage`, `WarningBanner` - its `tone: BannerTone` param picks WARNING (red, current default) vs INFO (secondary-container) styling, e.g. for an optional nudge that shouldn't read as an error, and its `actionLabel`/`onActionClick` are optional (default `null`) so it also covers a purely informational message with nothing to resolve - see `feature:share`'s processing-screen background hint) | Active |
| `core:settings`          | domain, data, di    | `AppSettingsRepository` - persists the user-entered Gemini API key in Jetpack DataStore Preferences (covered by the app's Auto Backup, so it syncs to a user's other devices). Also home to `GeminiApiKeyValidator`/`ValidateGeminiApiKeyUseCase` (`HttpGeminiApiKeyValidator` hits Gemini's lightweight `models.list` endpoint rather than `generateContent`, so checking a key costs no generation quota) - lives here rather than in `feature:geocoding` (which owns the real Gemini HTTP client for location resolution) since feature modules never depend on each other directly and `core:settings` already owns the Gemini key end-to-end | Active |
| `core:permissions`       | Compose utilities   | `requiredRuntimePermissions()`/`rememberRuntimePermissionState()` - the single source of truth for which runtime (dangerous) permissions InstaMaps needs (currently just `POST_NOTIFICATIONS` on API 33+) and their live granted/denied/permanently-denied status, re-checked on every `ON_RESUME`. Extracted out of `feature:share` into its own core module so both `feature:permissions` (the onboarding gate) and `feature:settings` (status display) can depend on it without depending on each other - feature modules never depend on each other directly | Active |
| `core:instagramauth`     | domain, data, di    | `InstagramAuthRepository` - persists the Instagram session cookie captured by `feature:instagramauth`'s WebView login, encrypting it with an AndroidKeystore-backed AES-256-GCM key (`AndroidKeystoreInstagramSessionCipher`) before it touches Jetpack DataStore Preferences. Deliberately *excluded* from Auto Backup/device transfer (unlike `core:settings`) since the Keystore key never leaves the device and a restored file alone would be undecryptable - see `app`'s `data_extraction_rules.xml`/`full_backup_content.xml` | Active |
| `core:history`           | domain, data, di    | `HistoryRepository`/`ObserveHistoryUseCase`/`RecordHistoryEntryUseCase` - persists the last 50 shared links (`MAX_HISTORY_ENTRIES`) plus whatever locations were found for each, as one JSON-array string in its own Jetpack DataStore Preferences file. Deliberately defines its own minimal `HistoryLocation(name, address)` rather than depending on `feature:geocoding`'s `ResolvedLocation` - core modules never depend on feature modules - so `feature:share`'s `ProcessSharedUrlWorker` does that mapping when recording an entry | Active |
| `core:update`            | domain, data, di    | `UpdateRepository`/`CheckForUpdateUseCase`/`DownloadAppUpdateUseCase` - checks GitHub Releases (`GitHubUpdateRepository`, unauthenticated `GET /repos/neteinstein/InstaMaps/releases/latest`) for a build newer than the one installed (`VersionComparator.isNewerVersion` - numeric, not lexicographic, per-segment comparison) and downloads its APK asset to `context.cacheDir/updates/`. `AppUpdateInstaller` (root package, Android glue) checks/requests the per-app "install unknown apps" permission and hands the downloaded APK to the system Package Installer via a `FileProvider` `content://` URI - see `feature:settings`'s "Update to latest" button | Active |
| `feature:maps`           | domain, di          | Builds the Google Maps deep link (`BuildMapsDeepLinkUseCase`) and launches it (`MapsLauncher`, with a browser fallback if the Maps app isn't installed) | Active |
| `feature:geocoding`      | domain, data, di    | `ResolveLocationUseCase` backed by the Gemini Flash REST API (`GeminiLocationRepository`) - sends all collected text (caption + video OCR) to Gemini with a location-identification prompt and returns every place it identifies (`List<ResolvedLocation>`, most-to-least likely) each ready for Google Maps | Active |
| `feature:videoprocessing`| domain, data, di    | Downloads the shared video (yt-dlp), extracts frames (`MediaMetadataRetriever`), OCRs them (ML Kit text recognition + entity extraction), turns raw text into `LocationCandidate`s. `YtDlpVideoDownloadRepository` attaches the persisted Instagram session cookie (`core:instagramauth`) to downloads when one is saved, and classifies a yt-dlp failure as `AppError.AuthenticationRequired` only when the source URL is actually an Instagram host, so a TikTok failure is never misattributed to a missing Instagram login | Active |
| `feature:permissions`    | presentation, di    | The onboarding/readiness gate `MainActivity` shows in place of `feature:share`'s main screen whenever `AppReadiness.isReady` is false - on first launch, and again any time it regresses. `PermissionsScreen` opens with a looping "videos fly into a box, a map comes out" hero animation explaining what InstaMaps does, then lists every requirement (Gemini API key, each `core:permissions` runtime permission) with why it's needed printed right above the button that resolves it, and a live granted/missing status. No back/skip action - the only way past it is to actually resolve every requirement, at which point `MainActivity`'s own readiness check flips back to `feature:share` on its own | Active |
| `feature:share`          | domain, presentation, work, di | Parses the shared URL, runs the video pipeline via `feature:videoprocessing`/`feature:geocoding` to resolve a ranked `List<ResolvedLocation>`, drives it from a `WorkManager` `CoroutineWorker` (survives the app being backgrounded) with a result notification (deep-links straight to the top-ranked match) and a matching animated Compose UI (`ShareScreen`) that lists every match found (scrollable) for the user to pick from instead of auto-opening Maps. The processing screen itself ends with a non-dismissable `BannerTone.INFO` `WarningBanner` telling the user they can leave/background the app and InstaMaps will keep working and notify them once it finds the location - true given the worker already runs as expedited, foreground-notification-backed `WorkManager` work. `ProcessSharedUrlWorker` also records every terminal outcome - found, not-found, or failed - to `core:history` via `RecordHistoryEntryUseCase`. No longer renders API-key/permission warnings itself - `MainActivity` only composes this screen once `feature:permissions`'s readiness gate is satisfied - so the idle/main screen keeps only a non-blocking, deliberately non-red ("info" `BannerTone`, since it's optional) "Connect Instagram" nudge, reacts to an `AppError.AuthenticationRequired` failure by surfacing a dedicated login prompt (`ShareUiState.AuthRequired`) that automatically retries the same video the moment a fresh session is saved, and offers a manual **Retry** button on any other failed/not-found outcome | Active |
| `feature:settings`       | presentation, di    | The Settings screen (`SettingsScreen`/`SettingsViewModel`) the user pastes their Gemini API key into - reachable from the top-right button on `feature:share`'s main screen (and, before that key exists, from `feature:permissions`'s onboarding gate); delegates persistence to `core:settings`. Also renders a live status row (via `core:permissions`) for every runtime permission InstaMaps needs that isn't granted yet, each with a Grant/Open App Settings action - a permission already granted has nothing to resolve, so it's left off the list, and the whole "Permissions" section disappears once none remain - and an "Update to latest" button (via `core:update`) that checks/downloads/installs a newer GitHub release, showing a `WarningBanner` with an "enable sideloading" action instead of downloading when the "install unknown apps" permission isn't granted yet. The Save button sits inline to the right of the API key field (`PrimaryButton(fillWidth = false)` in a `Row`, not a full-width `Scaffold` `bottomBar` anymore) and is only enabled while the field differs from the last-saved key (`SettingsUiState.hasUnsavedChanges`). Tapping it always saves the key, then calls `core:settings`'s `ValidateGeminiApiKeyUseCase` against the real Gemini API purely for feedback - the button shows a spinner (`loading = true`) while that check is in flight, then turns green or red (`ButtonTone.SUCCESS`/`ERROR`) with matching supporting text under the field; saving never blocks on the result, so a red state is only a warning that the key may not work, not a rejection | Active |
| `feature:instagramauth`  | presentation, di    | The Instagram login screen (`InstagramLoginScreen`/`InstagramLoginViewModel`): a `WebView` pointed at Instagram's own login page - InstaMaps never sees the entered password, only detects the `sessionid` cookie once login succeeds, then hands it to `core:instagramauth` to persist | Active |
| `feature:history`        | presentation, di    | The History screen (`HistoryRoute`/`HistoryViewModel`) - reachable from the icon next to Settings on `feature:share`'s main screen - lists `core:history`'s last-50 entries newest-first, each tappable to reopen the original video (`Intent.ACTION_VIEW`) with an "Open in Google Maps" CTA (via `feature:maps`) for the top-ranked location found, if any | Active |
| `app`                    | presentation        | Composition root: `InstaMapsApplication` starts Koin with every feature/core module; `MainActivity` is the single UI entry point, handling launcher taps, the Instagram/TikTok share target, the result-notification deep-link trampoline into `MapsLauncher`, gating its main-screen branch on `feature:permissions`'s `rememberAppReadiness()` (showing `PermissionsScreen` instead of `feature:share`'s `ShareRoute` whenever something's missing, re-evaluated on every recomposition/resume so a revoked permission always re-shows it), and switching between the main (`feature:share`), History (`feature:history`), Settings (`feature:settings`), and Instagram login (`feature:instagramauth`) screens | Active |

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
  `startKoin { }` call site, wiring every feature/core module's Koin module together. Every core
  module that persists its own `DataStore<Preferences>` (`core:settings`, `core:instagramauth`,
  `core:history`) must bind it with a `named(...)` qualifier - Koin resolves `single<DataStore<Preferences>>`
  by type alone, so two unqualified bindings silently shadow each other (the second-registered
  module's reads/writes go to the wrong file) instead of failing loudly. Give any new
  DataStore-backed module its own qualifier from the start
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
- **Location resolution**: Gemini Flash REST API, via the `gemini-flash-latest` model alias so the
  integration keeps working as Google retires/replaces pinned model versions (via
  `java.net.HttpURLConnection` + `org.json`) - no additional SDK dependency. The prompt asks for
  every place the video is actually about, ranked most-to-least likely, since a caption or OCR
  pass often surfaces more than one candidate (the featured spot, a mentioned neighborhood, a
  chain's other locations); `GeminiLocationRepository` parses the response into an ordered
  `List<ResolvedLocation>` rather than a single result
- **Background processing**: `WorkManager` (`CoroutineWorker`) runs the download -> OCR -> geocode
  pipeline so it survives the app being backgrounded right after a share (the common case, since
  the user was just in Instagram/TikTok); progress is observed via `getWorkInfoByIdFlow` for the
  in-app animated UI, and a result notification (tap to deep-link straight into Maps for the
  top-ranked match) fires regardless of whether the app is still in the foreground (see
  `ProcessSharedUrlWorker`, `ShareNotifier`, `ShareViewModel`). `ProcessSharedUrlWorker` also
  records every terminal outcome to `core:history` (best-effort - a history-write failure never
  masks the real pipeline result)
- **Settings persistence**: Jetpack DataStore Preferences (`core:settings`) stores the
  user-entered Gemini API key under the app's `filesDir`, covered by the manifest's existing
  `android:allowBackup="true"` Auto Backup - no separate sync/backup plumbing needed for it to
  carry over to a user's other devices
- **Gemini API key validation**: pressing Save on `feature:settings`'s Settings screen always
  persists the key, then calls `core:settings`'s `ValidateGeminiApiKeyUseCase`
  (`HttpGeminiApiKeyValidator`) to confirm it actually works, via a `GET` against Gemini's
  `v1beta/models` endpoint rather than `generateContent` - listing models only requires an
  authenticated request, so the check costs no generation quota. The Save button
  (`core:designsystem`'s `PrimaryButton`) shows a spinner while that request is in flight, then
  turns green or red based on the result; a red result is a non-blocking warning only - the key
  was already saved regardless of what validation finds
- **History persistence**: Jetpack DataStore Preferences (`core:history`), in its own file/qualifier
  from `core:settings`'/`core:instagramauth`'s stores, holds the last 50 shared links (oldest
  trimmed on write) as a single JSON-array string - covered by Auto Backup like `core:settings`,
  since a history list carries no secret material
- **In-app updates**: `core:update` checks GitHub Releases (`GET
  /repos/neteinstein/InstaMaps/releases/latest`, the same raw `HttpURLConnection`/`org.json`
  approach as the Gemini integration - no additional SDK dependency) for a build newer than the
  one installed, comparing the release tag (leading `"v"` stripped, see `AppUpdate.versionName`)
  against `PackageManager`'s reported `versionName` with `VersionComparator.isNewerVersion` - a
  numeric, per-segment comparison, not a lexicographic one, since `"1.0.9"` would otherwise
  wrongly compare as greater than `"1.0.16"`. If newer, `feature:settings`'s "Update to latest"
  button downloads the release's `.apk` asset to `context.cacheDir/updates/` (auto-excluded from
  Auto Backup, so no `data_extraction_rules.xml` changes needed) and hands it to the system
  Package Installer via a `FileProvider` `content://` URI (`AppUpdateInstaller`), gated behind the
  `REQUEST_INSTALL_PACKAGES` permission. If the user hasn't allowed installs from outside the Play
  Store yet, the button shows a warning instead of downloading, with an action that deep-links
  into that permission's system Settings screen (`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`)
  rather than attempting - and wasting bandwidth on - a download the system will refuse to install
- **Testing**: JUnit 4, Mockito-Kotlin, `kotlinx-coroutines-test`
- **Linting**: ktlint via `org.jlleitschuh.gradle.ktlint`
- **Coverage**: Kover (`org.jetbrains.kotlinx.kover`)

## Development Setup

No build-time secrets file is required - a clean checkout compiles and runs as-is. On first
launch (or any time a requirement regresses), InstaMaps shows `feature:permissions`'s onboarding
gate instead of the main screen: a looping "videos fly into a box, a map comes out" animation
explaining what the app does, followed by a card per requirement (the Gemini API key, each
`core:permissions` runtime permission) explaining *why* it's needed right above the button that
resolves it. Tapping the API key card's CTA opens `feature:settings`, where you paste in a key and
tap Save; get a free key from
[Google AI Studio's API key page](https://aistudio.google.com/apikey) (see the README's
"Getting a Gemini API key" section for the full walkthrough). The moment every requirement is
resolved, `MainActivity`'s readiness check (`rememberAppReadiness()`) flips over to `feature:share`'s
main screen on its own - no explicit "continue" step. Revoking a permission from system Settings
and reopening InstaMaps lands back on this gate automatically, since readiness is recomputed on
every recomposition/resume, not just on first launch.

Logging into Instagram is optional but recommended, not required to use the app, and is
deliberately *not* part of the onboarding gate above: if no session is saved, the main screen
shows a dismissible, non-red "Connect Instagram" banner (`BannerTone.INFO` - it's an optional
reliability boost, not a requirement, so it shouldn't read as a warning) that leads to
`feature:instagramauth`'s WebView login screen, pointed at Instagram's real login page - InstaMaps
never sees the password, only the resulting session cookie. Public/anonymous-accessible content
still downloads without logging in; if yt-dlp reports that a specific shared video needs a login,
the main screen automatically switches to a "Log in to Instagram" prompt instead and retries that
exact video the moment a fresh session is saved - see `feature:share`'s `ShareViewModel`
(`isInstagramAuthenticated`/`ShareUiState.AuthRequired`) and `core:instagramauth`.

`feature:settings` also has an "Update to latest" button (`core:update`) for installing newer
builds straight from GitHub Releases without going through the Play Store - see the README's
"Updating InstaMaps" section for the user-facing flow. It needs no setup of its own: the
"install unknown apps" permission it depends on is requested on demand, the first time the
button is actually pressed, rather than being part of the onboarding gate above.

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
  packages, plus `core:designsystem` and `core:permissions` (pure Compose UI/Android-framework glue
  with no business logic worth measuring, none of it exercisable without instrumentation/Robolectric,
  which this project doesn't use), are excluded from the coverage denominator - see the
  `kover { reports { filters { excludes { ... } } } }` block in the root `build.gradle.kts`. When
  you add a new module or a new UI/framework-glue-only package, extend that exclude list rather
  than letting it silently drag the aggregate number down. `app` is excluded as a whole module
  (not added to the `dependencies { kover(project(...)) }` list at all) for the same reason: it is
  a pure composition root with no domain/data logic of its own.
- Use `UnconfinedTestDispatcher`/`StandardTestDispatcher` (from `kotlinx-coroutines-test`) for
  coroutine-based code. Prefer hand-rolled fakes over Mockito mocks for repository interfaces
  where practical - cheaper to read and keep in sync than a mock's stubbing chain.
- Neither `GeminiLocationRepository` nor `HttpGeminiApiKeyValidator` has a test that makes a real
  network call - there's no MockWebServer/OkHttp in the version catalog, so both instead pull
  their response-code/error-mapping branching out into a small `internal` pure function
  (`HttpGeminiApiKeyValidator.classifyResponseCode(Int): ApiKeyValidity?`, in a companion object)
  that the test source set calls directly. Follow the same pattern for any new HTTP call: keep the
  actual `HttpURLConnection`/`safeCall` plumbing untested (consistent with the rest of this list),
  but extract whatever branches on the response so that logic isn't untestable too.
- Any test that round-trips real `org.json` `JSONObject`/`JSONArray` serialization (not just
  pre-network validation branches) needs a real desktop `org.json:json` jar on the test
  classpath (`testImplementation(libs.json)`, see `core/history/build.gradle.kts`). Without it,
  the module compiles against `android.jar`'s non-functional stub `org.json` classes, and under
  this project's `unitTests.isReturnDefaultValues = true` setting, calling `.toString()` on the
  stub returns `null` instead of running real logic - the compiler-inserted null-check then
  throws `NullPointerException: toString(...) must not be null` with no obvious link to the real
  cause. `feature:share`'s `ResolvedLocationsJson.kt` and `feature:geocoding`'s
  `GeminiLocationRepository.kt` both use `org.json` for real serialization too, but neither
  currently has a test exercising that path - add the same `testImplementation(libs.json)` there
  the moment a test does. `core:update`'s `GitHubUpdateRepositoryTest` is the first to actually
  exercise this path (see its `parseGitHubReleaseResponse` parsing tests) and already carries the
  dependency in `core/update/build.gradle.kts`.
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

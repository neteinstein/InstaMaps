# Android Development Context

## Project Overview

InstaMaps receives a shared Instagram Reel or TikTok video link from the OS share sheet,
downloads the video on-device, extracts the location it advertises (a restaurant, a landmark, a
store) by reading on-screen text with OCR, geocodes it with the Places SDK, and deep-links
straight into Google Maps. The whole pipeline (download -> frame extraction -> OCR -> entity
extraction -> geocoding) runs on-device; there is no backend server.

## Architecture

Clean Architecture, one Gradle module per feature, MVVM in the presentation layer. Each feature
module is internally layered as:

- **`domain/`**: use cases, repository interfaces, plain Kotlin domain models. No Android
  framework or third-party SDK types leak into this layer - it must stay unit-testable with
  plain JUnit and no mocking framework required.
- **`data/`**: repository implementations that call into a specific SDK/framework
  (`MediaMetadataRetriever`, ML Kit, Places SDK, yt-dlp).
- **`di/`**: a Koin `module { }` wiring the module's use cases and repositories together.
- Compose UI + ViewModel, for modules that own a screen.

Modules only depend downward: `feature:*` modules depend on `core:common`, and UI-owning modules
also depend on `core:designsystem`. Feature modules never depend on each other directly - shared
logic belongs in `core:common` instead.

### Module map

| Module                  | Layers present     | Responsibility                                                                                            | Status |
|--------------------------|---------------------|------------------------------------------------------------------------------------------------------------|--------|
| `core:common`            | domain, di          | `AppError`, `DispatcherProvider`, `LatLng`, `safeCall` - shared primitives used across every module         | Active |
| `core:designsystem`      | Compose UI, theme   | Shared Compose theme/typography + reusable components (`PrimaryButton`, `LoadingIndicator`, `ErrorMessage`) | Active |
| `feature:maps`           | domain, di          | Builds the Google Maps deep link (`BuildMapsDeepLinkUseCase`) and launches it (`MapsLauncher`, with a browser fallback if the Maps app isn't installed) | Active |
| `feature:geocoding`      | domain, data, di    | `SearchPlaceUseCase` against the Places SDK (`PlacesSdkPlaceSearchRepository`)                              | Active |
| `feature:videoprocessing`| domain, data, di    | Downloads the shared video (yt-dlp), extracts frames (`MediaMetadataRetriever`), OCRs them (ML Kit text recognition + entity extraction), turns raw text into `LocationCandidate`s | Active |
| `feature:share`          | -                   | Share-target intent handling; orchestrates the other features behind a ViewModel                            | Not yet built (excluded from `settings.gradle.kts`) |
| `app`                    | -                   | Composition root: `Application` class, Koin startup, single Activity/NavHost                                | Disabled in `settings.gradle.kts`; only resource scaffolding remains from the pre-rewrite app |

`settings.gradle.kts` is the source of truth for which modules are part of the build - a module
commented out there is not compiled, tested, or linted, and should not be assumed to exist.

## Tech Stack

- **Language**: Kotlin, JVM target 17
- **Min SDK**: 27; **Compile SDK**: 36 (target SDK will be pinned once the `app` module is rebuilt)
- **UI**: Jetpack Compose (Material 3 via the Compose BOM) - no XML layouts, no ViewBinding
- **Architecture**: MVVM, `ViewModel` + Compose state/`StateFlow` (no LiveData)
- **Concurrency**: Kotlin Coroutines (`viewModelScope`, dispatchers behind `DispatcherProvider`
  so tests can inject a test dispatcher instead of patching `Dispatchers.Main`)
- **DI**: Koin (`koin-android`, `koin-androidx-compose`) - constructor injection into use cases
  and repositories, `koinViewModel()` in Compose
- **Video download**: yt-dlp via `youtubedl-android`, forced to `bestvideo[height<=480]+bestaudio/best[height<=480]`
  to keep the download small and the on-device decode fast (see `YtDlpVideoDownloadRepository`)
- **Frame extraction**: `MediaMetadataRetriever` with `OPTION_CLOSEST_SYNC` (snap to keyframes,
  skip P/B-frame decoding) and `getScaledFrameAtTime` (downscale during decode, not after), fed
  through a coroutine `Channel` producer/consumer pipeline so extraction never blocks on OCR
  (see `MediaMetadataRetrieverFrameExtractor`, `ExtractLocationCandidatesUseCase`)
- **OCR / entity extraction**: Google ML Kit, on-device Text Recognition + Entity Extraction
- **Geocoding**: Google Places SDK for Android
- **Testing**: JUnit 4, Mockito-Kotlin, `kotlinx-coroutines-test`
- **Linting**: ktlint via `org.jlleitschuh.gradle.ktlint`
- **Coverage**: Kover (`org.jetbrains.kotlinx.kover`)

## Development Setup

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
  be reasonably unit-tested (e.g. framework glue that just calls `context.startActivity`) should
  be kept thin rather than pulling in Robolectric.
- Target **80%+ line coverage** on `domain` and `data` packages. `di` packages and
  `core:designsystem` (pure Compose UI, no branching logic) are excluded from the coverage
  denominator - see the `kover { reports { filters { excludes { ... } } } }` block in the root
  `build.gradle.kts`. When you add a new module or a new UI-only package, extend that exclude
  list rather than letting UI code silently drag the aggregate number down.
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
- Before writing code against a third-party library/SDK (Koin, ML Kit, Places SDK, yt-dlp,
  AndroidX/Compose), verify the real API surface first - decompile the resolved artifact or read
  real source at the exact version in use, rather than relying on a recalled API shape. Library
  APIs shift between versions often enough that this has repeatedly caught real mismatches during
  this project's build-out (e.g. Places SDK's real `Place.Field.DISPLAY_NAME` vs. the commonly
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

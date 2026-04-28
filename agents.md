# Android Development Context

## Project Overview

InstaMaps is an Android application that accepts shared Instagram reel URLs,
fetches the reel's description via the Instagram oEmbed API, extracts location information
from the description, and allows users to open the location in Google Maps.

## Architecture

The app follows Clean Architecture with MVVM presentation pattern:

### Layers

- **Presentation** (`presentation/`): Activities, ViewModels, UI state classes
- **Domain** (`domain/`): Business logic, use cases, repository interfaces, domain models
- **Data** (`data/`): Repository implementations, API interfaces, network setup

### Key Components

- `MainActivity`: Entry point, handles `ACTION_SEND` intents, observes ViewModel state
- `MainViewModel`: Coordinates use cases, exposes `LiveData<MainUiState>`
- `MainUiState`: Sealed class representing UI states (Idle, Loading, LocationFound, Error)
- `GetReelInfoUseCase`: Validates Instagram URL and delegates to repository
- `ExtractLocationUseCase`: Parses text to extract location using regex patterns
- `LocationRepositoryImpl`: Calls Instagram oEmbed API via Retrofit

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Networking**: Retrofit 2 + OkHttp 3 + Gson
- **Concurrency**: Kotlin Coroutines + ViewModelScope
- **UI**: AndroidX ViewBinding + Material Components
- **Architecture**: LiveData + ViewModel (AndroidX Lifecycle)
- **Testing**: JUnit 4, Mockito-Kotlin, Coroutines Test, Architecture Core Testing
- **Linting**: ktlint via `org.jlleitschuh.gradle.ktlint` plugin

## Development Setup

```bash
# Build the project
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run linting
./gradlew ktlintCheck

# Auto-fix lint issues
./gradlew ktlintFormat
```

## Testing

- Unit tests are in `app/src/test/`
- Target 80%+ coverage of domain and presentation layers
- Use `InstantTaskExecutorRule` for LiveData testing
- Use `StandardTestDispatcher` for coroutines testing

## Location Extraction Strategy

The `ExtractLocationUseCase` tries the following in order:

1. Explicit location patterns: `Location:`, `loc:`, `📍`, `🗺️`, `at:`, `in:`
2. CamelCase hashtags that look like place names (e.g., `#NewYorkCity`)
3. Coordinate pairs (e.g., `48.8566, 2.3522`)

## Google Maps Integration

Location is opened via Android Intent:

- Primary: `geo:0,0?q={location}` targeted at `com.google.android.apps.maps`
- Fallback: `https://www.google.com/maps/search/?api=1&query={location}` in browser

## CI/CD

GitHub Actions workflow (`.github/workflows/pr_checks.yml`) runs on every PR:

1. ktlint check
2. Unit tests

PR can only be merged if both checks pass.

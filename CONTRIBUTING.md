# Contributing to LibreArm Android

Thank you for your interest in contributing to LibreArm for Android! This project keeps QardioArm blood pressure monitors functional after Qardio shut down. Every contribution helps users continue monitoring their health.

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1) or later
- JDK 17
- Android SDK 34
- A physical Android device with Bluetooth LE support (emulator does not support BLE)
- A QardioArm blood pressure monitor for testing

### Building

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle
4. Build and run on a physical device

> **Note**: BLE functionality requires a real device. The app will not scan or connect on an emulator.

## How to Contribute

### Reporting Bugs

- Open a GitHub issue with a clear title and description
- Include your Android version, device manufacturer/model, and QardioArm generation
- Describe what you expected vs what happened
- Include steps to reproduce if possible

### Suggesting Features

- Open a GitHub issue describing the feature and its value to users
- Check existing issues first to avoid duplicates
- For larger features, discuss the approach in an issue before writing code

### Submitting Pull Requests

1. Fork the repository and create a feature branch from `main`
2. Keep PRs focused — one feature or fix per PR
3. Test on a physical device with a QardioArm
4. Run `./gradlew test` to verify unit tests pass
5. Update the README if your change adds user-facing features
6. Open a PR with a clear description of what changed and why

### What We're Looking For

Areas where contributions are especially welcome:

- **iOS feature parity** — Battery monitoring, strict validation, hypertension graph
- **Bluetooth stability** — Connection reliability across different Android manufacturers
- **Local persistence** — Room database for reading history
- **Data export** — CSV and PDF export for doctor sharing
- **Testing** — Unit tests for SFLOAT parsing, validation, and data models
- **UI improvements** — Component extraction, accessibility, layout refinements
- **Documentation** — README improvements, code comments

## Code Style

LibreArm Android follows standard Kotlin and Jetpack Compose conventions. Please match the existing patterns:

### Naming

- **Classes/Objects**: PascalCase (`BpClient`, `BpReading`, `BpViewModel`)
- **Variables/functions**: camelCase (`state`, `sessionActive`, `startConnect`)
- **Constants**: UPPER_SNAKE_CASE (`CLIENT_CONFIG_UUID`, `HEALTH_CONNECT_PACKAGE`)
- **Enum values**: UPPER_SNAKE_CASE (`SINGLE`, `AVERAGE3`)
- **Private backing fields**: Underscore prefix (`_state`, `_scope`)
- **Packages**: lowercase (`ble`, `model`, `health`, `ui.theme`)

### Organization

- Organize code by package: `ble/`, `model/`, `health/`, `ui/`, `data/`
- One primary class per file
- Data classes in `model/` package
- BLE logic in `ble/` package
- Health integration in `health/` package

### Patterns

- **State**: `StateFlow` + `.update {}` for thread-safe state mutations
- **Data**: `data class` for value objects
- **Results**: `sealed class` for type-safe results (`SaveResult`)
- **Errors**: `runCatching {}` + `.getOrElse {}` (not try/catch)
- **Validation**: Early returns with `if (!condition) return`
- **Async**: Kotlin coroutines (`launch`, `delay`, `withContext`)
- **UI**: Jetpack Compose with Material 3

### Formatting

- 4-space indentation
- Opening braces on the same line
- Generally keep lines under 120 characters
- One blank line between functions
- Imports organized by package (Android, Kotlin, Java, project)

### Dependencies

- **Prefer AndroidX and Jetpack libraries**
- Do not add third-party dependencies without discussion in an issue first
- The project intentionally has zero non-Google dependencies

### Permissions

When adding features that require new permissions:

- Add the permission to `AndroidManifest.xml`
- Handle runtime permission requests in `MainActivity.kt`
- Use `maxSdkVersion` for permissions deprecated in newer Android versions
- Test permission flows on both Android 12+ and older versions

## Architecture

```
app/src/main/java/com/ptylr/librearm/
├── MainActivity.kt                # Activity, permissions, Compose UI host
├── BpViewModel.kt                 # ViewModel wrapping BpClient
├── ble/
│   └── BpClient.kt               # BLE scan, connect, GATT, measurement, SFLOAT
├── model/
│   └── BpModels.kt               # BpState, BpReading, MeasurementMode
├── health/
│   └── HealthConnectManager.kt    # Health Connect availability, permissions, save
└── ui/theme/
    ├── Theme.kt                   # Material 3 theme
    ├── Color.kt                   # Color definitions
    ├── Type.kt                    # Typography
    └── Shape.kt                   # Shape definitions
```

- **BpClient** is the core BLE engine — scan, connect, GATT callbacks, measurement protocol, SFLOAT parsing
- **BpViewModel** wraps BpClient and exposes `StateFlow<BpState>` to the UI
- **HealthConnectManager** handles Health Connect availability checking, permission requests, and data saving
- **MainActivity** hosts Compose UI, handles Android permissions, and coordinates Health Connect flow

### Key Design Decisions

- `BpClient` manages its own `CoroutineScope` for BLE operations
- State flows upward via `StateFlow`; commands flow downward via function calls
- Health Connect saving happens in `MainActivity` (not ViewModel) due to Activity-bound permission contracts
- `SharedPreferences` used for simple settings (measurement mode, Health Connect toggle)

## Testing

### Unit Tests

Place unit tests in `app/src/test/java/com/ptylr/librearm/`.

Testable areas:
- SFLOAT parser (`decodeSfloat()`) with known byte sequences
- Reading validation (`isPlausible()` / `isValidReading()`) with boundary values
- BP classification logic
- Averaging algorithm

### Instrumented Tests

Place instrumented tests in `app/src/androidTest/java/com/ptylr/librearm/`.

- UI tests with Compose testing framework
- Health Connect integration tests (requires Health Connect on device)

### Manual Testing Checklist

Before submitting a PR, verify on a physical device:

- [ ] App launches without crashes
- [ ] BLE scan finds QardioArm within 30 seconds
- [ ] Single measurement completes successfully
- [ ] Average mode completes 3 readings with countdown
- [ ] Cancel measurement stops the cuff
- [ ] Health Connect save works (if enabled)
- [ ] Settings persist after app restart

## Questions?

Open an issue or reach out. We appreciate your help keeping QardioArm devices alive.

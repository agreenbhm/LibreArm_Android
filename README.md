# LibreArm (Android)

LibreArm for Android mirrors the iOS app: it connects directly to the **QardioArm** blood pressure monitor over BLE, lets you start/stop a measurement, optionally averages 3 readings with a user-selectable delay, shows the latest result in the UI, and can save blood pressure + heart rate to **Health Connect**.

## Features

- **Direct BLE connection** to QardioArm — no cloud, no accounts, no servers
- **Single or average-of-3 measurement modes** with configurable delay (15/30/45/60s)
- **Strict reading validation** — 6-rule physiological plausibility check matching the iOS app
- **Google Health Connect integration** — optional write of blood pressure and heart rate
- **Material 3 UI** with Jetpack Compose
- **Offline-first** — all data stays on device

## Build & run

```
./gradlew assembleDebug
./gradlew test                # Run unit tests
```

Requirements:
- Android Studio/AGP 8.3+, Compose Material 3
- Android SDK 34, Build Tools 34
- JDK 17
- Device with Bluetooth LE (QardioArm does not work in the emulator)

Permissions requested at runtime:
- Bluetooth Scan/Connect (or fine location on Android 10 and lower)
- Health Connect write access for blood pressure and heart rate (only when the toggle is enabled)
- Notifications (Android 13+) — optional, for low battery warnings

If you are building on a non-x86_64 Linux host, AGP's packaged `aapt2` may not be available; run the Gradle task on x86_64 or provide an `aapt2` binary for your architecture via `android.aapt2FromMavenOverride`.

## Testing

Unit tests cover the parsing, validation, and averaging logic in `BpParser.kt`. They run on a pure JVM with no Android SDK or emulator required:

```
./gradlew test
```

Test suites:
- **SfloatParserTest** (12 tests) — IEEE 11073 SFLOAT 16-bit decoder
- **ReadingValidationTest** (21 tests) — All 6 physiological validation rules
- **MeasurementParserTest** (7 tests) — BLE Blood Pressure Measurement (0x2A35) packet parsing
- **AveragingTest** (9 tests) — Multi-reading averaging with filtering

For BLE testing, use [nRF Connect for Mobile](https://www.nordicsemi.com/Products/Development-tools/nrf-connect-for-mobile) on a separate device to simulate a Blood Pressure Service GATT server.

## Architecture

```
app/src/main/java/com/ptylr/librearm/
├── MainActivity.kt              # Activity, permissions, Compose UI
├── BpViewModel.kt               # ViewModel wrapping BpClient
├── ble/
│   ├── BpClient.kt              # BLE scan, connect, GATT callbacks
│   └── BpParser.kt              # Standalone SFLOAT parsing & validation (testable)
├── model/
│   └── BpModels.kt              # BpState, BpReading, MeasurementMode
├── health/
│   └── HealthConnectManager.kt  # Health Connect integration
└── ui/theme/                    # Material 3 theme
```

## QardioArm BLE Protocol

The QardioArm uses standard Bluetooth SIG services with one vendor-specific control characteristic:

| Component | UUID | Purpose |
|-----------|------|---------|
| Blood Pressure Service | `0x1810` | Standard Bluetooth SIG service |
| Measurement Characteristic | `0x2A35` | IEEE 11073 SFLOAT format, notify |
| Control Characteristic | `583CB5B3-875D-40ED-9098-C39EB0C1983D` | Vendor-specific, write |
| Battery Service | `0x180F` | Standard Bluetooth SIG service |
| Battery Level | `0x2A19` | Single byte 0-100%, read/notify |

Commands written to the control characteristic:
- `[0xF1, 0x01]` — Start measurement (cuff inflates)
- `[0xF1, 0x02]` — Cancel measurement (cuff deflates)

Validation rules applied to readings (matching iOS):
1. Diastolic > 0 (filters partial BLE notifications)
2. Both values finite (filters SFLOAT NaN)
3. Systolic in 60–260 mmHg
4. Diastolic in 40–160 mmHg
5. Systolic > diastolic (physiological requirement)
6. Pulse pressure (sys − dia) ≤ 120 mmHg

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for code style and contribution guidelines.

## Credits

- Original iOS LibreArm by Paul Taylor — [ptylr/LibreArm](https://github.com/ptylr/LibreArm)
- Android port by [agreenbhm](https://github.com/agreenbhm)

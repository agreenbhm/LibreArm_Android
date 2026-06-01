# LibreArm (Android)

LibreArm for Android mirrors the iOS app: it connects directly to the **QardioArm** blood pressure monitor over BLE, lets you start/stop a measurement, optionally averages 3 readings with a user-selectable delay, shows the latest result along with a hypertension classification graph, and can save blood pressure + heart rate to **Health Connect**.

This project exists because Qardio, Inc. shut down its backend services and app support, leaving the QardioArm hardware functional but unusable with the original app.

---

## ✨ Android port enhancements

Features the Android port adds beyond the iOS app's feature set:

- **Bottom navigation** — Settings and About destinations alongside the measurement screen
- **Configurable readings** — take 1, 2, or 3 readings per measurement (averaged when more than one), selectable in Settings
- **Retry on invalid reading** — an implausible reading prompts a retry; three consecutive failures auto-cancel the measurement
- **Dark mode** — Auto / Light / Dark theme selector in Settings, applied immediately
- **Measurement history** — Calendar and Trends views of past blood-pressure and heart-rate readings, read back from Health Connect
- **Guest mode** — a sticky toggle to take readings that display but aren't saved to your history, for when you hand the cuff to someone else

## 📋 Inherited from iOS

The Android port mirrors the iOS LibreArm feature set; the version numbers below trace that **iOS** lineage (the port's own first Android release is v1.5.0).

### v1.5.0

- **Always-Visible Hypertension Graph**: blood pressure graph permanently displayed inside the reading card
  - Color-coded zones: Low (cyan), Normal (green), Prehypertension (orange), Stage 1 (pink), Stage 2 (red)
  - Reading plot point with white border and shadow
  - Graph is shown at reduced opacity until a real reading has arrived
- **Settings Persistence**: user preferences are saved between app sessions
  - "Save to Health Connect" toggle state persisted
  - "Average (3 readings)" mode selection persisted
  - Delay slider value persisted (15s, 30s, 45s, or 60s)
- **Improved UI Layout**: streamlined interface with no shifting elements
  - Top bar shows "LibreArm" (left) and "Blood Pressure" (right)
  - Status and battery info displayed on a dedicated line
  - Delay slider always visible (disabled when not in Average mode)
  - Launcher-icon image removed from the main view

### v1.4.0

- **Battery Level Display**: real-time battery monitoring using the standard BLE Battery Service (0x180F / 0x2A19)
  - Battery percentage shown under the connection status
  - Low battery warning (≤20%) and critical battery warning (≤10%)
- **Battery Notifications**: background notifications when battery crosses the low/critical thresholds (only when the app is backgrounded; requires `POST_NOTIFICATIONS` on Android 13+)
- **Critical Battery Protection**: measurements are blocked when battery ≤10% to prevent incomplete readings
- **Strict Reading Validation**: invalid or incomplete readings are no longer displayed or saved
  - Readings validated for physiologically plausible ranges, `systolic > diastolic`, and pulse pressure ≤ 120
- **Average Mode hardening**: requires all 3 readings to be valid; the averaged result is re-validated before being returned
- **Automatic Battery Checks**: battery level is read on connect, before measurements, and after completion

### v1.3.0 and earlier

- Connects to QardioArm over BLE (no Qardio cloud or accounts required)
- Connection management with retry button + 30s connection timeout
- Dynamic Start/Stop button (blue *Start* when idle, red *Stop* while inflating; sends cancel on tap)
- Average mode: 3 consecutive readings with a configurable inter-run delay and a live countdown
- Single, debounced final reading per session — no partial inflating entries written to Health Connect
- 100% local: no accounts, no data leaves your device

---

## 🚀 Build & run

```
./gradlew assembleDebug
```

Requirements:
- Android Studio / AGP 9, Gradle 9, Kotlin 2.2, Compose Material 3
- Android SDK 36 (minSdk 26)
- Device with Bluetooth LE (QardioArm does not work in the emulator)

Permissions requested at runtime:
- **Bluetooth Scan / Connect** (or `ACCESS_FINE_LOCATION` on Android 10 and lower)
- **Health Connect** write access for blood pressure and heart rate (only when the toggle is enabled)
- **`POST_NOTIFICATIONS`** (Android 13+) — used solely for the low/critical battery alerts when the app is in the background

---

## 🔧 Implementation notes

- **Language & UI**: Kotlin + Jetpack Compose (Material 3)
- **Bluetooth**: `android.bluetooth.le` (scan filtered by Blood Pressure Service 0x1810)
  - Blood Pressure Service (0x1810): measurement char 0x2A35 + vendor control UUID `583CB5B3-875D-40ED-9098-C39EB0C1983D`
  - Battery Service (0x180F): battery level char 0x2A19
- **Health**: [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) (`BloodPressureRecord` + `HeartRateRecord`) — writes readings, and reads blood pressure and heart rate history back for the Calendar / Trends views
- **Notifications**: `NotificationManagerCompat` on a dedicated channel, gated by `ProcessLifecycleOwner` so alerts only fire when the app is backgrounded
- **Persistence**: `SharedPreferences` (`librearm_prefs`)

The app uses the same debounce strategy as the iOS version: only the **final** reading of a measurement is delivered/saved, preventing dozens of partial entries from being persisted. As of v1.4.0 the Android port uses strict validation to ensure that only complete, physiologically plausible readings are displayed and saved.

---

## 🛡 Privacy

- LibreArm does **not** connect to the internet.
- All readings stay on your device.
- Data is saved into **Health Connect** only when you've enabled the toggle and granted permission.
- The history views read your blood pressure and heart rate back from Health Connect, with your permission, solely to display them in the app.

---

## Relationship to the iOS app

The Android port mirrors the feature set of the iOS LibreArm app (currently aligned with iOS v1.5.0). The original iOS project is at https://github.com/ptylr/LibreArm. The Android port is a separate, community-driven project; please direct Android-specific questions, issues, or contributions to this repository.

## 📜 License

This project is licensed under the [MIT License](LICENSE).

## Disclaimer

LibreArm is **not affiliated with or endorsed by Qardio, Inc.** QardioArm™ is a trademark of Qardio, Inc. This project is community-driven to keep existing hardware usable.

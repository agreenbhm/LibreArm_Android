# LibreArm (Android)

LibreArm for Android mirrors the iOS app: it connects directly to the **QardioArm** blood pressure monitor over BLE, lets you start/stop a measurement, optionally averages 3 readings with a user-selectable delay, shows the latest result in the UI, and can save blood pressure + heart rate to **Google Fit**.

## Build & run

```
./gradlew assembleDebug
```

Requirements:
- Android Studio/AGP 8.3+, Compose Material 3
- Android SDK 34, Build Tools 34
- Device with Bluetooth LE (QardioArm does not work in the emulator)

Permissions requested at runtime:
- Bluetooth Scan/Connect (or fine location on Android 10 and lower)
- Google Fit write access for blood pressure and heart rate (only when the toggle is enabled)

If you are building on a non-x86_64 Linux host, AGP's packaged `aapt2` may not be available; run the Gradle task on x86_64 or provide an `aapt2` binary for your architecture via `android.aapt2FromMavenOverride`.

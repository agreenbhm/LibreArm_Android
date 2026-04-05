# Change: Strict Blood Pressure Reading Validation

## Summary

Enhanced the blood pressure reading validation in `BpClient.kt` to match the iOS LibreArm app's stricter physiological checks. The previous `isPlausible()` function only checked that systolic and diastolic values fell within broad ranges. The new `isValidReading()` function adds three additional checks that prevent physiologically impossible readings from being accepted or saved to Health Connect.

## Problem

The existing `isPlausible()` function (lines 405-408) performed only two checks:

```kotlin
private fun isPlausible(reading: BpReading): Boolean {
    if (!reading.sys.isFinite() || !reading.dia.isFinite()) return false
    return reading.sys in 60.0..260.0 && reading.dia in 40.0..160.0
}
```

This allowed several categories of invalid readings to pass:

1. **Partial readings** (dia = 0): The QardioArm sends intermediate BLE notifications during measurement where diastolic is 0 (measurement not yet complete). These could be finalized prematurely.

2. **Systolic <= diastolic** (e.g., 70/80): Physiologically impossible — systolic pressure (heart contracting) is always higher than diastolic (heart relaxing). This could occur from a SFLOAT parsing edge case or corrupted BLE data.

3. **Extreme pulse pressure** (e.g., 250/50 = pulse pressure 200): While both sys and dia individually fall within range, a pulse pressure (sys - dia) greater than 120 mmHg indicates a measurement error, not a real reading.

The iOS app (`BPClient.swift`, `isValidReading()`) already checks for all of these cases.

## Fix

Replaced `isPlausible()` with `isValidReading()` that performs 6 validation checks:

```kotlin
private fun isValidReading(reading: BpReading): Boolean {
    // 1. Diastolic > 0 indicates a complete reading (not partial)
    if (reading.dia <= 0) return false

    // 2. Values must be finite (filters SFLOAT NaN and Infinity)
    if (!reading.sys.isFinite() || !reading.dia.isFinite()) return false

    // 3. Systolic range: 60-260 mmHg
    if (reading.sys !in 60.0..260.0) return false

    // 4. Diastolic range: 40-160 mmHg
    if (reading.dia !in 40.0..160.0) return false

    // 5. Systolic must exceed diastolic
    if (reading.sys <= reading.dia) return false

    // 6. Pulse pressure must be reasonable
    if ((reading.sys - reading.dia) > 120) return false

    return true
}
```

### Validation Rules (matching iOS)

| # | Rule | Range/Check | Why |
|---|------|-------------|-----|
| 1 | Diastolic > 0 | Required | Filters partial/intermediate BLE notifications |
| 2 | Values finite | Required | Filters SFLOAT NaN (0x07FF) and Infinity |
| 3 | Systolic range | 60-260 mmHg | Physiological minimum/maximum |
| 4 | Diastolic range | 40-160 mmHg | Physiological minimum/maximum |
| 5 | Systolic > Diastolic | Required | Fundamental cardiovascular physiology |
| 6 | Pulse pressure | <= 120 mmHg | Filters measurement errors with extreme spread |

### Changes to `finalizeIfNeeded()`

The finalization logic was also updated to properly handle invalid-but-complete readings:

- **Before**: Only checked `reading.dia <= 0` to filter partial readings, then called `isPlausible()` only when accumulating for average mode
- **After**: `isValidReading()` is called first. If a reading is complete (dia > 0) but fails other validation rules, the user sees a clear error message: "Invalid reading (X/Y). Check cuff fit and try again." The measurement session is ended cleanly.

### Changes to `average()`

Updated `average()` to use `isValidReading()` instead of `isPlausible()` for filtering accumulated readings. This ensures averaged results are computed only from fully validated readings.

## Files Changed

- `ble/BpClient.kt`:
  - Replaced `isPlausible()` with `isValidReading()` (expanded from 3 lines to 13 lines)
  - Updated `finalizeIfNeeded()` to validate before processing and show error status for invalid readings
  - Updated `average()` to use `isValidReading()` for filtering

## Testing

### Unit test cases for `isValidReading()`:

| Input (sys/dia) | Expected | Reason |
|-----------------|----------|--------|
| 120/80 | Valid | Normal reading |
| 138/88 (hr=72) | Valid | Normal reading with heart rate |
| 140/0 | Invalid | Partial reading (dia = 0) |
| 120/NaN | Invalid | SFLOAT special value |
| 50/40 | Invalid | Systolic below 60 |
| 270/80 | Invalid | Systolic above 260 |
| 120/30 | Invalid | Diastolic below 40 |
| 120/170 | Invalid | Diastolic above 160 |
| 70/80 | Invalid | Systolic <= diastolic |
| 80/80 | Invalid | Systolic = diastolic |
| 250/50 | Invalid | Pulse pressure 200 > 120 |
| 180/60 | Valid | Pulse pressure 120 = limit (boundary) |
| 181/60 | Invalid | Pulse pressure 121 > 120 |
| 60/40 | Valid | Minimum valid reading (boundary) |
| 260/140 | Valid | Maximum valid reading (boundary) |

### Manual testing on device:

1. Take a normal measurement — should display and save correctly
2. Cancel measurement mid-inflation — partial readings should not trigger error (dia = 0 returns silently)
3. Average mode with 3 readings — all 3 should be validated individually

## Compatibility

- No API changes — `isValidReading()` is private, same as the replaced `isPlausible()`
- No new dependencies
- Readings that previously passed `isPlausible()` and are physiologically valid will still pass
- Only truly invalid readings are now rejected (sys <= dia, extreme pulse pressure, partial readings)

## Related

- iOS reference: `BPClient.swift`, `isValidReading()` function (lines 246-263)
- This change was identified as part of a broader contribution effort to bring the Android app to feature parity with the iOS version. See the accompanying `CONTRIBUTING.md` for contribution guidelines.

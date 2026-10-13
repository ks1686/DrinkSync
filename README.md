# DrinkSync

[![CI](https://github.com/ks1686/DrinkSync/actions/workflows/ci.yml/badge.svg)](https://github.com/ks1686/DrinkSync/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![IEEE](https://img.shields.io/badge/IEEE-11449671-00629B)](https://ieeexplore.ieee.org/document/11449671)

Low-cost IoT smart coaster plus a Kotlin Android app for hydration tracking. The Raspberry Pi streams drink events over Classic Bluetooth RFCOMM; the phone stores them in SharedPreferences and turns daily goals into something you can actually see.

Co-authored [IEEE publication](https://ieeexplore.ieee.org/document/11449671). Rutgers Spring 2025 capstone. This tree is Classic Bluetooth RFCOMM, not the paper's BLE radio.

## What it does

- Detects pours / sips from a load-cell coaster (HX711 + gyro for stability)
- Sends events to the phone over Classic Bluetooth RFCOMM/SPP
- Tracks daily intake in SharedPreferences with gamified goals
- Runs offline after pairing — no cloud account required

## Layout

```text
DrinkSync/
  Android/   Kotlin app (Compose, RFCOMM server, SharedPreferences)
  Python/    CPython helpers for Raspberry Pi (HX711, RFCOMM, tare / stability)
```

## Android app

Use JDK 21 (Android Studio's JBR, or `JAVA_HOME` pointing at Temurin/Homebrew `openjdk@21`). Newer JDKs can break this Gradle version.

```bash
cd Android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Open the `Android/` project in Android Studio for the emulator, Bluetooth permissions, and on-device install. The app listens as an RFCOMM service named `DrinkSyncApp`.

GitHub Actions runs the Python tests, plus `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`, on every push and pull request to `main`. The debug APK is uploaded as a build artifact.

Daily totals reset the first time you open the app on a new calendar day. Small sips under 1 oz stay in leftover grams and are added once they round to a full ounce.

## Coaster / firmware helpers

Production entrypoint (gyro stability, then a scale reading):

```bash
cd Python
python3 stability_scale_trigger.py
```

Set `DRINKSYNC_BT_MAC` to your phone's Classic Bluetooth address before starting the service (for example `export DRINKSYNC_BT_MAC=AA:BB:CC:DD:EE:FF`). There is no built-in default; the service refuses to send until it is set. `bt.py` is the RFCOMM client and retries the service lookup and connect up to 3 times per event. `hx711.py` (vendored driver) and `scale_persistent_tare.py` cover the load cell and persistent tare; `gyroscope.py` is a standalone sensor debug helper.

Each stable reading sends **only the drop since the last reading**. Putting a full cup down, then sipping twice, logs two sips — not the running total from full. A refill (weight goes up) resets the baseline and sends nothing. `scale_config.json` may omit `initialMaxWeight`; offset and reference unit are enough to load. `lastWeight` is written after each decision so a restart does not invent a drink.

Parser-only checks (no Pi hardware required):

```bash
cd Python
python3 -m unittest discover -v
```

## Wire format (v1)

Transport: Classic Bluetooth RFCOMM/SPP.

- Service name: `DrinkSyncApp`
- UUID: `c7506ec6-09d3-4979-9db3-3b85acad20fd`

One UTF-8 line per event, client to server, newline-terminated:

```text
Weight difference: 12.50 grams
```

`<grams>` is liquid removed since the previous stable reading. The phone converts grams to fluid ounces (`grams / 29.5735`, nearest int), keeps leftover grams that do not yet make an ounce, and adds whole ounces to today's intake. `grams <= 0` is ignored.

The phone still accepts two parse-only aliases so older senders keep working: `Average weight: <grams> grams` and the typo `Weight Differnce: <grams> grams`. Do not send those from new code.

After a successfully parsed positive amount the phone writes `OK` plus a newline. On parse failure it writes nothing. The Pi uses a ~2s socket timeout and treats a completed send as success if the ACK never arrives.

## Stack

**Kotlin** · Jetpack Compose · SharedPreferences · Classic Bluetooth RFCOMM · CPython · Raspberry Pi · HX711 · MPU6050

## License

MIT. See [LICENSE](LICENSE).

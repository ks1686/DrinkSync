# DrinkSync

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![IEEE](https://img.shields.io/badge/IEEE-11449671-00629B)](https://ieeexplore.ieee.org/document/11449671)

Low-cost IoT smart coaster plus a Kotlin Android app for hydration tracking. The firmware streams drink events over BLE; the phone persists them locally and turns daily goals into something you can actually see.

Co-authored [IEEE publication](https://ieeexplore.ieee.org/document/11449671). Rutgers Spring 2025 capstone.

## What it does

- Detects pours / sips from a load-cell coaster (HX711 + gyro for stability)
- Sends events to the phone over Bluetooth Low Energy
- Tracks daily intake in Room with gamified goals
- Runs offline after pairing — no cloud account required

## Layout

```text
DrinkSync/
  Android/   Kotlin app (Compose, BLE, Room)
  Python/    coaster firmware helpers (HX711, BLE, tare / stability)
```

## Android app

```bash
cd Android
./gradlew :app:assembleDebug
```

Open the `Android/` project in Android Studio for the emulator, BLE permissions, and on-device install.

## Coaster / firmware helpers

```bash
cd Python
python3 scale.py
```

`bt.py` handles the BLE side. `hx711.py` and `stability_scale_trigger.py` cover the load cell and motion filter.

## Stack

**Kotlin** · Jetpack Compose · Room · BLE · Arduino / MicroPython · HX711

## License

MIT. See [LICENSE](LICENSE).

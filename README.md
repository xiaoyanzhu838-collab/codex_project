# codex_project

Android phone app for the Jove smart glasses demo.

## Overview

This project is a Jetpack Compose Android application used to discover, bind, and communicate with Jove glasses over Bluetooth Low Energy.

Current capabilities include:

- BLE device scanning and binding
- Connection state display
- Brightness and volume controls
- Album and dashboard UI pages
- Language selection and translation UI scaffolding

## Tech Stack

- Kotlin 2.2.10
- Android Gradle Plugin 9.1.0
- Jetpack Compose Material 3
- Minimum SDK 28
- Target SDK 36

## Project Structure

- `app/`: Android application module
- `app/src/main/java/com/zxy/jove/`: main app and BLE logic
- `app/src/main/res/`: app resources
- `手机端蓝牙实现.md`: phone-side Bluetooth design notes
- `眼镜端蓝牙实现.md`: glasses-side Bluetooth design notes

## Run Locally

1. Open the project in Android Studio.
2. Make sure `local.properties` points to a valid Android SDK.
3. Sync Gradle.
4. Run the `app` configuration on an Android device with Bluetooth enabled.

You can also build from the command line:

```powershell
.\gradlew.bat assembleDebug
```

## Notes

- `local.properties`, `.gradle`, `.kotlin`, and build outputs are intentionally ignored.
- A real device is recommended for BLE testing.

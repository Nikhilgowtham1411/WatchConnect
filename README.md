# WatchConnect

A Kotlin + Jetpack Compose Android app for discovering nearby Bluetooth Low Energy devices and requesting Android-level pairing.

## Current features
- BLE device discovery through Android Bluetooth LE scanner.
- Live nearby-device list with advertised device name and RSSI signal strength.
- Android pairing request for a selected device.
- Runtime permission handling for Android 12+ and Android 11 and below.
- Responsive dark Compose interface.
- No fabricated device, connection, or battery statuses.

## Compatibility notes
BLE discovery is not full smartwatch integration. A watch may advertise over BLE but require its manufacturer's companion app, proprietary GATT services, or SDK for messaging, health data, watch-face installation, and other functions. Android pairing alone does not guarantee that a watch is connected to this app.

Cross-brand support must be validated against each watch model's supported Bluetooth services and APIs.

## Build
Open the repository in Android Studio with JDK 17 and Android SDK platform 36. Android Studio can generate the Gradle wrapper when importing the project. Run the app on a physical Android phone with Bluetooth enabled; BLE scanning may be limited on emulators.

- Application ID: `com.nikhil.watchconnect`
- Minimum Android: 8.0 (API 26)
- Target SDK: 36
- Version: 1.0.0

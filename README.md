# WatchConnect

A Kotlin + Jetpack Compose Android app for discovering nearby Bluetooth Low Energy devices and exploring a watch-friend interaction flow.

## Current app foundation
- BLE device discovery through Android Bluetooth LE scanner.
- Nearby device list with advertised device name and RSSI.
- Android system pairing request.
- Runtime Bluetooth permissions for Android 12+ and Android 11 and below.
- Dark responsive Compose interface.

## Friend interaction UI
- Nearby / Requests / Friends navigation.
- Create a local friend-invite preview from a discovered device.
- Accept or cancel a displayed request.
- Local friends list.
- Chat screen with message composer and quick reactions (wave, heart, laugh, thumbs-up, fire).

**Important:** Friend invites, accept/decline, chat messages and reactions are currently local UI previews only. They are not transmitted to another watch or phone. The app does not yet implement a WatchConnect peer protocol, BLE GATT data service, internet relay, authentication, or real-time delivery. The UI deliberately labels these actions as previews rather than reporting false delivery.

## Next engineering work
To enable real watch-to-watch interactions, implement a defined peer protocol and transport:
1. A companion WatchConnect app/service on each supported device.
2. BLE GATT service characteristics for discovery, handshake, friend requests, acknowledgements and messages, or an authenticated internet relay for long-distance communication.
3. Secure identity and consent, request expiry, replay protection, and message acknowledgements.
4. Model-by-model testing; generic BLE scanning and Android pairing do not provide universal smartwatch messaging access.

## Build
Open the repository in Android Studio with JDK 17 and Android SDK platform 36. Run the app configuration on a physical Android phone with Bluetooth enabled. BLE scanning may be limited on emulators.

GitHub Actions builds a debug APK and publishes it as the WatchConnect-debug workflow artifact when the build succeeds.

- Application ID: com.nikhil.watchconnect
- Minimum Android: 8.0 (API 26)
- Target SDK: 36
- Version: 1.0.0

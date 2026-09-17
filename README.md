# WiFi Share for Android

Native Kotlin app that moves files **directly between two Android phones over Wi-Fi Direct**. A QR code is only used to exchange the Wi-Fi Direct group name, passphrase, and the sender’s address. File bytes never go through Bluetooth, a cloud server, or the public internet.

## What you need

- Two Android phones (Android 8 or newer, with Wi-Fi Direct)
- [Android Studio](https://developer.android.com/studio)

## Install on a phone

1. Open Android Studio.
2. Choose **Open** and select this `WifiShare` folder.
3. Plug in a phone with USB debugging, or start an emulator that supports Wi-Fi Direct (a physical phone is strongly recommended).
4. Press **Run**.

Studio downloads Gradle and the Android SDK the first time. When it finishes, **WiFi Share** appears on the phone.

## Use it

On the sending phone:

1. Tap **Send** and pick one or more files.
2. Keep the QR code on screen.

On the receiving phone:

1. Tap **Receive**.
2. Scan the QR.
3. Files save to **Downloads / WiFi Share**.

Stay within a few metres. The transfer uses a Wi-Fi Direct group owned by the sender (`WifiP2pManager`) and a TCP socket on that link. If a phone does not support Wi-Fi Direct, the app says so instead of hanging.

## Permissions

Only what the transfer needs: nearby Wi-Fi devices (or location on older Android), camera for the QR scanner, and storage/media to read and save files.

## Build a release APK

In Android Studio: **Build → Generate Signed App Bundle or APK**. For a quick unsigned debug APK, **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

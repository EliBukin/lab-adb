# Lab ADB

A tiny Android app that turns **Wireless debugging** on by itself when the phone joins your lab
Wi-Fi, so a computer you've already paired can reconnect over adb without anyone touching the
phone.

Android switches Wireless debugging off every time the phone leaves Wi-Fi. Lab ADB switches it
back on when the phone is on the lab network again.

> **Status: work in progress.** The current build is only a placeholder screen. The features
> below describe the planned first version.

Android 11 or newer. Sideloaded only: it is not on Google Play.

## Security model

- The app holds **WRITE_SECURE_SETTINGS**. Android never grants this to an app by itself: you
  grant it once over adb (see below).
- It only ever writes one setting, `adb_wifi_enabled = 1`. It never writes anything else, and it
  never switches Wireless debugging off (Android does that itself when you leave the Wi-Fi).
- It only writes it when its switch is on **and** the phone is on the subnet you configure in the
  app (for example `192.0.2.0/24`). On any other network it does nothing.
- No other app can make it write anything.
- It has **no INTERNET permission**, no location access, no analytics, no ads and no third-party
  code.
- Android's own checks still apply: on a network you have never approved, Android asks
  "Allow wireless debugging on this network?" before it starts. And only computers you have
  already paired or authorised can connect.

## One-time setup

1. Install the app and open it once.
2. Grant the permission from a computer connected over adb:

   ```bash
   adb shell pm grant bukin.labadb android.permission.WRITE_SECURE_SETTINGS
   ```

3. In the phone's settings for Lab ADB, set **Battery** to **Unrestricted** and turn
   **Pause app activity if unused** off.
4. In **Developer options**, turn on **Disable adb authorization timeout**, so the computer's
   authorisation doesn't expire after 7 days.
5. In the app, type in the lab subnet and turn the switch on.
6. On each lab access point, the first time Wireless debugging starts there, tick
   **Always allow on this network** and tap **Allow**.

## How to revoke

Take the permission back:

```bash
adb shell pm revoke bukin.labadb android.permission.WRITE_SECURE_SETTINGS
```

Or simply uninstall the app.

## Build

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Licence

MIT, see [LICENSE](LICENSE).

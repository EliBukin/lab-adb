# Lab ADB

A tiny Android app that turns **Wireless debugging** on by itself when the phone joins your lab
Wi-Fi, so a computer you've already paired can reconnect over adb without anyone touching the
phone.

Android switches Wireless debugging off every time the phone leaves Wi-Fi. Lab ADB switches it
back on when the phone is on the lab network again.

> **Status: work in progress (0.4.0).** 0.2.0 switched Wireless debugging on, on a real phone; 0.3.0 fixed the triggers; 0.4.0 ignores direct Wi-Fi links.

Android 11 or newer. Sideloaded only: it is not on Google Play.

## What it does

- **Joins the lab Wi-Fi → Wireless debugging on**, within seconds.
- **Wireless debugging goes off at the lab → back on within seconds.** That covers moving to
  another access point or band of the same Wi-Fi (Android switches it off then), and switching
  it off by hand.
- **A backup check every 15 minutes** for anything the other two miss.
- **Direct Wi-Fi links are ignored:** car Android Auto, screen mirroring, Quick Share and the
  phone's own hotspot don't count as a Wi-Fi, and are never the lab.
- **After a restart** it works again once you unlock the phone for the first time.
- **One screen:**
  - the on/off switch;
  - the lab subnet (and an optional gateway);
  - status lines;
  - a **Test now** button;
  - the last 20 events.
- **How it decides it's the lab:** the phone has an IPv4 address on Wi-Fi inside the subnet you
  set, for example `192.0.2.0/24`. If you also set a gateway, the network's default route must
  go through it. Prefixes from /8 to /32 only.

## How it works

Three triggers, all doing the same check (switch on? lab subnet? Wireless debugging off?):

1. **Wireless debugging changed.** A job watches the `adb_wifi_enabled` setting and runs a
   second or two after it changes:
   - at the lab, it switches Wireless debugging back on at once;
   - after you leave, it arms trigger 2 for your next arrival.

   The app's own switch-on also wakes this job; it then finds Wireless debugging already on and
   does nothing.
2. **Wi-Fi joined** (the instant trigger). Android's "Wi-Fi available" notice. Android sends it
   only once and sends it at once for a Wi-Fi that is already connected, so the app arms it only
   while no Wi-Fi is connected. The event list shows `Instant trigger armed` and
   `Instant trigger off: Wi-Fi connected`, only when that changes.
3. **Every 15 minutes.** A backup check, which also re-arms the other two. For example, if you
   connect to another Wi-Fi first, that uses up trigger 2; leaving it changes nothing, so this
   check re-arms trigger 2.

All of them run on the phone only, with Android's own job and network APIs. With battery set to
"Unrestricted", Android runs them even while the phone is idle (Doze).

## Security model

- The app holds **WRITE_SECURE_SETTINGS**. Android never grants this to an app by itself: you
  grant it once over adb (see below).
- It only ever writes one setting, `adb_wifi_enabled = 1`. That write sits in exactly one place
  in the code. It never writes anything else, and it never switches Wireless debugging off
  (Android does that itself when you leave the Wi-Fi).
- It only writes when **all** of these hold:
  - its switch is on;
  - the phone is on the subnet you configure in the app;
  - Wireless debugging is currently off.

  On any other network it does nothing.
- **One try per connection.** After writing, it re-reads the setting a few seconds later. If
  Android put it back to off (most likely an access point you haven't approved yet), the app
  logs it and doesn't try again on that connection until the phone reconnects. So it won't make
  Android ask you again and again. **Test now** retries once on demand.
- Permissions: only WRITE_SECURE_SETTINGS, RECEIVE_BOOT_COMPLETED and ACCESS_NETWORK_STATE.
  **No INTERNET permission**, no location access, no analytics, no ads, no third-party code. It
  uses only Android's own APIs.
- Only the launcher screen is exported. No other app can make Lab ADB write anything. The event
  list never stores IP addresses.
- Android's own checks still apply: on a network you have never approved, Android asks
  "Allow wireless debugging on this network?" before it starts. And only computers you have
  already paired or authorised can connect.

## One-time setup

1. Install the app and open it once.
2. Grant the permission from a computer connected over adb:

   ```bash
   adb shell pm grant bukin.labadb android.permission.WRITE_SECURE_SETTINGS
   ```

3. Tap **App settings** in the app. Set **Battery** to **Unrestricted** and turn
   **Pause app activity if unused** off.
4. In **Developer options**, turn on **Disable adb authorization timeout**, so the computer's
   authorisation doesn't expire after 7 days.
5. In the app, type the lab subnet, tap **Save**, and turn the switch on.
6. On each lab access point, the first time Wireless debugging starts there, tick
   **Always allow on this network** and tap **Allow**.

All status lines should then show ✓.

## Pausing it

Use the switch in the app. While the switch is on and you're at the lab, turning Wireless
debugging off by hand doesn't last: the app turns it back on within a few seconds.

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

The APK lands in `app/build/outputs/apk/debug/`. Unit tests: `./gradlew test`.

## Licence

MIT, see [LICENSE](LICENSE).

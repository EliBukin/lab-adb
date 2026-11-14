# Lab ADB

A tiny Android app that turns **Wireless debugging** on by itself when the phone joins your lab
Wi-Fi, so a computer you've already paired can reconnect over adb without anyone touching the
phone.

Android switches Wireless debugging off every time the phone leaves Wi-Fi. Lab ADB switches it
back on when the phone is on the lab network again.

> **Status: 0.5.0.** Switching Wireless debugging back on is tested in daily use on a Samsung
> phone with Android 16: a restart, a full day at the lab, and trips away and back. Each time it
> switched it back on by itself, within half a second of joining the lab Wi-Fi. 0.5.0 adds an
> optional hint that tells your computer the phone's address and port; its first phone test is
> still to come.

Android 11 or newer. Sideloaded only: it is not on Google Play.

## What it does

- **Joins the lab Wi-Fi → Wireless debugging on**, within seconds.
- **Wireless debugging goes off at the lab → back on within seconds.** That covers moving to
  another access point or band of the same Wi-Fi (Android switches it off then), and switching
  it off by hand.
- **A backup check every 15 minutes** for anything the other two miss.
- **Optional: tells your computer where the phone is.** A short signed message with the phone's
  address and Wireless debugging port, so the computer can reconnect at once. Off until you set
  it up (see [The hint to your computer](#the-hint-to-your-computer-optional)).
- **Direct Wi-Fi links are ignored:** car Android Auto, screen mirroring, Quick Share and the
  phone's own hotspot don't count as a Wi-Fi, and are never the lab.
- **After a restart** it works again once you unlock the phone for the first time. Some Samsung
  phones send that signal a few minutes late.
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
- Permissions: only WRITE_SECURE_SETTINGS, RECEIVE_BOOT_COMPLETED, ACCESS_NETWORK_STATE and
  INTERNET. INTERNET is there only for the optional hint: it sends one short signed message, only
  over the lab Wi-Fi, only to the one address you set. The app never listens and never receives
  anything. No location access, no analytics, no ads, no third-party code. It uses only Android's
  own APIs.
- Only the launcher screen is exported. No other app can make Lab ADB write anything. The event
  list never stores IP addresses.
- The hint key is never shown, logged, backed up or exported. The event list never stores the
  key, the signature or any address.
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

## The hint to your computer (optional)

The computer that reconnects over adb (called ANXEL in the app) can learn the phone's address and
port from the phone itself, instead of searching for them. It needs a key and an address; without
both, nothing is ever sent.

**What is sent:** one UDP datagram, one line of JSON, at most 512 bytes:

```json
{"app":"labadb","v":1,"model":"SM-A556E","port":43999,"ip":"192.0.2.80","t":1763150000000,"mac":"…"}
```

- `model`: the phone model. `ip`: the phone's address on the lab Wi-Fi.
- `port`: the Wireless debugging port, or `0` when the app couldn't find it.
- `t`: the phone's clock in milliseconds. `mac`: the signature (below).

Nothing else: no names, no Wi-Fi names, no location.

**To whom:** only the address in the app's **ANXEL address** field, an IPv4 address and a port,
for example `192.0.2.10:47113`. It is empty by default. Host names are not accepted, so the app
never looks anything up.

**When:**
- right after the app switches Wireless debugging on: 3 times, at 0, 3 and 8 seconds, because UDP
  can drop a packet;
- once at each 15-minute check while the phone is at the lab with Wireless debugging on;
- once when you tap **Send now**.

**Only when all of these hold:** the switch is on, the phone is on the lab Wi-Fi, Wireless
debugging is on, the key is set and the address is set. The screen says which one is missing.

**The limits on INTERNET:**
- send-only: the app never listens and never waits for or reads a reply;
- the socket is bound to the lab Wi-Fi, so a hint never goes over mobile data, a VPN or another
  Wi-Fi;
- one address: the one you typed;
- no retries: a failed send is listed once in the events, and the next hint is the next try.

**The port:** Android doesn't give apps the Wireless debugging port. The phone announces it on the
Wi-Fi itself (mDNS service `_adb-tls-connect._tcp`), and the app finds that announcement with
Android's service discovery, keeping only the one at the phone's own address. That needs Android
14 or newer and takes up to 5 seconds. If it doesn't work, the hint carries port 0, and the
computer has to find the port itself.

**The signature:** `mac` is HMAC-SHA256 over the ASCII text `labadb|1|<model>|<port>|<t>`, as 64
lowercase hex digits. The key is 32 random bytes, kept as 64 hex characters; the HMAC uses the 32
raw bytes. Every packet has a new `t` and so a new `mac`. The receiver should check the `mac` (with
a constant-time compare), that `t` is close to its own clock and newer than the last one it
accepted, and trust the packet's source address rather than the `ip` field. Then a device that
doesn't hold the key can't pretend to be your phone.

**Putting the key in.** Make the key on the computer, for example with `openssl rand -hex 32`, in
a file only you can read. Copy it into the app's private storage straight from that file, so it
never appears on a command line:

```bash
adb shell "run-as bukin.labadb sh -c 'umask 077; mkdir -p files; cat > files/hint.key'" < /path/to/hint.key
```

`run-as` works with the debug build this repository makes. The screen then shows **Key set: yes**.
Then type the computer's address and port in **ANXEL address**, tap **Save**, and tap **Send now**
at the lab to try it.

**Taking it out:** clear the **ANXEL address** field and tap **Save**, and remove the key:

```bash
adb shell run-as bukin.labadb rm -f files/hint.key
```

Uninstalling the app removes both.

## Known limits

- **Another Wi-Fi straight to the lab Wi-Fi**, with no break in between: the instant trigger is
  not armed while another Wi-Fi is connected, so the 15-minute check switches it on.
- **A private Wi-Fi link that an app sets up for itself** (rare) still counts as a Wi-Fi and
  holds the instant trigger off. Again the 15-minute check covers it.
- **A new port each time.** Android picks a new random port whenever Wireless debugging starts.
  With the hint set up, the app tells the computer the port when Android's service discovery finds
  it (Android 14 and newer). Otherwise the paired computer has to find it, for example with
  `adb mdns services` on the same subnet, or a script that scans the phone's ports and runs
  `adb connect`.

## Pausing it

Use the switch in the app: it stops the hint too. While the switch is on and you're at the lab,
turning Wireless debugging off by hand doesn't last: the app turns it back on within a few seconds.
To stop only the hint, clear the **ANXEL address** field.

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

Keep `targetSdk` at 36. From 37 on, Android can match direct Wi-Fi links (such as a car's
Android Auto link) to the app's "Wi-Fi joined" request, and they would fire the instant trigger.
Android also plans a local-network permission for apps that target it, which the hint would then
need.

## Licence

MIT, see [LICENSE](LICENSE).

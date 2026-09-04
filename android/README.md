# on7o Bridge (Android)

The Android half of the architecture described in the project's root
`README.md`:

```
M5StickS3 --Bluetooth--> Android phone --Internet--> server
```

The phone is a connectivity bridge, not a real-time passthrough. It receives
a whole captured thought from a StickS3 over Bluetooth Low Energy (BLE) once
the button is released, stores it locally, then syncs it to the on7o server
in the background whenever it has internet connectivity. See `PROTOCOL.md`
for the Bluetooth framing this app expects; it originally proposed
Bluetooth Classic, before it turned out the StickS3's ESP32-S3 has no
Classic BT radio at all, only BLE.

## Modules

- `core`: pure Kotlin/JVM, no Android dependencies. The bridge protocol
  parser/writer, local capture storage, WAV header handling and the upload
  HTTP client. Runs under plain JUnit with no emulator needed.
- `app`: the Android application. Compose UI, the foreground BLE capture
  service, WorkManager-based sync scheduling, and DataStore-backed settings.

## Building

```
./gradlew :core:test              # protocol/storage/upload logic, no Android dependency
./gradlew :app:assembleDebug       # full app build
./gradlew :app:testDebugUnitTest
```

`local.properties` (gitignored) must point `sdk.dir` at a local Android SDK
checkout, for example:

```
sdk.dir=/home/jdamicp/Android/Sdk
```

## Running

Install the debug build on a device or the `Medium_Phone_API_36.1` emulator,
open Settings, and set the server's base URL (placeholder text shows the
expected shape, e.g. `http://192.168.1.10:8080`). On the emulator, a
`server/` instance running on the host machine is reachable at
`http://10.0.2.2:8080` rather than `localhost`. Since the on7o server is
LAN-only, plain HTTP with no TLS, the app's network security config
explicitly allows cleartext traffic.

Finding a StickS3 happens in the same Settings screen: tap Scan (grants
`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` on API 31+, or location access on older
versions, a BLE scanning quirk of those platform versions), pick the
discovered device, then toggle Connect on the Home screen. There is no
pairing step through Android's system Bluetooth settings: BLE devices here
are not bonded, the app connects directly to whichever address was picked.

## Verified against real hardware

A real StickS3 running the BLE firmware has sent a full push-to-talk capture
to this app end to end: connect, subscribe, record, and a byte-exact WAV
landing in local storage. See `PROTOCOL.md` and `m5sitcks3/README.md`'s
Status section for the bugs that surfaced getting there (an advertising
packet overflow, GATT indications needing manual MTU-sized chunking, and
NimBLE's own confirmed-indication status code being non-zero). Syncing that
capture on to a server has not been exercised yet with a real server on the
LAN, only against the local `server/` instance used to build `core`'s tests.

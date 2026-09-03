# on7o Bridge (Android)

The Android half of the architecture described in the project's root
`README.md`:

```
M5StickS3 --Bluetooth--> Android phone --Internet--> server
```

The phone is a connectivity bridge, not a real-time passthrough. It receives
captured audio from a paired StickS3 over Bluetooth Classic (SPP), stores it
locally, then syncs it to the on7o server in the background whenever it has
internet connectivity. See `PROTOCOL.md` for the Bluetooth framing this app
expects, and note its provisional status: there is no StickS3 firmware
implementing the other end of that link yet.

## Modules

- `core`: pure Kotlin/JVM, no Android dependencies. The bridge protocol
  parser/writer, local capture storage, WAV header handling and the upload
  HTTP client. Runs under plain JUnit with no emulator needed.
- `app`: the Android application. Compose UI, the foreground Bluetooth
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
open Settings, and set the server's base URL. On the emulator, a `server/`
instance running on the host machine is reachable at `http://10.0.2.2:8080`
rather than `localhost`. Since the on7o server is LAN-only, plain HTTP with
no TLS, the app's network security config explicitly allows cleartext
traffic.

Pairing a StickS3 (once its firmware speaks Bluetooth) happens through
Android's system Bluetooth settings first; this app only connects to
already-bonded devices, selected from the Settings screen, and never scans.

## What is not testable yet

There is no StickS3 firmware that speaks Bluetooth yet, so the connection
and framing logic in `bluetooth/BluetoothCaptureService.kt` cannot be
exercised end-to-end in this environment. Everything upstream of the socket,
the protocol parser, local storage, and the upload client, has unit test
coverage in `core` instead.

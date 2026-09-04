# on7o Bluetooth bridge protocol (Android to StickS3)

> Verified against real hardware: a real StickS3 flashed with this firmware
> has sent a full push-to-talk capture to the Android app over this exact
> protocol, confirmed frame by frame, and the WAV landed intact on the phone.
> Two real bugs were found and fixed getting here, both worth knowing about
> if this is ever reimplemented: a 128-bit service UUID plus the device name
> together overflow the 31-byte legacy advertising packet unless scan
> response is explicitly enabled, and a GATT indication is exactly one ATT
> packet with no fragmentation, so anything larger than (ATT MTU - 3) bytes
> has to be split into multiple indications by hand.

## Transport

Bluetooth Low Energy (BLE), GATT. This document originally proposed
Bluetooth Classic (RFCOMM/SPP), before it turned out the StickS3's ESP32-S3
has no Bluetooth Classic radio at all, only BLE 5.0. Only the original
ESP32 (not S2/S3/C3/C6) has classic BT.

One private, project-specific service (not adopted by the Bluetooth SIG):

| UUID | Purpose |
|---|---|
| `8b6c9f10-4b3e-4d2a-9f0a-1f6c7a2e0a01` | Service |
| `8b6c9f11-4b3e-4d2a-9f0a-1f6c7a2e0a01` | Capture characteristic, `INDICATE` only |

The StickS3 is the GATT peripheral (server), advertising this service under
the name `on7o-sticks3-01` (`ble_transport.cpp`); the Android phone is the
GATT central, discovering it with a short scan filtered on the service UUID
(`BleDeviceRepository.scan`), letting the user pick a device once in
Settings, and connecting to its remembered address afterward
(`device.connectGatt(...)`, `BluetoothDevice.TRANSPORT_LE`). There is no
bonding or pairing step: the link is unauthenticated, matching the server's
own "LAN-only, no auth" stance for this milestone.

**Record-then-send, not live streaming.** BLE's sustained throughput is well
below the 32 kB/s a live 16 kHz/16-bit mono PCM stream would need. The stick
records a whole push-to-talk capture into a PSRAM buffer first
(`capture_buffer.cpp`) and only starts sending once the button is released,
so the transport never has to keep up with the microphone in real time.

**Delivery is indication-based, not notification-based.** Every frame is
sent as a GATT indication (`characteristic.indicate(...)` on the firmware
side) and the firmware blocks until the phone's stack confirms it
(`ble_transport.cpp`'s `sendReliable`/`indicateOnceAndWait`) before sending
the next one. Plain notifications are not acknowledged at the GATT layer and
are the more usual choice for BLE streaming, but indications give simple,
built-in flow control for a bulk transfer where a dropped packet would
otherwise corrupt the frame stream, at a cost in raw speed a multi-second
"sending" pause easily absorbs.

**MTU is negotiated, not assumed, and one indication is one ATT packet.**
The phone requests `247` bytes after connecting
(`BluetoothCaptureService`'s `requestMtu`); the firmware asks NimBLE for the
connection's actual negotiated peer MTU (`NimBLEServer::getPeerMTU`) before
every send rather than assuming a fixed size. This matters beyond
performance: a GATT indication is not fragmented and reassembled the way an
HTTP chunk is, so a single `indicate()` call larger than (ATT MTU - 3) bytes
is rejected outright. A `CAPTURE_HEADER` frame is small enough to always fit
in one indication regardless of MTU, but `AUDIO_CHUNK` frames are not, and
have to be split into several MTU-sized indications, each confirmed before
the next goes out; the split is purely a transport-level detail invisible to
`FrameReader`, which only ever sees the reassembled byte stream.

**NimBLE reports a confirmed indication's status as `BLE_HS_EDONE`, not 0.**
This is the non-obvious one: for a plain notification, status 0 means "sent
successfully" and is the terminal event. For an indication, status 0 only
means "sent, not yet acknowledged" and NimBLE-Arduino's server code filters
that event out before it ever reaches `onStatus`; the real, peer-confirmed
completion arrives with status `BLE_HS_EDONE` (14). Treating "code != 0" as
failure, the natural first guess, makes every single successful indication
look like an error.

The connection is opened once and kept open across multiple captures, same
as the RFCOMM design this replaced: there is no per-capture handshake at the
transport level, capture boundaries are expressed entirely by the framing
below, which is unchanged from the original RFCOMM-based proposal. A frame's
own layout never depended on what was underneath it.

## Byte order

All multi-byte integer fields are little-endian, matching the ESP32's native
byte order and the existing WAV/PCM convention already used by
`server/src/main/java/org/on7o/server/ingest/WavHeader.java` and the
firmware.

## Frame header (present on every frame)

| Bytes | Field | Value |
|---|---|---|
| 0-3 | magic | ASCII `"ON7O"` (0x4F 0x4E 0x37 0x4F) |
| 4 | version | `0x01` |
| 5 | messageType | see below |

The magic, version and type are repeated on every frame, not just once per
connection. This costs a few bytes of overhead (negligible: 6 bytes against a
typical 1024-byte audio chunk) in exchange for letting the receiver
resynchronize by scanning forward for the next magic sequence if the stream
ever gets corrupted (partial write, Bluetooth stack glitch, firmware bug).
Given there is no real firmware to validate this against yet, this
robustness margin is deliberate over the leaner "header once per connection"
option.

Unknown `version` bytes: the reader drops the frame and treats the
connection as desynchronized, triggering a resync scan. There is no
version-negotiation handshake in v1.

## Message types

### 0x01 CAPTURE_HEADER (device to phone, once per capture)

Sent when the device arms a new capture (button pressed).

| Bytes | Field | Notes |
|---|---|---|
| 6 | deviceIdLen (uint8) | N |
| 7..7+N-1 | deviceId (UTF-8) | e.g. "sticks3-01", forwarded unchanged to the server's `device=` query parameter. The phone never substitutes its own identity for the originating device's. |
| next 4 | sampleRate (uint32LE) | e.g. 16000 |
| next 1 | channels (uint8) | e.g. 1 |
| next 1 | bitsPerSample (uint8) | e.g. 16 |
| next 1 | capturedAtLen (uint8) | M; 0 is valid and means "device has no clock" |
| next M | capturedAt (UTF-8) | ISO-8601, e.g. 2026-09-02T14:03:00Z, when present |

If `capturedAtLen` is 0, the phone stamps its own receipt wall-clock time
(when this CAPTURE_HEADER frame was parsed) as `capturedAt` for the eventual
upload, not the later sync time, since sync may happen much after capture
during an offline period and using sync-time would misrepresent when the
thought actually happened.

### 0x02 AUDIO_CHUNK (device to phone, zero or more per capture)

| Bytes | Field | Notes |
|---|---|---|
| 6..9 | chunkLength (uint32LE) | N, payload bytes that follow |
| 10..10+N-1 | payload | raw interleaved PCM samples, little-endian, matching the format declared in CAPTURE_HEADER |

No fixed block size is mandated by the protocol. The firmware's existing
512-sample (1 KB) block size from `audio_input.h` maps naturally to one
AUDIO_CHUNK per block, but this is a firmware choice, not a protocol
requirement.

The receiver enforces a sanity ceiling, `MAX_CHUNK_BYTES = 65536` (64 KiB,
far above the expected ~1 KB blocks). A corrupt or malicious `chunkLength`
is rejected rather than triggering a huge allocation.

### 0x03 CAPTURE_END (device to phone, once per capture)

| Bytes | Field | Notes |
|---|---|---|
| 6..9 | totalPcmBytes (uint32LE) | sum of all AUDIO_CHUNK payload bytes sent for this capture; lets the phone sanity-check nothing was dropped |

After CAPTURE_END, the connection remains open. The next CAPTURE_HEADER on
the same stream begins a new, independent capture.

### 0x04 CAPTURE_ACK (phone to device, optional, one per capture)

| Bytes | Field | Notes |
|---|---|---|
| 6 | status (uint8) | 0x00 = phone persisted the capture locally, 0x01 = error |

Sent by the phone after it finishes writing the capture to local storage,
not after it syncs to the server, which may happen much later, offline
tolerantly, which is the whole point of this architecture. Not implemented
by either side in this cut: the phone has no writable characteristic to send
it on, and the firmware has no code path expecting it. It exists so a future
revision has a hook to give the user on-device feedback ("phone got it")
distinct from "server processed it," without conflating the two.

## Error handling / stream loss

If the BLE connection drops mid-capture (no CAPTURE_END was ever
received), the phone discards the partial audio file rather than uploading
a truncated or corrupt WAV, mirroring `ThoughtStore.store()`'s own
delete-partial-directory-on-failure behavior on the server.

## Open questions for firmware review (not yet decided)

- Should there be an idle keepalive (PING/PONG) so the phone can detect a
  silently-dead socket faster than the next IOException from a blocking
  read? Omitted from v1 for simplicity; reconnection currently relies on
  that IOException, which is sufficient but not fast.
- Multi-byte length fields are capped at uint32 (up to roughly 4 GiB), far
  beyond the firmware's own 5-minute/9.6 MB safety cutoff. No practical
  concern, noted for completeness.

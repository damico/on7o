# on7o Bluetooth bridge protocol (Android to StickS3)

> PROVISIONAL. Not yet implemented or verified against real StickS3 firmware.
> This document is a design proposal made unilaterally from the Android side,
> because the firmware side of this link does not exist yet (see README.md's
> architecture diagram: StickS3 to phone over Bluetooth is a future
> milestone). Review and adjust before writing the ESP32-side implementation.

## Transport

Bluetooth Classic, RFCOMM (SPP), UUID `00001101-0000-1000-8000-00805F9B34FB`
(the standard SPP UUID; Arduino's `BluetoothSerial` library registers this by
default when a device calls `SerialBT.begin("name")`).

The StickS3 is the RFCOMM server (as `BluetoothSerial::begin` makes it by
default); the Android phone is the RFCOMM client, initiating the connection
via `device.createRfcommSocketToServiceRecord(SPP_UUID)` against a bonded
`BluetoothDevice` the user picked in Settings. Pairing (bonding) itself
happens out-of-band, through Android's system Bluetooth settings, before this
app is involved at all. This app never scans or initiates pairing.

The connection is opened once and kept open across multiple captures. There
is no per-capture handshake at the socket level: capture boundaries are
expressed entirely by the framing below.

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
tolerantly, which is the whole point of this architecture. A future firmware
may ignore this entirely in v1; it exists so a future firmware has a hook to
give the user on-device feedback ("phone got it") distinct from "server
processed it," without conflating the two.

## Error handling / stream loss

If the RFCOMM connection drops mid-capture (no CAPTURE_END was ever
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

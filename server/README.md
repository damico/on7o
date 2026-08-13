# on7o-server

Spring Boot backend. At this milestone it does exactly one thing: **receive captured audio and keep it**, immutably, with the technical metadata around it. Transcription, LLM interpretation, ontology reconciliation and clarification questions attach to these records later, by id.

## Running

```bash
cd server
mvn spring-boot:run
```

On startup the log prints the address to paste into the firmware:

```
ingest endpoint: http://192.168.0.10:8080/api/thoughts/audio
```

## Security posture

**There is none, deliberately.** No authentication, no TLS, CORS wide open, bound to `0.0.0.0`. This is a LAN-only device during the capture milestone — do not forward this port. The one input check that still holds is path traversal on thought ids.

## API

### `POST /api/thoughts/audio`

The device sends the captured bytes as the **raw request body** — no multipart, no JSON envelope — so the firmware only has to write a fixed header and stream what the microphone produces. Chunked bodies are accepted, so it can start uploading before it knows how long the user will keep the button pressed.

| Query param | Default | Meaning |
|---|---|---|
| `device` | `unknown` | Device id recorded with the capture |
| `sampleRate` | `16000` | Hz |
| `channels` | `1` | 1 or 2 |
| `bits` | `16` | 8, 16, 24 or 32 |
| `format` | `pcm` | `pcm` = raw samples, wrapped in a WAV container by the server; `wav` = already a WAV file |
| `capturedAt` | *(receive time)* | ISO-8601 or epoch millis; optional, since the device has no clock |

Raw PCM is the default because it keeps the firmware free of container bookkeeping: it cannot know the total length up front, so the server writes a placeholder WAV header and patches the sizes once the stream ends.

Returns `201` with the thought record:

```json
{
  "id": "20260813T194124Z-231c19f0",
  "deviceId": "sticks3-01",
  "capturedAt": "2026-08-13T19:41:24.872Z",
  "receivedAt": "2026-08-13T19:41:24.872Z",
  "audioFile": "audio.wav",
  "audioBytes": 64044,
  "pcmBytes": 64000,
  "durationMs": 2000,
  "sampleRate": 16000,
  "channels": 1,
  "bitsPerSample": 16,
  "remoteAddress": "192.168.0.31"
}
```

`413` when the capture exceeds `on7o.storage.max-bytes`; `400` on an unsupported format. A rejected capture leaves nothing behind on disk.

### Reading back

| | |
|---|---|
| `GET /api/thoughts?limit=50` | Most recent captures first |
| `GET /api/thoughts/{id}` | One thought record |
| `GET /api/thoughts/{id}/audio` | The WAV, playable in a browser |
| `GET /api/health` | `{"status":"ok"}` |

## Storage

The filesystem is the whole storage layer for now — one directory per thought, ids timestamp-prefixed so lexical order is chronological:

```
data/thoughts/20260813T194124Z-231c19f0/
    audio.wav
    thought.json
```

Configurable in `application.yml`:

```yaml
on7o:
  storage:
    root: data/thoughts
    max-bytes: 33554432   # ~17 min of 16 kHz mono
```

## Testing without hardware

```bash
# 2 seconds of 440 Hz tone as raw PCM
python3 -c "
import math,struct,sys
open('tone.pcm','wb').write(b''.join(
    struct.pack('<h', int(20000*math.sin(2*math.pi*440*i/16000))) for i in range(32000)))"

curl -X POST 'http://localhost:8080/api/thoughts/audio?device=test' \
     -H 'Content-Type: application/octet-stream' \
     --data-binary @tone.pcm
```

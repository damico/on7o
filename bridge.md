# bridge

Handoff from the session of **2026-08-13**, written so work can resume on another machine.

Branch: **`audio-capture-pipeline`** (two commits ahead of `main`).

---

## Where the project stands

The README describes ten milestones. This session closed the first four and started the fifth.

| # | Milestone | State |
|---|---|---|
| 1 | Push-to-talk capture on the M5 device | **Done**, running on hardware |
| 2 | Audio to a Spring Boot server over Wi-Fi | **Done**, verified byte-exact |
| 3 | Speech-to-text | **Done**, local Whisper, synchronous |
| 4 | Persist the original transcription | **Done**, `transcription.json` |
| 5 | LLM interpretation of thoughts | Not started — **this is where to pick up** |
| 6–10 | Entities, questions, UI, ontology, Bluetooth | Not started |

The loop `capture → transmit → store → transcribe` is proven end to end on real hardware. A 4-second thought spoken into the device came back as `"Alô, teste 1, 2, 3. Alô, teste 1, 2, 3."`

---

## What was built

### Server (`server/`)

Spring Boot 3.4.5, Java 21. See `server/README.md` for the full API.

```
org.on7o.server
├── ingest/     ThoughtStore, WavHeader, PcmFormat, Thought, StorageProperties
├── stt/        Transcriber, WhisperCppTranscriber, Transcription, TranscriptionProperties
├── api/        IngestController, ThoughtsController, ApiExceptionHandler
└── LocalNetworkConfig    CORS open, prints the LAN ingest URL at startup
```

Three design decisions worth carrying forward:

**The request body is raw audio, not multipart.** The device cannot know how long a thought will be before the user stops talking, so it streams with chunked transfer encoding and the server patches the WAV header once the stream ends. This keeps all container bookkeeping off the firmware.

**Layers are separate files.** `audio.wav` is the record. `thought.json` is its metadata. `transcription.json` is *one reading* of the audio, carrying the engine, model and latency that produced it. A better model later writes a new reading without rewriting what was captured. Interpretation should follow the same rule — write `interpretation.json`, do not touch the others.

**A failing engine never costs a thought.** The capture reaches disk before transcription starts; if Whisper is down, ingest still returns `201` with a null transcription. This was verified by killing whisper mid-test. Keep this property when adding the LLM stage.

### Firmware (`m5sitcks3/`)

M5Stack StickS3, Arduino + M5Unified. See `m5sitcks3/README.md`, especially the **Flashing notes** — this board is genuinely awkward to flash and that section will save an hour.

Two hardware behaviours cost real debugging time and are now encoded in the firmware:

- The **ES8311 codec emits exact zeros for close to a second** after `M5.Mic.begin()`. Starting the mic per capture swallowed the first word of every thought. It now starts at boot and stays running.
- M5Unified defaults mic `magnification` to **16, which clips speech**. Now 4 (`ON7O_MIC_MAGNIFICATION`).

Capture is gapless: M5Unified queues two recordings, so three rotating buffers let a block upload while the mic keeps running, and `drainBlock()` flushes what is still in flight on release.

---

## Setting up the new machine

### 1. Secrets — not in git

`m5sitcks3/on7o-capture/secrets.h` is git-ignored and **must be recreated**:

```bash
cp m5sitcks3/on7o-capture/secrets.h.example m5sitcks3/on7o-capture/secrets.h
# then edit in the Wi-Fi SSID and password
```

Also set `ON7O_HOST` in `m5sitcks3/on7o-capture/config.h` to the new machine's LAN address. The server prints it at startup (`ingest endpoint: http://...`).

**The device radio is 2.4 GHz only.** A 5 GHz network is invisible to it — the screen will show `no wi-fi` and the credentials will look wrong when they are not. This cost time in the last session.

### 2. Server

```bash
cd server && mvn spring-boot:run
```

Requires Java 21. No database — the filesystem under `server/data/thoughts/` is the whole storage layer.

### 3. Whisper

Not vendored. Rebuild it:

```bash
git clone --depth 1 https://github.com/ggml-org/whisper.cpp.git ~/whisper.cpp
cd ~/whisper.cpp
cmake -B build -DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=ON
cmake --build build -j$(nproc) --config Release

curl -L -o models/ggml-large-v3-turbo-q5_0.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin
```

The model is **574041195 bytes** — verify the size, the HuggingFace CDN stalled mid-download last time and left a truncated file that looked complete.

**If the new machine has an NVIDIA GPU, build with CUDA instead** — this is the single biggest win available:

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Release -DGGML_CUDA=ON
```

Then run it:

```bash
~/whisper.cpp/build/bin/whisper-server \
  -m ~/whisper.cpp/models/ggml-large-v3-turbo-q5_0.bin \
  -l pt --host 127.0.0.1 --port 8090 -t $(nproc)
```

Point `on7o.stt.url` at it if the port differs.

### 4. Flashing the device (only if firmware changes)

arduino-cli with the `esp32` core **3.3.11** and **M5Unified 0.2.19** (0.2.12 is the minimum for StickS3). Compile FQBN:

```
esp32:esp32:esp32s3:PSRAM=opi,FlashSize=8M,PartitionScheme=default_8MB,CDCOnBoot=cdc
```

Auto-reset does not work on this board. Enter download mode by hand — unplug, hold the side button, plug in while holding, release after ~3 s — then flash with `--before no-reset`. Full detail in `m5sitcks3/README.md`.

---

## Measured on the old machine

Baseline to compare against. Old box: **i5-1135G7, 4 cores, 15 GB RAM, no discrete GPU.**

| | |
|---|---|
| Transcription, 4 s audio | **~20 s** (≈5× real time, CPU only) |
| Firmware flash size | 1.11 MB (33% of partition) |
| Firmware static RAM | 52 kB (16%) |
| Capture bitrate | 32 kB/s (16 kHz, 16-bit mono) |

A GPU should cut transcription by an order of magnitude, which may make the synchronous design viable on its own.

---

## Open items

**Synchronous transcription exceeds the firmware's timeout.** `uploader.cpp` has `kResponseTimeoutMs = 8000`, but ingest now holds the request open for ~20 s on CPU. The server stores and transcribes correctly, but the device displays `lost link`. Either raise that constant and reflash, or move transcription to a worker. On a GPU machine the latency may drop below 8 s and the problem disappears — measure before changing anything.

**A ~3 ms click at the start of each capture.** The first block after an idle gap carries whatever DC sat in the I2S DMA buffer. The fix is committed (`nextBlock()` drops the first block) but **has not been flashed** — the device is running the version without it. Harmless for speech-to-text; flash it whenever the board is next open.

**`ON7O_HOST` is a hardcoded IP.** Fine for a test hotspot, painful in real use. mDNS discovery would remove the reflash-on-IP-change cycle.

**No tests.** Nothing in `server/src/test`. `WavHeader`, `PcmFormat.durationMs` and `ThoughtStore` path-traversal rejection are the obvious first targets.

**Server is unauthenticated by design.** No auth, no TLS, CORS open, bound to `0.0.0.0`. Deliberate for the LAN milestone. Revisit before anything leaves the local network.

---

## Suggested next step

Milestone 5: LLM interpretation. The pieces are in place — `Transcription` gives clean pt-BR text, and `examples/test.ttl` already sketches the target ontology by hand (`Thought`, `Claim`, `Question`, `Entity`, plus `knowledgeStatus` of `Asserted`/`Inferred`/`Hypothesized` and `confidence`).

Mirror the `stt` package: a narrow `Interpreter` interface, one implementation, output to `interpretation.json`. Keep the source-preservation and never-lose-a-thought properties.

The README is emphatic that **not understanding is useful information** — the interpreter should emit `ClarificationQuestion` records for what it cannot resolve rather than inventing answers. That is the feature that makes on7o different from a transcription app, so it is worth building early rather than bolting on.

For testing interpretation, capture something semantically rich rather than "alô teste" — the README's own examples ("preciso marcar o dentista da Ana", the SciCrop board meeting) are good material, since they are designed to expose unresolvable references.

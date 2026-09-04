# bridge

Handoff from the sessions of **2026-09-03** and **2026-09-04**, four threads: moving transcription off
the request thread and letting the network ask about its own gaps (from another machine, 2026-09-01),
scaffolding the Android bridge app (this machine, 2026-09-02), building the StickS3's BLE firmware and
proving the whole Bluetooth bridge end to end on real hardware for the first time (this machine, later
2026-09-03), and diagnosing why a proven capture took roughly ten times its own length to transfer, then
making the transfer survive the phone's screen locking in a pocket (this machine, 2026-09-04).

Branch: **`feature/sticks3-ble-bridge`**

---

## Where the project stands

| # | Milestone | State |
|---|---|---|
| 1 | Push-to-talk capture on the M5 device | **Done**, running on hardware |
| 2 | Audio to a Spring Boot server over Wi-Fi | **Done**, verified byte-exact |
| 3 | Speech-to-text | **Done**, local Whisper, off the request thread |
| 4 | Persist the original transcription | **Done**, `transcription.json` |
| 5 | LLM interpretation of thoughts | **Done**, rThought + qThought + cThought, plus entity derivation |
| 6 | Ontology diagrams per stage | **Done**, hand-rolled inline SVG |
| 7 | HCIN financial projection | **Done**, all 16 issues |
| 8 | Bluetooth bridge (StickS3 BLE + Android app) | **Done**, verified on real hardware: a real capture recorded on the stick landed byte-exact on the phone over BLE |

The loop `capture -> transmit -> store -> transcribe -> interpret` is proven end to end on real hardware
over Wi-Fi. The Bluetooth leg (`M5StickS3 --Bluetooth--> Android --Internet--> server`) is now proven
too, stick to phone: a real push-to-talk capture went from the StickS3's BLE firmware to the Android
app's local storage, byte-exact. What has not been proven yet is the phone's own sync to a server over
the internet leg with a real server address configured, only against `core`'s test fixtures.

---

## What was added this session: the Android bridge app

New Gradle project under `android/` (previously an empty placeholder directory), built on branch
`feature/android-bluetooth-audio-sync` and merged into `main` this session. Purpose: receive captured audio from a paired StickS3 over
Bluetooth Classic and sync it to the same `/api/thoughts/audio` endpoint the firmware already uses over
Wi-Fi, tolerating offline periods (capture locally, sync in the background). The StickS3 firmware itself
was not touched: it has no Bluetooth code, so the Bluetooth link could not be tested against real
hardware this session.

```
android/
  core/                       (pure Kotlin/JVM, no Android deps, runs under plain JUnit)
    protocol/                 BridgeProtocol, Frame, FrameReader, FrameWriter
    capture/                  Capture, SyncState, WavHeader, CaptureWriter, CaptureStore
    sync/                     UploadClient, UploadResult
  app/                        (the Android application)
    bluetooth/                BluetoothCaptureService (foreground service, RFCOMM), PairedDeviceRepository
    sync/                     SyncWorker, SyncScheduler (WorkManager)
    settings/                 SettingsRepository (DataStore: server URL, paired device)
    ui/                       HomeScreen, SettingsScreen (Compose)
  PROTOCOL.md                 the Bluetooth framing spec, marked provisional
```

Decisions worth remembering:

- **Two Gradle modules, split specifically because the Bluetooth link is untestable here.** Everything
  that does not need `android.*` (the protocol parser, local storage, the WAV header math, the upload
  HTTP client) lives in `core` and has unit test coverage: 11 tests, all green
  (`FrameReaderTest`, `WavHeaderTest`, `CaptureStoreTest`, `UploadClientTest`). Only the socket handling
  in `BluetoothCaptureService` is unverified end to end.
- **The Bluetooth framing protocol is a proposal, not a spec.** There is no firmware counterpart yet.
  `PROTOCOL.md` documents a length-prefixed frame format (CAPTURE_HEADER, AUDIO_CHUNK, CAPTURE_END,
  CAPTURE_ACK) over the standard SPP UUID, with every frame repeating its magic/version/type so a
  corrupted stream can resynchronize, matched by KMP search rather than a naive scan so a magic-like
  byte sequence inside audio payload cannot desync the reader. It needs review before the ESP32 side is
  written.
- **The phone is a store-and-forward bridge, not a passthrough.** Captures land in
  `{filesDir}/captures/{id}/audio.wav` + `capture.json` (same shape as the server's own `ThoughtStore`,
  right down to reusing its id scheme and its placeholder-header-then-patch approach to streaming to
  disk), then `SyncWorker` uploads whatever is not yet `SYNCED` whenever `WorkManager` sees
  `NetworkType.CONNECTED`, plus immediately after a capture finishes and on a manual "Sync now".
- **Upload reuses the existing server contract unchanged.** `format=wav` on the query string, since the
  phone always has the complete file by sync time, unlike the firmware's live HTTP-chunked stream. No
  new server-side endpoint.
- **No Room, no Hilt.** Flat files plus manual constructor wiring in `BridgeApplication`, matching
  `ThoughtStore`'s own "the filesystem is deliberately the whole storage layer" stance and the project's
  general aversion to heavyweight frameworks where a plain approach works.
- **Settings, not `config.h`.** The server base URL and the paired device address are runtime,
  DataStore-backed settings, not compiled in, because the phone roams between networks and devices in a
  way the stationary StickS3 does not.

### Gradle/AGP version pins, and why they are not the newest available

Getting a real build green in this environment surfaced compatibility constraints no amount of reading
would have: AGP 9.3.0 needs Gradle >=9.5 and, from AGP 9.0 on, folds Kotlin support into AGP itself,
which conflicts with applying the `org.jetbrains.kotlin.android` plugin separately. The latest Compose
BOM and OkHttp releases both require `compileSdk 37`, but only platform 36 is installed locally. After
downgrading through several intermediate combinations, the pins that actually build are:

| Component | Version | Why not newer |
|---|---|---|
| AGP | `8.13.2` | 9.x's built-in-Kotlin change and its Gradle floor were both blockers |
| Gradle | `9.1.0` | wrapper distribution download from `services.gradle.org` was extremely slow in this environment; the wrapper jar and `gradlew`/`gradlew.bat` were fetched directly from GitHub (`raw.githubusercontent.com/gradle/gradle/v9.1.0/...`) instead, then the full distribution was downloaded separately and used directly (not through the wrapper) to run the verification builds |
| Compose BOM | `2026.03.01` | `2026.08.00` requires `compileSdk 37` |
| OkHttp | `5.1.0` | `5.5.0` requires `compileSdk 37` |

`compileSdk`/`targetSdk` stayed at `36`, `minSdk` at `26`. None of this blocks anything: it is recorded
here so the next session does not have to rediscover it, and revisited once `compileSdk 37` /
newer AGP is worth adopting.

### Verification actually run

```
./gradlew :core:test          # 11/11 tests green: protocol framing, WAV header, local store, upload contract
./gradlew :app:assembleDebug  # produces app-debug.apk (13.8 MB)
```

Not run: anything requiring a real StickS3 speaking Bluetooth (there is no firmware for it yet), and no
manual UI walkthrough on the `Medium_Phone_API_36.1` emulator (build/test verification only this
session).

All eighteen issues are closed on GitHub, each with the commit that implemented
it: `945c2a8` for issues 1 to 16, `6e61879` for 17 and 18.

---

## What was added later this session: BLE firmware, verified on real hardware

Picking up right where the Android scaffold left off: the user asked to build the StickS3's Bluetooth
firmware so the simplified local loop (stick -> phone -> LAN -> notebook server) could actually be
tested. Exploring the firmware surfaced the blocker the earlier RFCOMM decision had missed: the
StickS3's ESP32-S3 has no Bluetooth Classic radio, only BLE. With the user confirming record-then-send
over live streaming, both the firmware and the Android app's Bluetooth layer were rewritten for BLE,
and then, for the first time this project has had the hardware to do it, actually tested end to end on
a real StickS3 and a real Samsung phone connected over adb (USB for the stick's serial port, wireless
debugging for the phone once the USB port was needed for the stick).

```
m5sitcks3/on7o-capture/
+-- ble_transport.h/.cpp   (NimBLE GATT peripheral: advertise, indicate, MTU-aware chunking)
+-- capture_buffer.h/.cpp  (PSRAM buffer for one whole record-then-send capture)
+-- ble_protocol.h         (frame constants, mirrors BridgeProtocol.kt by hand)
    uploader.h/.cpp, WiFi, secrets.h.example: removed, the phone owns the internet leg now
android/app/.../bluetooth/
+-- BleDeviceRepository.kt        (replaces PairedDeviceRepository: BLE scan, not bonded devices)
+-- GattNotificationInputStream.kt (bridges GATT callbacks into a blocking InputStream for FrameReader)
    BluetoothCaptureService.kt: RFCOMM socket -> BluetoothGatt central, rewritten
```

Getting a real capture through exposed four bugs no amount of code review would have caught, three in
the new BLE code and one that was always there waiting for two connection attempts to race:

- **A double-connect race.** `BluetoothCaptureService.onStartCommand` launched a new
  `connectionLoop()` coroutine on every start command with no guard. Two taps on Connect (which is
  exactly what happened while testing) started two loops racing on the same `gatt` field, one calling
  `close()` on the connection the other had just opened mid-setup. Fixed with a `Job` guard: a second
  start command while one loop is already active is now a no-op.
- **Advertising overflow.** The device name and the 128-bit service UUID together do not fit in the
  31-byte legacy advertising packet (3 + 17 + 18 = 38 bytes). Confirmed with this machine's own
  `bluetoothctl info`: the stick was advertising its name but no UUID at all, so the Android app's
  UUID-filtered scan found nothing. `NimBLEAdvertising::enableScanResponse(true)`, called before
  `setName()`, was the missing line; NimBLE moves the name into scan response data once that is on,
  freeing the primary packet for the UUID.
- **Indications are not chunked automatically.** A GATT indication is exactly one ATT packet, not a
  fragmented stream the way an HTTP chunk is. `AUDIO_CHUNK` frames up to 4 KiB were being handed to
  `indicate()` in one call, which simply fails for anything bigger than the negotiated MTU allows. Once
  the `CAPTURE_HEADER` frame (small enough to always fit) started confirming while every `AUDIO_CHUNK`
  failed, this became the obvious next suspect. Fixed by querying `NimBLEServer::getPeerMTU()` and
  splitting every send into MTU-sized indications, each confirmed before the next goes out; frame
  boundaries stay a pure property of the byte stream, invisible to the chunking.
- **The real root cause: `BLE_HS_EDONE` is success, not failure.** Even a 24-byte `CAPTURE_HEADER`,
  nowhere near any MTU limit, kept failing after the chunking fix. Reading NimBLE's own
  `ble_gatts_indicate_rx_rsp` (the handler for a genuine peer confirmation) showed it reports status
  `BLE_HS_EDONE` (14), not 0, and that status-0 events for indications are filtered out by the library
  before `onStatus` ever fires, since they only mean "sent, not yet acknowledged." The code was
  treating every successful confirmation as an error. One-line fix: `code == 0 || code == BLE_HS_EDONE`.

With all four fixed, a real button press produced `ble send finished: ok=1 samples=59904` on the
stick's serial output and a `119808 bytes` capture (exactly `59904 * 2`) on the phone. Configuring the
server's LAN address in Settings and tapping Sync now closed the loop the rest of the way: the server
logged `captured 20260903T220524Z-1fdd65e1 from sticks3-01 (3744 ms, 119852 bytes)`, wrote a valid
16 kHz mono PCM WAV to disk, and the capture in the app turned `Synced`. Transcription failed only
because whisper.cpp was not running, exactly the non-blocking failure the async-transcription work
from earlier this session was built to tolerate. This is the first time any part of this project's
Bluetooth design has run against real hardware, and the first time the whole architecture diagram in
`README.md`, stick to phone to server, has been proven end to end rather than in two separate halves.

A smaller, cosmetic issue found along the way: the stick's idle screen only redraws on a state
transition, so "no phone" stayed on screen after the phone actually connected, until the next button
press or result timeout forced a redraw. Fixed by checking `ble::connected()` against what is currently
shown once per loop iteration while idle, redrawing only when it changes.

Also added, at the user's request: a placeholder (`http://192.168.1.10:8080`) on the Settings screen's
server URL field, so it is obvious what to type without reading the helper text first.

### Verification actually run

Real hardware, not emulation: firmware built and flashed with `pio run -t upload` (manual download mode
per this file's own instructions), Android app built with `:app:assembleDebug` and installed via `adb
install -r` on a physical Samsung SM-S711B. `:core:test` was not rerun this half of the session since
none of `core` changed. `ble_transport.cpp`'s diagnostic `Serial.printf` calls (indicate rejected /
timed out / confirmed-with-error, subscribe events, negotiated MTU) were left in deliberately: they are
what made each of the four bugs above findable, and they only print on the error/one-per-connect paths,
not in a hot loop.

### Transcription no longer happens inside the ingest request

This was the last thing standing between the device and a clean end-to-end run.
`IngestController` transcribed before answering, so the request stayed open for
as long as Whisper took, measured here at **21.7 s for 4 s of audio** on CPU. The
firmware gives up after `kResponseTimeoutMs = 8000` and shows `lost link`, so
every real capture looked like a failure on the device while the server had
stored the audio perfectly and transcribed it correctly.

```
ingest/
+-- TranscriptionWorker   (transcribe and save, on a virtual thread or on the caller's)
```

`POST /api/thoughts/audio` now answers `201` as soon as the audio is on disk,
measured at **79 ms** for the same capture, and hands the thought to the worker.
The transcription is null in that response and arrives under
`GET /api/thoughts/{id}/transcription` when the engine finishes.

- **The capture is the record and the transcription is derived from it.** That
  was already the rule when the engine failed, where a `201` with a null
  transcription is the honest answer. Being slow is the same kind of event as
  failing, so it gets the same answer, and the pending state is one the index
  page already renders.
- **One implementation of "transcribe and store".** `TranscribeController` calls
  the same worker method rather than repeating the store-transcribe-save
  sequence, so the SSE path and the ingest path cannot drift.
- **A background job is not retried.** One still running when the server stops is
  lost, which costs a button press and not a thought: the audio is on disk and
  the transcribe endpoint runs it again.
- **The in-flight guard is only on `submit`.** Two ingests of the same thought
  cannot double-transcribe it, while the explicit endpoint always runs, because
  pressing a button means "do this now".

Nothing about this needed a firmware change. The 8 s timeout is now generous
rather than tight, and was left alone.

### The network can now ask about its own gaps

Every question on7o asked until today started from something the user said: the
qThought stage reads one thought and asks what it cannot resolve from the text.
It never saw the HCIN, which is why `subjectRef` and `predicateRef` had been null
on every question since the contract was written. There was no node to point at,
because the question did not come from the graph.

```
clarification/
+-- NetworkClarificationService  (validate, then ask about what is missing)
+-- GapPhrasing                  (a SHACL message is not a question)
+-- ClarificationService.addQuestions / ProposedQuestion
hcin/
+-- HcinRepository.observingThought, describe, connectsSocialEntities
api/
+-- POST /api/hcin/clarifications, NetworkClarificationResponse
```

On the first real capture this produced three questions the pipeline had no
other way to ask: which layer and which setting the invitation between the ego
and Ninoska belongs to, and when the interaction happened.

- **A gap is attached to the thought that produced it.** A node has no
  conversation of its own, but the thought that introduced it does, so the
  question joins that thought's questions and travels through the lifecycle that
  already exists. The provenance graph is what makes that possible, and it is
  the first time anything has read it back.
- **The id comes from the gap.** Node plus property, and nothing else, so the
  same gap found on a later run is recognized as the question already asked.
- **The text is refreshed while the question is open.** The id says which gap is
  being asked about; the wording is only the current rendering of it. Once
  answered or skipped the wording freezes, because it is what the user read.
  This was written after a first run produced "the relationship 57dcc0e01676"
  and re-running could not improve it.
- **These questions are never required.** A required question blocks
  consolidation, and the thought that produced the node has already been
  consolidated. Blocking it retroactively would strand the thought.
- **A gap is only asked about when it has an answer.** Reconciliation mints an
  `hcin:Relationship` for every statement a thought makes, geography included, so
  without a test the network asked which relational layer connects a venue to the
  city it stands in. Layer and context are now asked only about nodes joining two
  social entities. The gap stays in the validation report, where it is a true
  observation; it just does not become a question.

### A shape and a prompt that contradicted each other

The consolidation prompt says never to invent a date, an amount or a currency the
thought did not state. Three shapes required exactly those, as violations. The
two rules had never met, because every fixture had complete data.

The first real capture was an invitation with no date, which the model correctly
left out and the shapes then called a fatal defect. Presence and well-formedness
are now separate constraints in all three places: a missing date or currency is a
clarification candidate, while a malformed one, or two dates on one interaction,
stays a violation. `ShaclValidationServiceTest` gained the two cases that pin the
distinction.

### The device host was stale again

`ON7O_HOST` pointed at `10.109.118.42`, from a network the machine is no longer
on. It now reads `172.25.234.30`, which is what the server prints on startup.
The Wi-Fi SSID in `secrets.h` did not change. This is the third time the address
has been edited by hand, which is the argument for mDNS getting made for us.

---

## What was fixed this session (2026-09-04): a ten-times-realtime transfer, and the locked screen

The previous session proved a capture could reach the phone byte-exact over BLE, but that capture was
short. The very next real recording exposed what the short test never had a chance to hit: roughly ten
seconds of transfer for every second recorded, which invalidates push-to-talk as a design regardless of
how faithful the bytes are once they arrive. Replacing the stop-and-wait `indicate()` per `AUDIO_CHUNK`
packet with unconfirmed `notify()` fixed short captures immediately, but anything past ten to twelve
seconds still failed outright, which is what this session actually chased down, on real hardware with
the stick's serial output and the phone's `logcat` captured side by side.

```
m5sitcks3/on7o-capture/ble_transport.cpp
+-- notifyOnce() gated by a small in-flight credit window (kMaxInFlightNotifications), released only
    when onStatus() reports a notification's transmission actually finished
+-- indicateOnceAndWait() gained the same local-rejection retry notifyOnce() already had
m5sitcks3/platformio.ini
+-- CONFIG_BT_NIMBLE_MSYS1_BLOCK_COUNT raised from the library default of 12 to 64
android/app/.../bluetooth/BluetoothCaptureService.kt
+-- requestConnectionPriority(CONNECTION_PRIORITY_HIGH) once the GATT connection is established
```

- **`BLE_HS_ENOMEM`, not silence, was the actual signal.** `onStatus()` already existed, but only to log
  a failure no code path acted on. It fired on nearly every `AUDIO_CHUNK` notification, and decoding the
  status code against NimBLE's own header (`ble_hs.h`) showed code 6 is `BLE_HS_ENOMEM`: the stack's
  shared `MSYS_1` mbuf pool, 12 blocks of 256 bytes by default, exhausted by a sustained flood of
  notifications the pool was never sized for. `nimconfig.h` names this exact scenario in its own comment.
  Raising the pool to 64 blocks was the decisive fix; a smaller in-flight window on top of it kept the
  flood from immediately refilling it.
- **`indicate()` had no retry; `notify()` did.** `CAPTURE_HEADER` and `CAPTURE_END` go out as confirmed
  indications, and the single local-rejection check on `indicate()` had no retry loop at all, unlike the
  30-attempt retry `notify()` already carried. Under a congested queue this is exactly where a capture
  that had otherwise gone perfectly would fail, at the very last frame. Giving `indicate()` the same
  retry evened out the asymmetry, and turned out to be the one change the locked-screen case needed most.
- **A locked screen changes the BLE connection interval, and there is no API to stop it.** Once the
  phone's screen goes dark, Android renegotiates a slower connection interval regardless of any earlier
  `CONNECTION_PRIORITY_HIGH` request, which is enforced by the platform, not something an app can opt
  out of. The fix is not fighting that renegotiation; it is making the firmware patient enough to keep
  working at whatever rate the link actually allows, which the retry and credit changes above already
  did.
- **The firmware's own `ok=1` was never proof the phone kept the capture.** `sendCapture()` only confirms
  `CAPTURE_HEADER` and `CAPTURE_END`; a lost `AUDIO_CHUNK` notification is caught solely by the phone
  comparing received bytes against `CAPTURE_END`'s declared total, silently, on the Android side. A
  capture the stick reported as sent could still have been discarded on the phone without either side
  saying so out loud. Every fix in this session was verified against that byte-exact comparison, not
  against the stick's own screen.
- **Samsung's own battery management can force-stop the app, separately from Doze.** Turning on
  unrestricted battery usage for the app through Settings triggered `Force stopping org.on7o.bridge`
  from `com.android.settings` itself, visible in `logcat`. The service restarted on its own afterward,
  but it is worth knowing that changing this setting is not itself a no-op.

### Verification actually run

Real hardware throughout, diagnosed rather than guessed at: the stick's serial output and the phone's
`logcat` captured concurrently during every test round. Screen-on: three back-to-back captures of 22 to
25 seconds each, all landing byte-exact (`pcmBytes` in `capture.json` matching the stick's own reported
sample count exactly). Screen locked, phone in a pocket for several minutes first: two captures, both
byte-exact, pulled off the phone with `adb shell run-as org.on7o.bridge` (there is no playback screen in
the app yet) and played back to confirm the audio itself is intact, not just the byte count.

---

## The HCIN-FIN track

Sixteen issues on GitHub (`[HCIN-FIN]`) implement `HCIN-fin-proj.md`: an ego-centric
financial projection computed from a persistent RDF dataset. They form one dependency
chain, in blocks:

| Block | Issues | What it is |
|---|---|---|
| Ingestion and contracts | 1-2 | **Done.** Text ingestion, stable DTOs with question ids |
| Clarification lifecycle | 3-5 | **Done.** Synchronous `/analyze`, question states, `/consolidate` |
| Semantic core | 6-9 | **Done.** Jena TDB2, HCIN vocabularies, SHACL, `/reconcile` |
| Metrics | 10-11 | **Done.** Interaction proximity, financial magnitude, vector `w(t)` |
| Projection and UI | 12-14 | **Done.** Projection DTO, SVG graph, `asOf` time navigation |
| Tests | 15-16 | **Done.** Deterministic fixture, end-to-end test with no LLM network |

All sixteen are implemented and covered by tests.

---

## What was added in the previous session (2026-08-24): the HCIN-FIN track in detail

### Issue #1: `POST /api/thoughts/text`

A thought that arrives already written. This unblocks the whole HCIN track: the
fixtures need explicit timestamps and must not depend on hardware or on Whisper.

```
api/
+-- TextThoughtController   (POST /api/thoughts/text)
+-- TextThoughtRequest      (validated body)
+-- TextThoughtResponse
ingest/
+-- ThoughtService          (facade: thought + its transcription)
```

`capturedAt` is mandatory and must carry an explicit ISO-8601 offset. Jackson is
configured in `application.yml` not to rewrite offsets into the server timezone
(`adjust-dates-to-context-time-zone` and `write-dates-with-context-time-zone`
both false), so the instant a caller states survives the round trip.

`ThoughtService.ingestText` writes `thought.json` and `transcription.json`
together, with `engine: "text"`, so rThought, qThought and cThought treat a text
thought exactly like a transcribed capture. No audio file is created.

### Issues #2 to #5: clarification as a resource

The pipeline existed but its API was raw Turtle over SSE, with questions and
answers as two string lists that only lined up by position. It is now addressable.

```
clarification/
+-- ClarificationQuestion   (id, thoughtId, text, required, status, createdAt)
+-- QuestionStatus          (OPEN, ANSWERED, SKIPPED, OBSOLETE)
+-- AnswerRevision          (one version of one answer)
+-- AnswerSubmission        (an answer on its way in)
+-- QuestionIds             (stable ids derived from thought, index and text)
+-- ClarificationStore      (questions.json, answers.json, legacy formats included)
+-- ClarificationService    (the lifecycle)
analysis/
+-- ThoughtAnalysisService  (stages 1 and 2, idempotent)
+-- ConsolidationService    (stage 3, idempotent, gated on required answers)
+-- AnalysisResult / ConsolidationResult and their statuses
ontology/
+-- TurtleMetrics           (statements, entities, relationships, via Jena)
api/
+-- AnalysisController      (POST /analyze, POST /consolidate)
+-- ClarificationController (GET questions, POST/GET answers)
+-- ten DTO records
```

Decisions worth remembering:

- **Ids, not positions.** Every question carries a stable id derived from its
  thought, its index and its text, so re-reading the same input yields the same
  id. Answers name a question id, so partial and out-of-order submissions are
  safe.
- **Questions are never deleted.** Re-analysis retires the old ones as
  `OBSOLETE` and keeps them in the same file. They stay queryable after
  consolidation, and an answer the user already gave is never orphaned.
- **Answers are append-only.** A correction is revision 2, not an overwrite.
  `GET /answers` returns the current answer per question; `?history=true` also
  returns every superseded version.
- **Blank is not an answer.** An answer must carry text or be explicitly
  `skipped`. The questions page sends blanks as skips, since the user saw the
  question and chose to leave it.
- **Consolidation is gated, not blocked.** An unanswered required question
  returns `MISSING_REQUIRED_ANSWERS` and the ids, without calling the model.
  `allowIncomplete` proceeds anyway, which is what the SSE flow does.
- **One implementation per stage.** `UnderstandController` no longer holds
  pipeline logic: its SSE endpoints call the same services as the REST ones,
  always with `force`, because pressing a button means "do this now".

Two DTOs from issue #2 were deliberately not created. `ApiErrorDto` would
duplicate `ProblemDetail`, which is already the single error schema across the
API. `KnowledgeArtifactDto` belongs to issue #9, where the artifact listing that
needs it lives; adding it now would be a record with no reader.

`subjectRef` and `predicateRef` exist on the question contract but are always
null: the qThought prompt returns plain question text, and inventing a subject
URI to fill the field would be exactly the kind of fabrication the pipeline is
supposed to avoid. Populating them means enriching the prompt, which belongs
with the HCIN vocabulary work in issue #7.

### Issues #6 to #9: the HCIN itself

Per-thought Turtle files record what one thought said. This is where they become
a network that can be asked questions across thoughts, and that survives a
restart.

```
hcin/
+-- HcinDataset          (TDB2, schema reloaded from the packaged files on startup)
+-- HcinTransactions     (explicit read and write, so no caller can forget one)
+-- HcinRepository       (SPARQL reads, Turtle exports, named-graph writes)
+-- HcinGraphs           (schema, asserted, inferred, hypotheses, thoughts, questions, provenance)
+-- HcinVocabulary       (the terms, as Jena resources)
+-- ShaclValidationService, ShaclReport, ShaclSeverity
+-- read models: HcinEntity, HcinMembership, HcinInteraction,
                 HcinFinancialFlow, HcinFinancialAuthority, KnowledgeTier
reconcile/
+-- CThoughtReader       (reads entities, claims and HCIN-shaped nodes out of a cThought)
+-- EntityMatcher        (normalized label plus kind; the URI is a function of the key)
+-- HcinUris             (every URI derived from what the thing is, never from when)
+-- ReconciliationService
resources/hcin/
+-- hcin-core.ttl, hcin-financial.ttl, hcin-shapes.ttl
```

Decisions worth remembering:

- **The split between graphs is epistemic, not technical.** What the vocabulary
  says, what the user confirmed, what the system inferred and what it merely
  suspects are different kinds of claim. Every record the repository returns
  carries the tier it was found in, so no consumer treats a guess as a fact
  without knowing it.
- **Every query is answered as of an instant.** Nothing later influences the
  answer, which is what will make issue #14 a parameter rather than a rewrite.
- **Reconciliation never promotes.** Authority stated without a scope is recorded
  as a hypothesis however confidently the thought asserted it. One approval is
  evidence of one approval.
- **Reconciliation owns the epistemic statements.** A thought's own
  `knowledgeStatus` is dropped on the way in: it may say it is certain, but where
  that certainty lands is not its call. Copying it across left nodes claiming to
  be asserted while sitting among the hypotheses.
- **`recordedAt` is first-write-wins.** It says when a fact entered the HCIN, so
  a later merge must not move it forward. Without that rule nothing would ever be
  idempotent, because every re-run would add a fresh timestamp.
- **SHACL severity carries meaning.** Violation, warning and clarification
  candidate are not degrees of the same thing. The third is a knowledge gap, which
  is the pipeline working, not failing.

### Issues #10 and #11: reading the network

```
projection/
+-- ProjectionProperties     (weights, decay, bounds, base currency)
+-- InteractionWeightPolicy, TemporalDecayPolicy
+-- InteractionProximityService, ProximityScore, ProximityContribution
+-- VisualDistanceNormalizer
+-- FinancialMetricsService, FinancialMagnitude, FinancialAuthorityState
+-- NodeRadiusScale
+-- RelationshipMetricsService, RelationshipMetrics, RelationshipVector
```

- **Nothing here reads the clock.** Every calculation takes its `asOf`. That is
  what makes the tests about the model rather than about when they run, and what
  will let issue #14 ask about last year with the same code.
- **A score can explain itself.** A proximity carries the interactions that
  produced it, each with its weight, its elapsed periods and its decay.
- **Gross is not net.** Both are kept: they answer different questions, and a
  projection that only knew the balance would draw a large relationship as if it
  barely existed.
- **No invented exchange rates.** Amounts outside the base currency are reported
  beside the headline figures, never converted.
- **Two vector components are null, not zero.** The network does not know whether
  the ego depends on this person, and "no dependency" would be an answer it has
  not earned.
- **None of it is ontology.** Weights and decay live in configuration precisely
  so that changing how the network is read never changes what it claims is true.

### Issues #12 to #14: the projection

```
projection/
+-- FinancialProjectionService  (nodes, edges, groups, from the measurements)
+-- GraphProjection, ProjectionNode, ProjectionEdge, ProjectionGroup
+-- NodeType, FlowStyle, FlowSummary
api/
+-- FinancialProjectionController  (projection, metrics, config)
web/
+-- FinancialProjectionViewController -> templates/financial-projection.html
```

- **The engine stops before layout.** It returns a distance from the centre and a
  radius, never an x and a y. Where a node lands is the renderer's decision, and
  keeping the line there is what lets the same projection be drawn any other way.
- **The clock is read in the controller and nowhere else.** Every layer below is
  handed an explicit instant, which is what made issue #14 nearly free.
- **Who is on the picture is itself temporal.** Someone the ego had no connection
  to yet is absent from an earlier projection: drawing them there would let a
  later fact change an earlier picture. Connection means an interaction, money,
  authority, or a membership that held at that instant.
- **An organization is the setting, not a participant.** It is drawn as an
  enclosure around its people, and becomes a node of its own only when the ego
  deals with the organization directly, as a payment to ACME does.
- **The page holds no knowledge.** Moving the time control is a new request, not
  a recalculation in the browser: what the network looked like is the server's
  answer to give. The detail panel empties on reload, because numbers computed
  for one instant say nothing about another.
- Drawing is hand-rolled inline SVG per the project rules: `createElementNS`, one
  CSS transform on a `<g>` for pan and zoom, `Map` state, no library.

**Placement is two-level.** An organization is drawn at the distance of the
person inside it the ego sees most often: that person keeps their true distance
and sits on the enclosure's inner border, and everyone else in the same
organization is measured from that border rather than from the centre, across a
band whose width follows the member count. Someone who belongs nowhere keeps
their own distance untouched.

The reason is that a raw distance is the reciprocal of proximity, so anyone seen
in the last few weeks lands within a few pixels of everyone else while the
enclosure drawn around them stretches across the whole picture. Two things were
done about it: `VisualDistanceNormalizer` now maps through a logarithm, which
spreads the population without changing the ordering, and the renderer anchors
group members on their organization's border, which keeps enclosures compact.

No enclosure may reach inside the ego's own: when the border a group would take
does not clear it, the whole group is pushed out by the least that does. That
push has one subtlety worth remembering, because getting it wrong looked exactly
like the push not happening at all. A member's position inside the band is a
fraction of where they stand among their own colleagues, and that fraction has to
be measured against the real distances. Measured against the pushed-out border
instead, the nearest member gets a negative fraction and lands back inside the
enclosure the push existed to clear.

The ego's own enclosure also writes its label underneath rather than above:
it sits at the centre with everything else around it, so a label above it runs
straight through whichever organization is drawn to the side.

The second is deliberately a layout decision and lives in the template. It makes
a drawn distance depend on which company someone works for, so the honest
per-person value stays in the API: the engine still emits no coordinates.

The alternative rule considered for the organization's own distance was the
aggregate proximity of all its members, which says something the projection
cannot otherwise say ("I am closer to ACME than to anyone in it") but pushes
large companies towards the centre and can pile groups there. The closest-member
rule cannot do that, and preserves one true distance per group.

Two things were fixed after looking at the rendered page rather than the JSON:
organization nodes were black on black and invisible, and the view used a fixed
zoom that cropped whoever was farthest away. Group members are now also placed in
one angular sector each, spaced by an on-screen gap rather than a fixed angle, so
an enclosure stays compact instead of stretching across the picture.

### Issues #15 and #16: the fixture and the end-to-end test

```
server/src/test/resources/hcin-fin/
+-- synthetic-transcripts.jsonl   (nine transcripts, explicit offsets, no audio)
+-- synthetic-answers.json        (the clarification script; a null answer is a skip)
+-- cthoughts/transcript-00N.ttl  (what a model would have consolidated)
+-- expected-projection.json      (what the whole thing must produce)
```

The answers file doubles as the question script, so a question and its answer can
never drift apart. The `cthoughts` directory is the fourth artifact issue #15 did
not name: it is the deterministic interpreter's data, and it is what lets the
whole pipeline run with no model and no API key.

`examples/hcin-fin-proj-example/` holds a second synthetic dataset, written to
reproduce the hand-drawn diagram in `examples/hcin-fin-proj-example.png`: four
organizations including the ego's own, an independent person, one inflow and two
outflows, and authority on both sides. It is what the group colouring and the
two-level layout were built against.

`FinancialProjectionEndToEndTest` drives text to questions to answers to
consolidation to reconciliation to validation to projection, over the API, and
checks the result against the fixture. Amounts, types, group membership and flow
direction are asserted exactly; distances and radii are asserted as orderings,
because their absolute values depend on parameters meant to be tuned and an
ordering that survives tuning is the real invariant.

It also covers the historical case: six months earlier, Bob still holds the
revenue authority that ended in June, Maria has not been given hers yet, and Sam
and both organizations are absent.

One thing the fixture exposed: the in-memory dataset outlives a single test
method, so each one now clears the knowledge graphs first. Without that, the
second test found the first test's lunches still on record.

### The consolidation prompt now targets HCIN

`PROMPT_CTHOUGHT` gained `HCIN_MAPPING`, which tells the model to express
interactions, financial flows, authority and memberships in `hcin:` and `hcinf:`
terms, with explicit instructions never to invent a scope, an amount, a currency
or a date, and never to turn one approval into standing authority.

This closes the gap flagged in the previous handoff: reconciliation could always
read HCIN terms, but nothing was producing them. **It has not been run against a
live model.** The fixture shows the shape that works end to end; whether the
model actually produces that shape is the first thing to check on the next run
with a real API key.

### Thought origin

`Thought` gained a `source` field: `audio`, `derived`, or an ingestion label such
as `synthetic`. Metadata written before the field existed reads back as `audio`,
decided by the compact constructor. The derived accessors `isAudio()` and
`isDerived()` are `@JsonIgnore`, so `thought.json` holds state only.

The index page no longer claims a `0:00` recording for a thought that has no
audio: it shows the source instead of duration and device.

### Structured errors

`ApiExceptionHandler` now answers with RFC 7807 problem details for a body that
fails validation (every offending field at once, under `errors`) and for one that
cannot be parsed at all, naming the field Jackson choked on.

### Fixes to existing code, found by the first tests

- `LocalNetworkConfig` cast the application context to `WebServerApplicationContext`
  unconditionally, which fails in any test that uses a mock servlet environment.
- `ThoughtStore.audioPath` built a path from a null filename for thoughts without
  audio. It now rejects them, `GET /api/thoughts/{id}/audio` answers `404`, and the
  transcribe stream sends an error event instead of failing mid-response.

### First tests in the project

`server/src/test/` did not exist before. It now holds 85 tests:

| Test | Covers |
|---|---|
| `ThoughtServiceTest` | text thoughts at the store level |
| `TextThoughtControllerTest` | the ingestion endpoint over MockMvc |
| `ClarificationServiceTest` | question identity, answer revisions, legacy files |
| `AnalysisFlowTest` | text to questions to answers to artifact, over MockMvc |
| `HcinRepositoryTest` | SPARQL reads, `asOf` filtering, export, survival across a restart |
| `ShaclValidationServiceTest` | valid and invalid fixtures, and the three severities |
| `ReconciliationServiceTest` | entity matching, idempotency, tiers, provenance, conflicts |
| `ProjectionMetricsTest` | proximity, decay, distance bounds, gross vs net, radius bounds |
| `FinancialProjectionEndToEndTest` | the whole pipeline against the synthetic fixture |

`AnalysisFlowTest` stubs `ThoughtInterpreter` with `@MockitoBean`, so the whole
pipeline runs with no network and no API key, and can assert that a repeated
call did not reach the model at all. Run with
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test`.

---

## Storage layout (per thought directory)

```
{root}/{id}/
  audio.wav          (audio captures only)
  thought.json
  transcription.json
  entity-context.txt (derived thoughts only)
  rthought.ttl       (stage 1 output)
  qthought.ttl       (stage 2 OWL output)
  questions.json     (every question ever asked, with ids and statuses)
  answers.json       (append-only log of answer revisions)
  cthought.ttl       (stage 3 output)
```

---

## Setting up on a new machine

```bash
cp server/.env.example server/.env
# edit OPENAI_API_KEY and optionally OPENAI_MODEL
```

Run with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn spring-boot:run` (the
default `java` on this machine is 17; 21 must be explicit).

---

## Notes

`HCIN-fin-proj.md` writes its formulas as ` ```math ` fences for display and
`$...$` for inline. It used to use the LaTeX delimiters `\[...\]` and
`\(...\)`, which GitHub does not recognize at all, so every formula in the
document rendered as literal backslashes. The fenced form was chosen over `$$`
on purpose: the document is about money, and the first time someone writes a
figure with a dollar sign in a paragraph, a stray `$` would start swallowing
text until the next one.

The em dash is gone from every file the project owns: 41 of them, rewritten as
colons, commas, parentheses or sentence breaks rather than deleted, so each
sentence still reads. Two remain on purpose. `CLAUDE.md` has to name the
character in order to forbid it, and `bootstrap.min.css` is a vendored build
that should stay byte-identical to upstream.

## Formulas checked against the code

Every formula in the documents was confronted with its implementation. Seven were
already faithful: the proximity sum, the reciprocal distance, gross against net
magnitude, the logarithmic radius, the five-component vector, the interaction
weights, and the numeric defaults of the projection configuration.

Five had drifted, and all five are now closed:

- The logarithmic normalization of visual distance was in the code and not in the
  document. Section 13 now states it, because it is the single decision that most
  changes the picture and someone reimplementing from the text would have got a
  different one.
- Section 20's relationship vector delta had no implementation. It now has one,
  behind `GET /api/hcin/financial-projection/delta` (issue 17).
- The qualified edge of section 18 was five parts of eight. The layer is now set
  wherever the class settles it, and a missing layer or context is raised by the
  shapes as a clarification candidate rather than guessed (issue 18). Goals are
  still unimplemented and deliberately left out: they need a way for the ego to
  state an objective, which nothing in the pipeline asks for.
- Section 8 called the authority's reach `context`. The implementation calls it
  `scope`, which is the better name, and the document now says so and explains
  why it is distinct from the generic context every statement may carry.
- The configuration example in section 30.6 listed keys that do not exist and
  omitted the two that do. It now matches, and says which choices are fixed on
  purpose rather than configurable.

The canonical relationship example in `HCIN.md` used `hcin:type` and
`hcin:startedAt`, predating the temporal model the same document argues for a few
sections later. It now uses `hcin:layer` and `hcin:validFrom`.

## Open items carried forward

**`CAPTURE_ACK` (0x04) still unimplemented on both sides.** `PROTOCOL.md` always said this one was
optional for v1; nothing about the debugging this session needed it, but it would give the stick a way
to show "phone got it" distinct from "server processed it."

**The stray 0-byte `Pending` captures from the debugging session are still in the phone's local
storage.** Harmless (they just never sync, `UploadClient` would try and fail against whatever
capturedAt/format they hold), but worth clearing before the next real test so the capture list is not
cluttered with them.

**Transcription latency is unmeasured on a GPU.** 21.7 s for 4 s of audio on
CPU no longer blocks the device, but it does mean a thought is pending for half a
minute before it can be interpreted.

**Server unauthenticated.** No auth, no TLS, CORS open. Deliberate for LAN milestone.

**Test coverage is still narrow.** `WavHeader`, `PcmFormat.durationMs`,
`ThoughtStore` path traversal and `ThoughtInterpreter` parsing remain untested.

**The app has no way to play a capture back.** Verifying this session's fixes meant pulling `audio.wav`
off the phone with `adb` each time; the capture list only shows an id and a byte count.

---

## Suggested next step

The simplified local loop (stick -> Bluetooth -> Android -> Wi-Fi -> server) is proven end to end;
run whisper.cpp alongside the server so a synced capture actually gets transcribed too, not just
stored. Beyond that, a real, non-test thought through the whole pipeline, capture to interpretation, is
the natural next milestone now that the device side actually works.

Separately, on the HCIN-FIN track: run a real thought through the whole thing with a live API key. Everything is
proven against a deterministic interpreter, which proves the pipeline and says
nothing about the model: whether `HCIN_MAPPING` actually gets the consolidation
stage to emit well-formed `hcin:` and `hcinf:` terms is unknown until it is
tried. `GET /api/hcin/validate` is the quickest way to see what came out, since
the shapes will say precisely what is missing.

After that, the obvious openings:

- **Dependency and reciprocity.** Two components of the relationship vector are
  still null. They are the ones that would let the projection answer whether a
  relationship is balanced.
- **An answer does not reach the graph.** The network now asks about its own
  gaps, but answering one writes to `answers.json` and stops there. Nothing feeds
  a layer or a context back into the HCIN, so the gap stays open and the same
  question will be asked again about a node that never fills in. This is the half
  of the loop still missing, and it is the same missing piece as the one below:
  nothing promotes anything.
- **Nothing ever becomes a fact.** Reconciliation writes to the hypotheses graph
  and never promotes, which is right as a default and total as a rule. The first
  real capture put eleven entities and ten relationships into the network and
  left the asserted graph empty, so the financial projection drew one node, the
  ego, from a network that knew four people. Some evidence has to be enough, and
  a user answering a question about a statement is the obvious candidate.
- **A membership written as a blank node is silently dropped.** The model
  consolidated `_:membership1 a hcin:Membership` with a role of CEO, correct in
  every respect except that it had no URI, and `CThoughtReader.readEvents` skips
  a subject that is not a URI resource. Nothing warns. Memberships are what draw
  organizations as enclosures, so the loss is not cosmetic. Skolemizing on the
  way in would fix it, and needs a rule for what makes two blank memberships the
  same one.
- **Entity matching beyond the name.** `EntityMatcher` matches on a normalized
  label, so two people with the same name are one person. The strategy is
  isolated behind one class for exactly this reason.
- **The device.** None of the HCIN work has touched the firmware, which still
  times out on synchronous transcription and still has the click fix unflashed.

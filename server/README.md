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

**There is none, deliberately.** No authentication, no TLS, CORS wide open, bound to `0.0.0.0`. This is a LAN-only device during the capture milestone. Do not forward this port. The one input check that still holds is path traversal on thought ids.

## API

### `POST /api/thoughts/audio`

The device sends the captured bytes as the **raw request body** (no multipart, no JSON envelope), so the firmware only has to write a fixed header and stream what the microphone produces. Chunked bodies are accepted, so it can start uploading before it knows how long the user will keep the button pressed.

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

### `POST /api/thoughts/text`

A thought that arrives already written: no device, no audio, no speech-to-text. This is how the HCIN work runs on synthetic fixtures with explicit timestamps, without hardware and without waiting on Whisper.

```json
{
  "text": "Almocei com o Bob hoje. Discutimos a renovacao do contrato da ACME.",
  "capturedAt": "2026-08-24T12:30:00-03:00",
  "source": "synthetic"
}
```

`capturedAt` is mandatory and must carry an explicit ISO-8601 offset: a fixture states when its thought happened, and the projection metrics depend on that instant being unambiguous. `source` is optional and defaults to `synthetic`.

Returns `201`, echoing the offset the caller sent:

```json
{
  "thoughtId": "20260824T153000Z-ab12cd34",
  "text": "Almocei com o Bob hoje. Discutimos a renovacao do contrato da ACME.",
  "capturedAt": "2026-08-24T12:30:00-03:00",
  "receivedAt": "2026-08-24T15:30:01Z",
  "source": "synthetic"
}
```

Invalid bodies come back as `400` with an RFC 7807 problem detail, listing every offending field at once:

```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "the request body is invalid",
  "errors": { "capturedAt": "capturedAt is required, as an ISO-8601 timestamp with offset" }
}
```

The thought it creates carries no audio and is served by the same read endpoints as a capture. Its transcription is written immediately, with `engine: "text"`, so every interpretation stage downstream treats it exactly like a capture that has already been through speech-to-text.

### Interpretation

Three stages turn a transcription into knowledge: rThought extracts what the text
states, qThought asks what it leaves unresolved, cThought consolidates the two
with the user's answers.

The web UI drives these over Server-Sent Events, because a model call takes long
enough that a browser has to be told what is happening. Everything that is not a
browser uses the synchronous endpoints below, which answer once with the whole
result.

```http
POST /api/thoughts/{id}/analyze
POST /api/thoughts/{id}/consolidate
```

Both are safe to repeat: a thought whose artifacts already exist is read back
rather than sent to the model again. Pass `{"force": true}` to re-run anyway.

`POST /analyze` returns the questions the thought is now asking:

```json
{
  "thoughtId": "20260824T153000Z-ab12cd34",
  "analysisStatus": "QUESTIONS_REQUIRED",
  "reused": false,
  "semanticArtifact": "rthought.ttl",
  "questionsArtifact": "qthought.ttl",
  "questions": [
    {
      "id": "q-7f52a1",
      "thoughtId": "20260824T153000Z-ab12cd34",
      "text": "Bob pertence a ACME?",
      "required": true,
      "status": "OPEN",
      "createdAt": "2026-08-24T15:31:00Z"
    }
  ]
}
```

Re-analyzing does not delete the questions it replaces: they were really asked
and may really have been answered, so they are kept and marked `OBSOLETE`.

`POST /consolidate` refuses while a required question is unanswered, and says
which one, rather than letting the model invent the missing half:

```json
{
  "thoughtId": "20260824T153000Z-ab12cd34",
  "status": "MISSING_REQUIRED_ANSWERS",
  "artifact": null,
  "openRequiredIds": ["q-7f52a1"]
}
```

Once answered, it returns the artifact and what it holds. The counts describe
that artifact alone: nothing is merged into a shared dataset until reconciliation.

```json
{
  "thoughtId": "20260824T153000Z-ab12cd34",
  "status": "CONSOLIDATED",
  "reused": false,
  "artifact": "cthought.ttl",
  "statements": 14,
  "entities": 3,
  "relationships": 4,
  "openRequiredIds": []
}
```

Send `{"allowIncomplete": true}` to consolidate without the missing answers, which
is what the web UI does: the user saw every question and left those blank on purpose.

### Clarification

```http
GET  /api/thoughts/{id}/questions
POST /api/thoughts/{id}/answers
GET  /api/thoughts/{id}/answers?history=true
```

Every question has an id and every answer names one, so answers may arrive a few
at a time and in any order:

```json
{
  "answers": [
    {"questionId": "q-7f52a1", "answer": "Sim, Bob trabalha na ACME."},
    {"questionId": "q-9c31bd", "skipped": true}
  ]
}
```

Either say something or say explicitly that you are skipping. An empty answer
that is not marked skipped would record silence as if it were knowledge.

A correction is a new revision rather than an overwrite: what the user said last
is the answer, and what they said before is still on record under
`?history=true`. Answering a question the thought never asked is a `400`.

### The HCIN

Consolidated thoughts are candidates in their own directory. Reconciliation is
what merges one into the shared network:

```http
POST /api/thoughts/{id}/reconcile
```

```json
{
  "thoughtId": "20260824T153000Z-ab12cd34",
  "status": "RECONCILED",
  "entitiesCreated": 3,
  "entitiesMatched": 0,
  "relationshipsCreated": 1,
  "statementsAsserted": 24,
  "statementsInferred": 0,
  "statementsHypothesized": 9
}
```

Statements are counted per epistemic tier because how much the network grew
matters less than what it now claims to know. Three rules keep the merge honest:

- **Nothing is promoted by merging.** A statement arrives with the status the
  thought gave it and lands in the graph that matches. A guess in the HCIN is
  still a guess.
- **Scope survives.** "Maria approved a payment related to the ABC contract"
  never becomes "Maria has permanent unrestricted authority at ABC". Authority
  with no stated scope is recorded as a hypothesis however confidently it was
  said, and the missing scope is left missing so the shapes can raise it as a
  question.
- **Nothing is overwritten.** Statements are only added. A hypothesis that
  contradicts a confirmed fact sits beside it, in its own graph.

Reconciling twice is safe: entity URIs are a function of what the entities are,
so the second merge reports zero.

The dataset is Apache Jena TDB2, at `on7o.hcin.location`, divided into named
graphs: `schema`, `asserted`, `inferred`, `hypotheses`, `thoughts`, `questions`,
`provenance`.

```http
GET /api/hcin/artifacts
GET /api/hcin/artifacts/hcin-core.ttl
GET  /api/hcin/data?graph=asserted
GET  /api/hcin/validate?graph=hypotheses
POST /api/hcin/clarifications
```

The data endpoints serialize out of the dataset every time rather than serving a
stored copy: a second file claiming to be the truth would eventually disagree
with the first. Only the packaged artifacts and the known graph names can be
asked for.

`GET /api/hcin/validate` runs the SHACL shapes. Its findings come back at three
levels, and the third is the interesting one:

| Level | Meaning |
|---|---|
| `FATAL` | the data breaks the model and must not be trusted |
| `WARNING` | the data is usable but thin |
| `CLARIFICATION_CANDIDATE` | something is missing that a person could be asked about |

An authority with no scope is not an error. It is a question waiting to be asked.

`POST /api/hcin/clarifications` is what asks it. It validates the knowledge
graphs and turns every clarification candidate into a real question, which is the
one place in on7o where a question starts from the network rather than from
something the user said. Each carries the node and the property it is about, so
`subjectRef` and `predicateRef` are populated where a question from a thought
leaves them null.

A gap is attached to the thought that first observed the node, found through the
provenance graph, so the question joins that thought's questions and is answered
in the same place as the rest. The id is derived from the node and the property
and from nothing else, so calling this repeatedly never duplicates a question:
one already asked keeps whatever state the user left it in, and only its wording
is refreshed while it is still open.

Two things are deliberately not asked. A gap on a node no thought is recorded as
having observed is reported under `unattributed` rather than guessed at, since a
question has to be asked of someone. And layer and context are asked only about
nodes joining two people or organizations: reconciliation mints a relationship
for every statement a thought makes, and which relational layer connects a venue
to the city it stands in is a question with no answer.

**Answers do not yet reach the graph.** They are recorded like any other answer,
and nothing feeds them back into the HCIN, so the gap that produced the question
stays open. Closing that loop is the next piece of work.

### Projection metrics

The financial projection reads the HCIN at an instant and produces distances,
sizes and scores, never coordinates: where a node lands is layout, and layout is
not knowledge.

Proximity is the sum over interactions of each one's weight times its decay:

```text
P(t) = sum over interactions of  weight * decayFactor ^ (elapsed 24h periods)
```

Someone seen weekly stays close, someone seen once a quarter drifts away, a
recent meeting pulls them back in. Visual distance is `1 / (epsilon + P)`,
normalized across the population into the configured band, so distances are
comparable inside one projection but not between two.

Money is kept as `BigDecimal` throughout, and gross is kept apart from net: a
relationship carrying a million in and almost a million out is a large financial
relationship with a small balance. Nothing is converted between currencies.
Amounts outside the base currency are reported beside the headline figures rather
than converted at a rate this system does not have.

Node radius is `minRadius + alpha * log(1 + gross)`, bounded at both ends, so one
large relationship cannot swallow the picture.

Every calculation takes the instant it is being made for. Nothing in the metric
code reads the clock, which is what lets the same code answer for last year.

All of it is configuration under `on7o.projection`, and none of it is ontology:
what a meeting is worth is a choice about how to read the network, never a claim
about the world.

### The projection

```http
GET /api/hcin/financial-projection?asOf=2026-08-24T18:00:00Z
GET /api/hcin/financial-projection/metrics?asOf=2026-08-24T18:00:00Z
GET /api/hcin/financial-projection/delta?from=2026-03-01T00:00:00Z&to=2026-08-24T18:00:00Z
GET /api/hcin/projection-config
```

The projection says how far apart things are and how big they should be. It does
not say where they go: coordinates are layout, and layout is not knowledge.

```json
{
  "asOf": "2026-08-24T18:00:00Z",
  "ego": "urn:hcin:person:me",
  "nodes": [
    {
      "id": "urn:hcin:person:bob",
      "label": "Bob",
      "type": "PERSON",
      "organizationIds": ["urn:hcin:org:acme-company"],
      "interactionProximity": 1.7071,
      "visualDistance": 80.0,
      "financialMagnitude": 0,
      "radius": 8.0,
      "financialAuthority": "NONE",
      "interactions": 3
    }
  ],
  "edges": [
    { "source": "urn:hcin:person:me", "target": "urn:hcin:person:bob",
      "directFinancialFlow": false, "flowDirection": "NONE", "strokeStyle": "DASHED" }
  ],
  "groups": [
    { "id": "urn:hcin:org:acme-company", "label": "ACME Company",
      "memberNodeIds": ["urn:hcin:person:bob", "urn:hcin:person:carol"] }
  ]
}
```

`asOf` is read from the clock in the controller and nowhere else. Everything
below it is given an explicit instant, which is what lets the same code answer
for last year.

A snapshot says how things stand. Only the difference between two of them says
whether a relationship is warming or cooling, whether money has shifted
direction, or whether authority has moved, so `delta` subtracts one relationship
vector from the other, component by component. An entity known at one instant and
not the other is compared against an empty vector: the ego had no relationship
with them then, which is a real starting point rather than missing data. A
component nobody computes stays null on both sides, since never having looked is
not the same as having looked and found nothing.

The metrics endpoint returns the working behind those numbers: every interaction
a proximity was computed from, with its weight, its elapsed periods and its
decay. Saved beside `projection-config`, a projection is reproducible, which a
screenshot never is.

### The picture

```text
GET /hcin/financial
```

An ego-centric graph, drawn as hand-rolled inline SVG with no diagramming
library: `document.createElementNS`, a `<g>` moved by one CSS transform for pan
and zoom, and plain `Map` state for nodes and edges.

| What you see | What it means |
|---|---|
| distance from the centre | how recently and often you interact |
| node size | gross money moved, not the balance |
| solid edge | money passes directly between you |
| dashed edge | a relationship with no direct financial flow |
| green edge | money coming in |
| red edge | money going out |
| red or green node | authority over spending or over revenue |
| enclosure | an organization, around the people inside it |
| a node in no enclosure | someone who belongs nowhere |

Colours and dashes are projection conventions, not ontology. What is a fact is
that money passed or did not; that dashes are how we say so is a choice, and it
lives in the renderer.

Placement happens in two levels. An organization is drawn at the distance of the
person inside it you see most often, so that person keeps their true distance
exactly and sits on the enclosure's inner border; everyone else in the same
organization is measured from that border instead of from the centre. Someone who
belongs nowhere keeps their own distance untouched.

That trade exists because a raw distance is the reciprocal of proximity: people
you see regularly all land within a few pixels of each other, while the enclosure
drawn around them stretches across the picture. Anchoring on the border buys a
compact enclosure for a little fidelity, and it is a layout decision only. The
honest per-person distance is still what `visualDistance` returns.

Clicking a node explains it: every number, and the meetings that produced the
distance. The time control asks the server what the network looked like at
another instant, so moving it changes memberships, authority, flows and distances
together. A person the ego had no connection to yet is simply absent from an
earlier picture.

## Speech-to-text

Transcription runs through a local [whisper.cpp](https://github.com/ggml-org/whisper.cpp) server. Captured thoughts are personal by nature, so the audio never leaves the machine.

```bash
~/whisper.cpp/build/bin/whisper-server \
    -m ~/whisper.cpp/models/ggml-large-v3-turbo-q5_0.bin \
    -l pt --port 8090
```

`large-v3-turbo` is the quality/speed compromise for Brazilian Portuguese on CPU: it gets proper nouns and technical jargon that `small` mangles, while its reduced decoder keeps it several times faster than plain `large-v3`.

Configured under `on7o.stt`:

```yaml
on7o:
  stt:
    enabled: true          # false stores captures without transcribing
    url: http://127.0.0.1:8090
    language: pt           # keeps Whisper from guessing Spanish on short clips
    timeout: 10m
```

**Transcription happens after the response, not during it.** `POST /api/thoughts/audio` answers `201` as soon as the audio is on disk, in a few dozen milliseconds, and `TranscriptionWorker` runs Whisper on a virtual thread. Transcribing inside the request held it open for several times the length of the audio, far past the 8 s the device waits before it reports a capture as lost, so every real capture looked like a failure on the device screen while the server had stored it perfectly.

The transcription is therefore null in the ingest response, and arrives under `GET /api/thoughts/{id}/transcription` when the engine finishes. Until then the thought is simply pending, a state the index page already shows. A background transcription is best effort: it is not retried, and one still running when the server stops is lost, which costs a button press rather than a thought, since the audio is on disk and the transcribe endpoint runs it again.

If the engine is unreachable or fails, the capture is still stored and `201` is still returned, with a null transcription. A broken speech-to-text engine must never cost the user a thought.

The result is written to `transcription.json`, beside the audio and separate from `thought.json`:

```json
{
  "thoughtId": "20260813T210304Z-e503071b",
  "text": "Alô, 123. Alô, 123.",
  "language": "pt",
  "engine": "whisper.cpp",
  "transcribedAt": "2026-08-13T21:04:22Z",
  "latencyMs": 18432
}
```

Keeping it in its own file is deliberate: the audio is the record, and a transcription is one reading of it. A better model later produces a new transcription without rewriting what was captured.

### Reading back

| | |
|---|---|
| `GET /api/thoughts?limit=50` | Most recent captures first |
| `GET /api/thoughts/{id}` | One thought record |
| `GET /api/thoughts/{id}/transcription` | What speech-to-text understood |
| `GET /api/thoughts/{id}/audio` | The WAV, playable in a browser; `404` for a thought without audio |
| `GET /api/thoughts/{id}/questions` | What the thought is still asking |
| `GET /api/thoughts/{id}/answers` | What it has been told |
| `GET /api/health` | `{"status":"ok"}` |

## Storage

The filesystem is the whole storage layer for now: one directory per thought, ids timestamp-prefixed so lexical order is chronological:

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

The shortest path is a text thought, which skips the microphone and Whisper entirely:

```bash
curl -X POST http://localhost:8080/api/thoughts/text \
     -H 'Content-Type: application/json' \
     -d '{"text":"Almocei com o Bob hoje.","capturedAt":"2026-08-24T12:30:00-03:00"}'
```

To exercise the audio path instead:

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

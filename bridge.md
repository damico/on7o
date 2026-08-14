# bridge

Handoff from the session of **2026-08-13**, updated after the web UI and LLM pipeline work.

Branch: **`main`**

---

## Where the project stands

| # | Milestone | State |
|---|---|---|
| 1 | Push-to-talk capture on the M5 device | **Done**, running on hardware |
| 2 | Audio to a Spring Boot server over Wi-Fi | **Done**, verified byte-exact |
| 3 | Speech-to-text | **Done**, local Whisper, synchronous |
| 4 | Persist the original transcription | **Done**, `transcription.json` |
| 5 | LLM interpretation of thoughts | **In progress** (rThought + qThought + cThought pipeline wired) |
| 6-10 | Entities, questions, UI, ontology, Bluetooth | Not started |

The loop `capture -> transmit -> store -> transcribe` is proven end to end on real hardware.

---

## What was added this session

### Project conventions (CLAUDE.md)

Added `CLAUDE.md` at the repo root with the agreed rules: Java Spring Boot backend, Thymeleaf + Bootstrap frontend (no CDNs), KISS + GoF patterns, no Hibernate/JPA, Javadoc in English on every class, no em dash in any text, commit messages must not reference tooling by brand name, bridge.md must be updated on every commit.

### Web UI (server/)

Thymeleaf + Bootstrap 5 added as a dependency. Bootstrap CSS and JS are bundled locally under `src/main/resources/static/`. The design follows the Resend aesthetic: black background, near-black cards, white text, monospace accents.

New packages:

```
org.on7o.server
+-- Constants            (interface with all prompts and config keys)
+-- web/
|   +-- IndexController  (GET / - thought list)
|   +-- QuestionsController (GET /thoughts/{id}/questions)
|   +-- ThoughtView      (view model: Thought + Transcription + stage flags)
+-- llm/
|   +-- OpenAiProperties
|   +-- OpenAiClient     (RestClient wrapper, no SDK)
|   +-- ThoughtInterpreter (3-stage pipeline)
|   +-- QThoughtResult
|   +-- InterpretationException
+-- api/
    +-- TranscribeController  (GET SSE /api/thoughts/{id}/transcribe/stream)
    +-- UnderstandController  (GET SSE understand + consolidate, POST answers)
```

Templates: `index.html`, `questions.html`.

### Three-stage interpretation pipeline

Each stage runs in a virtual thread and streams progress via Server-Sent Events.

**Stage 1 - rThought** (`GET /api/thoughts/{id}/understand/stream`):
Sends the transcription to OpenAI with `PROMPT_RTHOUGHT`. Returns OWL 2 Turtle saved as `rthought.ttl`. Extracts subjects, predicates, adjectives strictly from the text with no inference.

**Stage 2 - qThought** (same SSE stream, sequential):
Sends rThought to OpenAI with `PROMPT_QTHOUGHT`. Returns a JSON block of plain-language questions + a Turtle block of `on7o:ClarificationQuestion` instances saved as `qthought.ttl` and `questions.json`. When done, the SSE client is redirected to the questions page.

**Stage 3 - cThought** (`GET /api/thoughts/{id}/consolidate/stream`):
Requires answers to have been saved first via `POST /api/thoughts/{id}/answers`. Calls OpenAI with `PROMPT_CTHOUGHT` and saves `cthought.ttl`.

### Storage layout (per thought directory)

```
{root}/{id}/
  audio.wav
  thought.json
  transcription.json
  rthought.ttl      (stage 1 output)
  qthought.ttl      (stage 2 OWL output)
  questions.json    (stage 2 plain questions for the UI)
  answers.json      (user answers, prerequisite for stage 3)
  cthought.ttl      (stage 3 output)
```

### Configuration

OpenAI key and model are read from a `.env` file (gitignored). Copy `.env.example` and fill in `OPENAI_API_KEY`. Default model is `o3`, overridable via `OPENAI_MODEL`.

---

## Setting up on a new machine

Same as before plus:

```bash
cp server/.env.example server/.env
# edit OPENAI_API_KEY and optionally OPENAI_MODEL
```

Run with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn spring-boot:run` (the default `java` on this machine is 17; 21 must be explicit).

---

## Open items carried forward

**Synchronous transcription exceeds firmware timeout.** Same as before: `kResponseTimeoutMs = 8000` in firmware vs ~20 s on CPU. Measure on a GPU machine before changing anything.

**Click at start of each capture.** Fix committed in firmware but not flashed.

**`ON7O_HOST` hardcoded.** mDNS would remove the reflash-on-IP-change cycle.

**No tests.** `WavHeader`, `PcmFormat.durationMs`, `ThoughtStore` path-traversal, and now `ThoughtInterpreter` parsing are the obvious first targets.

**Server unauthenticated.** No auth, no TLS, CORS open. Deliberate for LAN milestone.

**cThought display is raw Turtle.** The questions page shows the OWL output in a pre block. A friendlier rendering (graph or structured list) would improve usability.

---

## Suggested next step

Test the pipeline end to end with a semantically rich phrase (see README examples: "preciso marcar o dentista da Ana"). The rThought -> qThought -> user answers -> cThought loop is wired but untested against the live API.

After that: milestone 6 (entity persistence and cross-thought linking).

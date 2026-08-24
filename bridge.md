# bridge

Handoff from the session of **2026-08-24**, after finishing all sixteen issues of the HCIN-FIN track.

Branch: **`main`**

---

## Where the project stands

| # | Milestone | State |
|---|---|---|
| 1 | Push-to-talk capture on the M5 device | **Done**, running on hardware |
| 2 | Audio to a Spring Boot server over Wi-Fi | **Done**, verified byte-exact |
| 3 | Speech-to-text | **Done**, local Whisper, synchronous |
| 4 | Persist the original transcription | **Done**, `transcription.json` |
| 5 | LLM interpretation of thoughts | **Done**, rThought + qThought + cThought, plus entity derivation |
| 6 | Ontology diagrams per stage | **Done**, hand-rolled inline SVG |
| 7 | HCIN financial projection | **Done**, all 16 issues |

The loop `capture -> transmit -> store -> transcribe -> interpret` is proven end to end on real hardware.

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

## What was added this session

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

## Open items carried forward

**Synchronous transcription exceeds firmware timeout.** `kResponseTimeoutMs = 8000`
in firmware vs ~20 s on CPU. Measure on a GPU machine before changing anything.

**Click at start of each capture.** Fix committed in firmware but not flashed.

**`ON7O_HOST` hardcoded.** mDNS would remove the reflash-on-IP-change cycle.

**Server unauthenticated.** No auth, no TLS, CORS open. Deliberate for LAN milestone.

**Test coverage is still narrow.** `WavHeader`, `PcmFormat.durationMs`,
`ThoughtStore` path traversal and `ThoughtInterpreter` parsing remain untested.

---

## Suggested next step

Run a real thought through the whole thing with a live API key. Everything is
proven against a deterministic interpreter, which proves the pipeline and says
nothing about the model: whether `HCIN_MAPPING` actually gets the consolidation
stage to emit well-formed `hcin:` and `hcinf:` terms is unknown until it is
tried. `GET /api/hcin/validate` is the quickest way to see what came out, since
the shapes will say precisely what is missing.

After that, the obvious openings:

- **Dependency and reciprocity.** Two components of the relationship vector are
  still null. They are the ones that would let the projection answer whether a
  relationship is balanced.
- **Clarification from the shapes.** SHACL already produces clarification
  candidates. Turning those into questions would close the loop: the network
  would ask about its own gaps rather than waiting for a thought to raise them.
- **Entity matching beyond the name.** `EntityMatcher` matches on a normalized
  label, so two people with the same name are one person. The strategy is
  isolated behind one class for exactly this reason.
- **The device.** None of the HCIN work has touched the firmware, which still
  times out on synchronous transcription and still has the click fix unflashed.

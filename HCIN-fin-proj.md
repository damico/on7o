# HCIN Financial Projection

## 1. Purpose

The **HCIN Financial Projection** is an ego-centric visualization of a Human-Centric Intelligence Network (HCIN) focused on the financial dimension of human relationships.

The projection is centered on a single person — the **ego** — and represents:

- the people with whom the ego has relationships;
- the organizations, companies, institutions, or informal groups that contextualize those relationships;
- direct or indirect financial relationships;
- direction of financial flow;
- financial decision power;
- temporal proximity derived from interaction history;
- financial intensity derived from the magnitude of revenue and expenditure;
- people who are independent and are not associated with any organization.

The visualization is not the HCIN itself. It is a **computed view** of the HCIN under a specific perspective, context, goal, and time.

A useful abstraction is:

```math
Visualization = \Phi(HCIN, t, context, goal)
```

For this projection:

```text
perspective = ego
context     = financial
goal        = understand the ego's financial relationship network
time        = selected instant or interval
```

The same underlying HCIN can later generate other projections — professional, emotional, governance, intellectual, family, or any other relational layer — without changing the underlying semantic representation.

---

## 2. Conceptual Architecture

```text
Synthetic Transcriptions
        |
        v
      on7o
Knowledge Acquisition
        |
        v
LLM Interpretation
        |
        +------------------------+
        |                        |
        v                        v
Candidate Semantic Facts     Questions
        |                        |
        +-----------+------------+
                    |
                    v
              Clarified Facts
                    |
                    v
                  HCIN
                    |
      +-------------+-------------+
      |             |             |
      v             v             v
 Semantic       Relational     Epistemic
  Layer           Layer          Layer
      |             |             |
      +-------------+-------------+
                    |
                    v
          Financial Projection
```

The role of **on7o** is knowledge acquisition.

The role of **HCIN** is to represent human-centered relational intelligence.

The role of the **Financial Projection** is to transform a selected subset of HCIN properties into visual variables such as position, distance, color, line type, and node size.

---

## 3. The Ego-Centric Model

The projection has one central node:

```text
Me
```

`Me` is the ego and represents the perspective from which the network is observed.

All displayed relationships are interpreted relative to this ego.

This does **not** mean that the HCIN only contains ego-to-person relationships. The underlying HCIN can represent arbitrary person-to-person and person-to-organization relations.

The Financial Projection may choose to display only the subgraph relevant to the ego.

---

## 4. People

People are represented as circular nodes.

A person may:

- belong to one organization;
- belong to several organizations;
- have historical memberships;
- act in different roles depending on the context;
- have no organizational membership.

Example:

```text
John Doe
    |
    +-- Person
    +-- relatedTo Me
    +-- no required Organization membership
```

Organizational membership is therefore optional.

---

## 5. Organizations and Groups

Organizations are represented visually as enclosing regions or clusters.

Examples include:

- companies;
- institutions;
- associations;
- informal groups;
- partnerships;
- departments;
- boards;
- projects, if the projection chooses to use project membership as a grouping dimension.

A person inside an organizational region is interpreted as having some relevant membership, role, affiliation, or association with that organization.

The visual grouping is derived from semantic HCIN information and should **not** be treated as the canonical storage format.

---

## 6. Financial Relationships

A relationship between the ego and another person may have a direct financial component or no direct financial component.

### 6.1 Dashed edge

A dashed edge means:

> There is a relationship, but no direct revenue or expenditure is currently attributed to it in this projection.

Example:

```text
Me - - - - Bob
```

This may still be a highly relevant professional or social relationship.

The absence of a direct financial flow does not mean that the relationship has no financial importance.

---

### 6.2 Continuous edge

A continuous edge indicates a direct financial relationship.

```text
Me -------- Maria
```

The direction and economic interpretation are encoded separately.

---

## 7. Direction of Financial Flow

A direct financial edge has a direction relative to the ego.

Conceptually:

```text
outflow  -> money leaving the ego's financial sphere
inflow   -> money entering the ego's financial sphere
```

The current visualization convention is:

```text
red edge   = financial outflow
green edge = financial inflow
```

The semantic model should store the direction and amounts explicitly rather than storing only a visual color.

For example:

```text
FinancialFlow
    source
    target
    direction
    amount
    currency
    occurredAt
    context
```

Color is a projection-level encoding derived from those facts.

---

## 8. Financial Decision Power

Financial flow and financial decision power are distinct concepts.

A person can have financial decision authority without personally receiving or paying the money.

For example:

```text
Me
 |
 | payment
 v
Organization
 |
 +--> Maria has approval authority
```

Therefore the HCIN should distinguish:

```text
FinancialFlow
```

from:

```text
FinancialAuthority
```

A possible semantic representation is:

```text
FinancialAuthority
    holder
    organization
    authorityType
    validFrom
    validTo
    spendingLimit
    currency
    context
```

In the current visualization:

```text
black person node = no known relevant financial decision authority

red person node   = relevant authority over expenditure / outgoing money

green person node = relevant authority over revenue / incoming money
```

More advanced versions may represent mixed authority, scoped authority, approval thresholds, or multidirectional authority without forcing a single categorical color.

---

## 9. Temporal Interaction Proximity

The visual distance between `Me` and another person represents **interactional proximity**, not graph-theoretical shortest-path distance.

The intended rule is:

> The more recent and recurrent the interactions, the closer the person appears to the ego.

The closest possible state corresponds to interaction within the most recent 24-hour interval.

As time passes without interaction, proximity decays.

Repeated interaction counteracts the decay.

Thus:

- a person interacted with every week tends to remain closer;
- a person interacted with only once every three months tends to remain farther away;
- a recent interaction can temporarily move a person significantly closer;
- long inactivity gradually moves a person away.

---

## 10. Interaction Events

Every interaction should be represented as a temporal event.

Example:

```text
Interaction
    participants: Me, Bob
    occurredAt: 2026-08-24T12:30:00-03:00
    interactionType: in-person-meeting
    context: ACME Company
    source: transcript-2026-08-24-001
```

The projection does not require audio to exist.

For this project, synthetic, temporally aligned transcriptions are sufficient as source evidence.

Example:

```text
2026-08-24T12:30:00-03:00
"Almocei com o Bob hoje. Discutimos a renovação do contrato da ACME."
```

Another example:

```text
2026-08-17T15:10:00-03:00
"Conversei com a Carol sobre os termos comerciais do contrato."
```

And another:

```text
2026-05-12T09:40:00-03:00
"Falei rapidamente com o Sam sobre uma possível parceria."
```

The timestamps are critical because the geometry of the network depends on time.

---

## 11. Proximity Function

Let:

```math
I_{ij}
```

be the set of interactions between people $i$ and $j$.

A useful initial proximity function is:

```math
P_{ij}(t)
=
\sum_{k \in I_{ij}}
q_k \cdot \delta^{d_k}
```

where:

- $P_{ij}(t)$ is the interactional proximity at time $t$;
- $q_k$ is the weight of interaction $k$;
- $d_k$ is the number of elapsed 24-hour periods since interaction $k$;
- $\delta$, with $0 < \delta < 1$, is the daily decay factor.

For example, if:

```math
\delta = 0.9
```

then an interaction contributes approximately:

```text
today       1.0000
1 day       0.9000
2 days      0.8100
3 days      0.7290
7 days      0.4783
30 days     0.0424
```

The exact decay constant must be calibrated empirically.

A slower decay may better represent durable professional relationships.

A faster decay may better emphasize current activity.

---

## 12. Different Interaction Weights

Not all interactions need to contribute equally.

The $q_k$ factor can encode interaction importance.

Example initial values:

```text
mentioned in a thought        0.10
asynchronous message          0.30
phone call                    0.60
meeting                       1.00
strategic meeting             1.50
financial negotiation         1.50
financial transaction         1.20
```

These values are analytic parameters, not ontological truths.

They should be stored in projection configuration rather than hard-coded into the ontology.

---

## 13. From Proximity to Visual Distance

The visual distance can be derived inversely from proximity.

A simple function is:

```math
D_{ij}(t)
=
\frac{1}{\epsilon + P_{ij}(t)}
```

where $\epsilon$ prevents division by zero.

Conceptually:

```text
many recent interactions
        |
        v
high proximity
        |
        v
low visual distance
        |
        v
person appears close to Me
```

and:

```text
few old interactions
        |
        v
low proximity
        |
        v
high visual distance
        |
        v
person appears far from Me
```

In the rendering layer, the value should be normalized to:

```math
D_{min} \le D_{visual} \le D_{max}
```

to prevent extreme layouts.

---

## 14. Financial Intensity

The size of a person's node represents financial intensity.

The important distinction is that intensity should normally be based on **gross financial magnitude**, not only net balance.

Suppose a relationship contains:

```text
revenue     1,000,000
expense       950,000
```

The net balance is:

```text
50,000
```

but the relationship has a very large financial volume.

Therefore an initial financial magnitude measure should be:

```math
F_{ij}
=
\sum_k |amount_k|
```

rather than:

```math
\left|
\sum_k amount_k
\right|
```

The first measures the volume of financial exposure.

The second measures only net balance.

Both values may be useful, but they answer different questions.

---

## 15. Mapping Financial Intensity to Node Size

Financial data is usually highly skewed.

A linear mapping can make a few large relationships visually dominate the entire diagram.

A logarithmic mapping is more appropriate:

```math
R_i
=
R_{min}
+
\alpha \log(1 + F_i)
```

where:

- $R_i$ is the displayed node radius;
- $F_i$ is the gross financial magnitude associated with person $i$;
- $R_{min}$ is the minimum node radius;
- $\alpha$ is a visual scaling constant.

The projection configuration should define minimum and maximum radii.

---

## 16. Financial Flow vs. Financial Intensity

These dimensions must remain separate.

```text
edge direction/color
    =
direction of financial flow

node size
    =
financial magnitude / importance
```

Thus a very large node can exist with:

- predominantly incoming money;
- predominantly outgoing money;
- large amounts in both directions;
- substantial financial influence without direct flow, if a future projection chooses to encode influence separately.

---

## 17. HCIN Core Model

The Financial Projection can be described as one projection of the HCIN Core Model.

A useful initial formalization is:

```math
HCIN = (V,E,L,T,C,P,G)
```

where:

```text
V = vertices / entities
E = qualified relationships
L = relational layers
T = temporal information
C = contexts
P = perspectives and provenance
G = goals and interests
```

For the Financial Projection:

```text
V
    People
    Organizations

E
    human relationships
    memberships
    financial flows
    financial authority relations

L
    financial
    professional
    organizational

T
    interaction history
    relationship validity
    financial events
    observation time

C
    organizations
    contracts
    negotiations
    projects
    financial contexts

P
    Me as ego perspective
    evidence provenance
    confidence
    epistemic status

G
    financial understanding
    commercial objectives
    risk, revenue, cost, opportunity
```

---

## 18. Evolution of the Edge

A conventional graph starts with:

```math
e = (A,B)
```

meaning only that two nodes are connected.

A semantic graph may evolve this to:

```math
e = (A,r,B)
```

where $r$ gives the relationship a meaning.

HCIN requires a richer qualified relationship:

```math
e =
(A,B,l,t,c,p,g,\mathbf{w})
```

where:

- $A$ = source entity;
- $B$ = target entity;
- $l$ = relational layer;
- $t$ = temporal state;
- $c$ = context;
- $p$ = perspective and epistemic metadata;
- $g$ = goal or interest under which the relation is being evaluated;
- $\mathbf{w}$ = multidimensional relationship vector.

The relationship is therefore not a static line.

It is a contextual, temporal, perspectival object.

---

## 19. Relationship Vector

For the Financial Projection, an initial relationship vector may be:

```math
\mathbf{w}_{ij}(t)
=
[
P_{interaction},
F_{magnitude},
A_{authority},
D_{dependency},
R_{reciprocity}
]
```

where:

```text
Pinteraction
    interactional proximity

Fmagnitude
    financial magnitude

Aauthority
    financial decision authority

Ddependency
    financial or operational dependency

Rreciprocity
    reciprocity of economic or relational exchange
```

The projection does not need to display every vector component.

Instead, it maps selected components to visual properties.

Example:

```text
Pinteraction
    -> visual distance

Fmagnitude
    -> node size

financial flow direction
    -> edge color

presence of direct flow
    -> solid or dashed edge

Aauthority
    -> node color / visual state
```

This means the diagram is a rendering function over the HCIN state rather than a hand-authored graph.

---

## 20. Evolution of the Relationship Vector

The vector changes over time:

```math
\mathbf{w}_{ij}(t)
```

A useful representation of change between two moments is:

```math
\Delta \mathbf{w}_{ij}
=
\mathbf{w}_{ij}(t_2)
-
\mathbf{w}_{ij}(t_1)
```

This allows the system to detect changes such as:

```text
interaction proximity increasing
financial magnitude increasing
decision authority decreasing
dependency increasing
reciprocity decreasing
```

The HCIN can therefore represent not only the current relationship but its trajectory.

---

## 21. Temporal HCIN State

The network should be understood as a temporal system:

```math
HCIN(t_0),
HCIN(t_1),
HCIN(t_2),
...
```

A relation can change without disappearing.

A person can:

```text
become closer
become more distant
gain authority
lose authority
begin generating revenue
stop generating revenue
change organizations
change roles
```

Therefore the system should preserve history rather than continuously overwrite the present state.

---

## 22. Valid Time and Observation Time

Two temporal concepts are especially important.

### Valid Time

When was the fact true in the represented world?

Example:

```text
Bob had approval authority from January to June.
```

### Observation Time

When did HCIN learn or observe the fact?

Example:

```text
The system learned in August that Bob had held that authority until June.
```

These are different.

An HCIN implementation should therefore be capable of distinguishing fields such as:

```text
validFrom
validTo
observedAt
recordedAt
lastConfirmedAt
```

This distinction is important for historical reconstruction and epistemic correctness.

---

## 23. The Three HCIN Layers

The Financial Projection makes the three principal HCIN layers concrete.

### 23.1 Semantic Layer

The Semantic Layer answers:

```text
Who is Bob?
What is ACME Company?
Is Bob associated with ACME?
What is financial authority?
What is an interaction?
What is a payment?
What is a professional relationship?
```

Typical technologies:

```text
RDF
RDFS
OWL
SKOS
SHACL
```

This layer represents meaning.

---

### 23.2 Relational Layer

The Relational Layer answers:

```text
How close is Bob to Me?
How often do we interact?
How much money is involved?
In which direction does money flow?
How central is this person?
How dependent is the relationship?
How has the relationship changed?
```

Typical mechanisms include:

```text
temporal scoring
network analysis
multilayer graph analysis
financial aggregation
distance functions
vector calculations
```

This layer represents structure and dynamics.

---

### 23.3 Epistemic Layer

The Epistemic Layer answers:

```text
Why does HCIN believe Bob has financial authority?
Was it explicitly stated?
Was it inferred?
Which transcript supports the fact?
When was the evidence observed?
What confidence does the system assign to the assertion?
Has the ego confirmed it?
```

Typical concepts include:

```text
provenance
evidence
perspective
confidence
epistemic status
observation time
human confirmation
```

This layer represents how the system knows what it claims to know.

---

## 24. Synthetic Transcriptions as Input

Audio is not required for this projection experiment.

The input corpus can consist entirely of synthetic transcriptions aligned to timestamps.

Example:

```text
2026-08-24T12:30:00-03:00
"Almocei com o Bob hoje. Discutimos a renovação do contrato da ACME."
```

```text
2026-08-21T10:20:00-03:00
"A Carol me ligou para conversar sobre os próximos passos comerciais com a ACME."
```

```text
2026-08-18T16:45:00-03:00
"A Maria aprovou um pagamento de 300 mil relacionado ao contrato da ABC."
```

```text
2026-08-15T09:15:00-03:00
"Conversei com o John Doe sobre uma proposta. Ele trabalha de forma independente."
```

```text
2026-08-01T14:30:00-03:00
"Falei com o Rob sobre uma possível revisão das condições comerciais com a XYZ."
```

```text
2026-05-12T09:40:00-03:00
"Falei rapidamente com o Sam sobre uma possível parceria."
```

The synthetic corpus should deliberately include:

- frequent relationships;
- old relationships;
- recent interactions;
- people with organizations;
- independent people;
- incoming financial flows;
- outgoing financial flows;
- relationships with no direct financial flow;
- people with and without financial authority;
- changes of authority over time;
- repeated interactions with different frequencies.

This makes the projection testable.

---

## 25. Example on7o Interpretation

Input:

```text
2026-08-24T12:30:00-03:00
"Almocei com o Bob hoje. Discutimos a renovação do contrato da ACME."
```

Possible structured interpretation:

```text
Interaction:
    participants = [Me, Bob]
    occurredAt = 2026-08-24T12:30:00-03:00
    type = in-person-meeting
    context = ACME Company

Topic:
    contract renewal

Evidence:
    transcript = transcript-2026-08-24-001

Epistemic status:
    asserted / extracted from user transcription
```

Consequences:

```text
interaction history changes
        |
        v
P(Me,Bob,t) increases
        |
        v
D(Me,Bob,t) decreases
        |
        v
Bob moves visually closer to Me
```

---

## 26. Example Financial Authority Interpretation

Input:

```text
2026-08-18T16:45:00-03:00
"A Maria aprovou um pagamento de 300 mil relacionado ao contrato da ABC."
```

Possible candidate knowledge:

```text
Maria
    may have financial approval authority
    context = ABC Company

FinancialEvent
    amount = 300000
    context = ABC contract
```

The HCIN should not necessarily generalize from one event to permanent authority.

The system may generate a clarification question:

```text
"Maria aprova todos os pagamentos da ABC, ou apenas pagamentos relacionados a esse contrato?"
```

The answer can then modify the semantic and epistemic state of the network.

---

## 27. Projection Configuration

Visual semantics should be configurable and kept outside the ontology.

Example:

```text
distance.metric              = temporalInteractionProximity
distance.decayPeriod         = 24h
distance.decayFactor         = 0.90

node.size.metric             = grossFinancialMagnitude
node.size.scale              = logarithmic

edge.direct.none             = dashed
edge.direct.inflow           = solid-green
edge.direct.outflow          = solid-red

authority.none               = black
authority.incoming           = green
authority.outgoing           = red
```

This allows the ontology and HCIN data to remain stable while the visualization evolves.

---

## 28. Time Navigation

A future interface should allow the user to select time.

Example:

```text
2024 ---------------- 2025 ---------------- 2026
                                             ^
                                            now
```

Moving the selected time should recompute:

```text
HCIN(t)
```

and therefore recompute:

```text
node positions
node sizes
financial flows
financial authority
organizational memberships
relationship vectors
```

Questions that become possible include:

```text
How did my financial network change over the last two years?

Who became closer to me?

Which relationships lost interactional proximity?

Which organizations became more financially important?

Who gained or lost financial authority?

Where did financial dependency increase?
```

---

## 29. Projection Principle

The HCIN should **not** store visualization coordinates, node radius, CSS colors, or line styles as semantic truth.

Instead:

```text
HCIN facts
    |
    v
metrics
    |
    v
projection rules
    |
    v
rendered diagram
```

Therefore:

```text
position
size
color
stroke style
cluster layout
```

are derived properties.

This keeps the semantic model independent from any particular visualization library or UI technology.

---

# 30. Required Artifacts for a Reproducible Prototype

The following artifact set is sufficient to build a reproducible first version of the proposed diagram.

## 30.1 `hcin-core.ttl`

**Format:** Turtle / RDF / OWL

Purpose:

- define HCIN core classes;
- define `Person`;
- define `Organization`;
- define `Relationship`;
- define `Interaction`;
- define `FinancialFlow`;
- define `FinancialAuthority`;
- define `Membership`;
- define temporal properties;
- define epistemic concepts;
- define provenance relationships;
- define contexts and goals.

This file is the semantic schema.

---

## 30.2 `hcin-financial.ttl`

**Format:** Turtle / RDF

Purpose:

- define the financial projection vocabulary;
- define financial flow concepts;
- define inflow/outflow semantics;
- define decision authority;
- define financial magnitude concepts;
- define financial dependency;
- define projection-relevant relationship types.

This can initially remain separate from `hcin-core.ttl` to keep the core model generic.

---

## 30.3 `hcin-shapes.ttl`

**Format:** SHACL Turtle

Purpose:

- validate HCIN entities;
- identify missing properties;
- validate financial events;
- require timestamps where necessary;
- validate currency and amounts;
- identify ambiguous authority assertions;
- drive potential clarification questions.

Example use:

```text
FinancialFlow exists
but amount is missing
        |
        v
SHACL violation / knowledge gap
        |
        v
possible clarification question
```

---

## 30.4 `synthetic-transcripts.jsonl`

**Format:** JSON Lines

Purpose:

Store the temporally aligned synthetic on7o transcriptions.

Example:

```json
{"id":"transcript-001","timestamp":"2026-08-24T12:30:00-03:00","text":"Almocei com o Bob hoje. Discutimos a renovação do contrato da ACME."}
{"id":"transcript-002","timestamp":"2026-08-21T10:20:00-03:00","text":"A Carol me ligou para conversar sobre os próximos passos comerciais com a ACME."}
{"id":"transcript-003","timestamp":"2026-08-18T16:45:00-03:00","text":"A Maria aprovou um pagamento de 300 mil relacionado ao contrato da ABC."}
```

No audio files are necessary.

---

## 30.5 `hcin-data.ttl`

**Format:** Turtle / RDF

Purpose:

Contain the concrete network generated from the synthetic transcripts, including:

- `Me`;
- people;
- organizations;
- memberships;
- interactions;
- financial flows;
- financial authority;
- evidence references;
- timestamps;
- epistemic status.

This is the semantic dataset used by the prototype.

---

## 30.6 `projection-config.json`

**Format:** JSON

Purpose:

Define the analytic and visual mapping parameters.

Example structure:

```json
{
  "temporalProximity": {
    "periodHours": 24,
    "decayFactor": 0.9,
    "minDistance": 80,
    "maxDistance": 600
  },
  "interactionWeights": {
    "mention": 0.1,
    "message": 0.3,
    "phoneCall": 0.6,
    "meeting": 1.0,
    "strategicMeeting": 1.5,
    "financialNegotiation": 1.5
  },
  "financialMagnitude": {
    "metric": "gross",
    "scale": "log",
    "minRadius": 8,
    "maxRadius": 40
  }
}
```

The exact numbers are experimental.

---

## 30.7 `projection-data.json`

**Format:** JSON

Purpose:

Contain the **derived graph**, ready for rendering.

This file is generated from the RDF dataset plus projection configuration.

Example:

```json
{
  "asOf": "2026-08-24T13:00:00-03:00",
  "ego": "me",
  "nodes": [
    {
      "id": "bob",
      "type": "person",
      "organization": "acme",
      "interactionProximity": 4.82,
      "visualDistance": 112.4,
      "financialMagnitude": 120000,
      "radius": 19.2,
      "financialAuthority": "none"
    }
  ],
  "edges": [
    {
      "source": "me",
      "target": "bob",
      "directFinancialFlow": false,
      "stroke": "dashed"
    }
  ]
}
```

This is a projection artifact, not the source of semantic truth.

---

## 30.8 `metrics.json`

**Format:** JSON

Purpose:

Store computed values independently from rendering:

```text
interaction proximity
visual distance
gross financial magnitude
net financial balance
authority score
dependency score
reciprocity
relationship vector w(t)
```

Separating metrics from rendering helps with testing and reproducibility.

---

## 30.9 `queries/`

**Format:** SPARQL files

Suggested files:

```text
queries/
    people.rq
    organizations.rq
    memberships.rq
    interactions.rq
    financial-flows.rq
    financial-authority.rq
    evidence.rq
    projection-base.rq
```

Purpose:

Retrieve the semantic facts necessary to calculate the projection.

---

## 30.10 `projection-engine`

**Format:** Java code or equivalent implementation

Purpose:

Compute:

```math
P_{ij}(t)
```

```math
D_{ij}(t)
```

```math
F_{ij}
```

```math
\mathbf{w}_{ij}(t)
```

and generate:

```text
metrics.json
projection-data.json
```

In the on7o context, this can naturally be implemented as a Spring Boot service using Apache Jena.

---

## 30.11 `financial-network.html`

**Format:** HTML + JavaScript

Purpose:

Render the interactive graph.

A graph visualization library may be used, but it should receive only the computed `projection-data.json`.

Responsibilities:

- render ego;
- render people;
- render organizational regions;
- position nodes according to visual distance;
- encode financial intensity in node size;
- encode flow direction;
- encode financial authority;
- render dashed and continuous edges;
- allow time navigation;
- provide tooltips or detail panels.

---

## 30.12 `README-financial-projection.md`

**Format:** Markdown

Purpose:

Explain:

- how to run the prototype;
- how to regenerate RDF from transcripts;
- how to calculate metrics;
- how to regenerate the projection JSON;
- how to open the visualization;
- expected output;
- current limitations.

---

# 31. Minimal Artifact Set

For the smallest useful prototype, the following files are enough:

```text
hcin-core.ttl
hcin-financial.ttl
hcin-shapes.ttl

synthetic-transcripts.jsonl
hcin-data.ttl

projection-config.json
metrics.json
projection-data.json

queries/
    interactions.rq
    financial-flows.rq
    financial-authority.rq
    projection-base.rq

projection-engine/
financial-network.html
```

The pipeline is:

```text
synthetic-transcripts.jsonl
            |
            v
      on7o interpretation
            |
            v
       hcin-data.ttl
            |
      +-----+------+
      |            |
      v            v
hcin-core.ttl  hcin-shapes.ttl
      |            |
      +-----+------+
            |
            v
       SPARQL queries
            |
            v
     projection-engine
            |
       +----+----+
       |         |
       v         v
 metrics.json  projection-data.json
                     |
                     v
             financial-network.html
```

This artifact set separates:

```text
source evidence
semantic truth
validation
analytics
projection
visualization
```

That separation is important because it allows each layer of HCIN to evolve independently without turning the diagram itself into the data model.

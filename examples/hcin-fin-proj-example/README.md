# Financial projection example

The synthetic data behind `../hcin-fin-proj-example.png`, the hand-drawn diagram
this projection was designed to reproduce.

```
transcripts.jsonl   twelve thoughts, with explicit offsets and no audio
cthoughts/e-NN.ttl  what consolidation produces for each, in HCIN terms
```

What it contains, and why each piece is there:

| In the drawing | In the data |
|---|---|
| ACME, ABC and XYZ enclosing their people | memberships with roles and start dates |
| Me inside My Company | the ego belongs to an organization too |
| John Doe outside every enclosure | a person with no membership at all |
| green Carol | revenue authority, scoped to ACME approvals |
| red Maria | expenditure authority, scoped to ABC contract payments |
| green line to Carol | 85,000 coming in |
| red lines to John Doe and Maria | 12,000 and 300,000 going out |
| dashed lines everywhere else | relationships with no money in them |
| green ACME, red ABC, dashed XYZ | the balance with everyone inside each one |

Distances and node sizes are not in the drawing, so the interaction history was
written to put ACME nearest, ABC and John Doe in the middle, and XYZ farthest.

To load it into a running server, post each transcript as a text thought, write
the matching `cthoughts/e-NN.ttl` into that thought's directory as
`cthought.ttl`, and call `POST /api/thoughts/{id}/reconcile`.

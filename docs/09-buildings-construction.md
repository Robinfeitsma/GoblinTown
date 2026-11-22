---
title: Buildings & Construction
status: approved
updated: 2026-11-22
placeholders: true
---

# Buildings & Construction

## Summary

Buildings are the town layer of the founding-wipe cycle: they host role
goblins (an assignment needs a building to work at), store material, and act
as tier gates. Construction is a fraction of the cycle budget — building a
founding structure is minutes of idle time, not a project. This doc defines
the building model, the construction mechanic, the `buildings.json` table
shape, and the Phase-2 founding build order; it inherits all scale decisions
from [Economy Balance](08-economy-balance.md). Grid placement is scoped
deliberately: Phase 2a runs buildings as **abstract slots** (no map math),
full isometric grid placement lands with world-gen (Phase 3), because
spatial layout only becomes a real decision when the hero aura exists
([Idle Loop](03-idle-loop.md)).

## Links

- Depends on: [Idle Loop & Town Economy](03-idle-loop.md) ·
  [Economy Balance (Phase 1)](08-economy-balance.md) ·
  [Core Game Loop](02-core-loop.md) · [03-architecture](06-architecture-modularity.md)
- Feed into: `buildings.json` + `town-tiers.json` (H5), sim-town module

## Player-facing behavior

| Trigger (what the player does) | State change | Observable outcome |
|---|---|---|
| Picks a building to build | site command validated (affordable + unlocked) then executed | site appears with a progress bar; cost deducted upfront |
| Assigns goblins as builders | `builder` labor allocation on the site | site gains +1 progress per builder per tick |
| Building completes | building state → complete; hosted role unlocked (if tier-gated by knowledge) | slots available for assignment; role panel updates |
| Cancels a site | `PLACEHOLDER` refund policy (OQ-2) | site removed; resources per refund table |
| Wipe occurs | town layer deleted — sites and complete buildings included (L4) | rebuild costs full price again; role knowledge untouched (L1) |

## Design

### Building model

Every building instance has: `type`, `state (site | complete)`,
`progress`, and Phase-1-over-Phase-2a state: none (abstract slots). No
position, no facing — grid placement data joins in Phase 3 as extra fields
(save migration, save/doc discipline per H4).

```
Build command (Phase 2a)          Validate → deduct → site → progress → complete
├── validate: type unlocked?      (tier gates, below)
├── validate: stockpile ≥ cost?   (hard refuse, no partial payment)
├── deduct cost at confirmation   (deterministic: nothing else can afford it meanwhile)
├── progress += builders × 1 × siteMultiplier, per tick
└── complete at progressCost      (siteMultiplier = 1 in Phase 2a)

All costs are whole units; `progressCost` in progress-points: 1 unit of
cost ≈ 1 progress point (see table below).
```

### buildings.json schema (lands with its mechanics — this doc approved first)

```json
{
  "id": "wood-camp",
  "hosts": "gatherer",
  "slots": 4,
  "cost": [{ "resource": "wood", "amount": 450 }],
  "progressCost": 4500,
  "startUnlocked": true,
  "tier": 0,
  "passive": null
}
```

| Field | Unit | Meaning |
|---|---|---|
| `hosts` / `slots` | — | which role may be assigned here; 0 slots = non-worker buildings |
| `cost` | whole units per resource | deducted at build confirmation |
| `progressCost` | progress points | site completes at this total progress; builders add 1/tick (so 4500 = 75 s at 1 builder × 60 t/s) |
| `tier` | integer | town tier required to build |
| `passive` | `{produces, unitsPerSecond}` or null | idle output with zero assignments (idle-loop edge case) |

### Founding build order — draft numbers (Phase 2a, founding cycle 1)

Anchor: P1 (docs/08) puts a cap at ~12 min of early fill, so a first
building costing ~½ a wood cap (450 wood) is **~2–3 goblin-minutes of idle
time**; the full founding build order fits one burst (P4 target), per
docs/08's scale chain ("first building ≈ ½ wood cap, tier gate ≈ ½ wood +
½ stone cap"):

| # | Building | Cost (draft) | progressCost | hosts | slots | Notes |
|---|---|---|---|---|---|---|
| 1 | wood camp | 450 wood | 4500 | gatherer | 4 | `startUnlocked: true` — the hero's first project |
| 2 | stone quarry | 450 wood | 4500 | miner | 4 | needs wood camp complete (`tier: 0`, gated by camp) |
| 3 | food larder | 300 wood | 3000 | — | 0 | capacity +400 food (see table below) |
| 4 | tier gate: goblin hall | 450 wood + 300 stone | 5400 | — | 0 | gate to tier 1 → role unlocks continue there |

Capacity buildings multiply the cap from docs/08 (each +400 food, +600 wood,
+400 stone `PLACEHOLDER` — tuned with tier thresholds when tiers land).
Raw caps from docs/08 are the *founding floor*; buildings extend them.

### Worker accounting (binding, Phase 2a)

- `builder` is a labor mode of any role goblin, **not** a role: assigning
  goblins to a site consumes no production (they aren't at their post).
- A goblin is either assigned to work or assigned to build — never both
  (hero rule analogy: one goblin, one job; idle-loop Q4 guard).
- XP does not accrue while building (OQ-2 resolution in docs/08 binds
  *production*-clamped XP only).

### Town tiers (mechanic only, thresholds PLACEHOLDER)

Building 4 completes → tier 1 → role knowledge check (L1): roles already
known re-recruit instantly; new tier roles enter the recruit list.
`town-tiers.json` holds `{tier, unlocks:[roles], buildingPrereqs}`.

## Edge cases

- Offline: linear progress ⇒ closed-form catch-up exact (H1/H2, same rule
  as production; sites store `progress` in game state, not timers).
- Zero stockpile / empty mid-site: costs are deducted upfront, so a site
  never starves; builders idling at an empty stockpile make no progress
  (nothing to build with — same marker as production-clamp, no event).
- Cancel/destroy: `PLACEHOLDER` (OQ-2) — draft: refund 50% of remaining
  cost? Phase-1 landing says **full refund, cost already deducted ⇒ simple
  credit-back** of unspent progress portion (binding choice below).
- Wipe mid-construction: site deleted with the town (L4); no refund
  (materials were spent — a wipe is supposed to hurt).
- Max sites / duplicate-type limit: none in Phase 2a (founding scale only).

## Player economy check (recompute after wiring, rules/03 expected values)

With docs/08 rates (4 gatherers @ 3 wood/s): wood camp paid in
450 ÷ 12 ≈ 37.5 s of fill time; hall ≈ 62.5 s wood + 50 s stone (2 miners).
Full founding build order (1–4) ≈ 4 caps of material ≈ **one 10–20 min
burst with assignment shuffling** — matches P4. These become the expected
values in construction-acceptance tests when wired.

## Open questions

- OQ-4 — Do buildings *repair* (post-run attrition?) — deferred with combat;
  no attrition model exists yet.

### Resolved by user review (2026-11-22)

- OQ-1 — **Abstract slots in Phase 2a.** Grid placement + world-gen is a
  later phase; position data joins via save migration then.
- OQ-2 — **Full refund on cancel** of the deducted cost.
- OQ-3 — **Draft build-order numbers confirmed** (450/450/300/450+300 wood/
  stone; progressCost = cost).
- Worker-accounting clarification at approval: Phase 2a tracks production
  assignments and builder allocations as separate pools; the one-goblin-one-
  job cross-check between them needs global population accounting, which
  lands with the recruitment/tier phase. Until then the sim never lets a
  builder contribute production (same tick: builders produce nothing).

## Changelog

- 2026-11-22 — **Approved by user review**: OQ-1 abstract slots, OQ-2 full
  refund, OQ-3 numbers confirmed; worker accounting clarified (pool
  cross-check deferred to recruitment phase). Status draft → approved.
- 2026-11-22 — Initial draft: abstract-slots Phase 2a scoping, building
  model + `buildings.json` schema, founding build order anchored to docs/08
  caps, worker accounting (builder = labor mode), tier mechanics, OQ-1–4.

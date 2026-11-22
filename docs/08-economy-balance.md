---
title: Economy Balance (Phase 1)
status: draft
updated: 2026-11-22
placeholders: true
---

# Economy Balance (Phase 1)

## Summary

This doc replaces the `PLACEHOLDER` numbers in the idle-loop formulas with a
concrete, tunable scale chain for the Phase-1 economy (resources + worker
roles, no buildings yet). It fixes the **unit conventions**, the **target
pacing** (derived from the approved loop docs, not invented here), and
**draft numbers** for Phase 1; later phases (buildings, tiers, hero aura)
inherit the chain and fill in their own rows. Balance lives in
`data/economy.json` (H5) — this doc is the justification and the target
table, the JSON is the source of truth the sim loads.

## Links

- Depends on: [Idle Loop & Town Economy](03-idle-loop.md) ·
  [Core Game Loop](02-core-loop.md) · [DR-0003 fixed-point](../decisions/0003-fixed-point-convention.md)
- Rules: [coding rules](../rules/02-coding.md) §2 (no constants in code),
  [technical requirements](01-technical-requirements.md) H5

## Pacing targets (binding, from approved docs)

| # | Target | Source | Value |
|---|---|---|---|
| P1 | 4 founding workers fill their stockpile cap while unattended | idle feel | **10–12 minutes** (return-from-breakfast scale) |
| P2 | An average role goblin completes 2–3 level-ups during one active burst (10–20 min) | session shape, pillar 2 | 10–20 min |
| P3 | 8 h offline leaves the player measurably richer than staying (pillar 1) | 00-game-concept | = caps full **+ continued XP** (open question OQ-2) |
| P4 | Early founding cycle ≈ one play burst | 02-core-loop §Wipe pacing | 10–20 min |

## Scale chain (the part later phases inherit)

Resources → caps → rates are derived top-down from cycle targets, so every
number has a reason:

```
P4 (cycle ≈ 1 burst)  →  founding build order needs ≤ 2–3 caps of material
                      →  P1 (caps: 4 workers × per-worker rate × ~700 s)
                      →  role rates (units/s, whole numbers)
                      →  building costs scale: first building ≈ ½ wood cap,
                         tier gate building ≈ ½ wood + ½ stone cap   (PLACEHOLDER — buildings phase)
```

### Level curve

Fixed by `Balance.RoleDef` (already implemented, DR-0003):
`XP to complete level n = levelUpBaseXp + levelUpXpGrowth × (n − 1)`,
cumulative `S(n) = n·base + growth·n(n−1)/2`. Linear growth keeps the
closed-form catch-up exact (no chain of thresholds).

## Draft numbers — Phase 1

Resources and caps (`data/economy.json`, per DR-0003 whole units):

| Resource | Capacity (draft) | Capacity (was PLACEHOLDER) | Rationale |
|---|---|---|---|
| food | 800 | 500 | reserved for upkeep/consumption (later phase); sized between wood and stone |
| wood | 900 | 500 | universal building material: 4 gatherers @ 3/s fill in ~12.5 min (P1 high end) |
| stone | 600 | 300 | 4 miners @ 2/s fill in ~12.5 min (P1); rarer, gates tiers |

Roles (per assigned goblin, whole units/second; sim converts to per-tick
Q32.32 at load):

| Role | Produces | Rate (draft) | XP/s | Notes |
|---|---|---|---|---|
| gatherer | wood | 3 (was 6) | 2 | first role, lowest tier |
| miner | stone | 2 (was 4) | 2 | tier-gate material: slower, denser value |

### XP curve check (base 100, growth 50, XP 2/s — kept from placeholders)

| Level completed | Cumulative XP | Time per goblin at 2 XP/s |
|---|---|---|
| 2 | 100 | 50 s |
| 5 | 700 | ~5.8 min |
| 10 | 2 700 | ~22.5 min |
| 20 | 10 450 | ~87 min |
| 30 | 23 200 | ~3.2 h |

Satisfies P2 (2–3 level-ups per burst ≈ levels 5–10 by 20 min) and gives
overnight runs (P3) 8+ level-ups per goblin **if** OQ-2 resolves to
"XP continues at cap".

### Rolled back from the placeholders

Halving the role rates (6→3, 4→2) triples cap fill time into P1 territory;
caps grew ~2× so building costs have room to live inside a cap or two. All
draft numbers stay within `Balance` validation guards (positive rates,
positive capacity, MAX_LEVEL ≤ 1 000 000).

## Player-facing behavior

| Trigger (what the player does) | State change | Observable outcome |
|---|---|---|
| Assigns goblins to a role | assignment count increases | stockpile gains `count × rate` per second (fixed-point exact) |
| Stockpile reaches cap | production for that resource stops | `StockpileFull` event row in the timeline |
| Role goblin accrues while producing | XP pool crosses threshold | `RoleLevelUp` event; level visible on the role panel |

## Edge cases

- Offline: linear rates ⇒ closed-form catch-up exact (DR-0003); caps clamp
  silently and emit `StockpileFull` once (sim behavior, unchanged here).
- Zero assignments: Phase 1 has no government/our production (idle with zero
  assignments produces nothing — buildings doc must state passive rates).
- Food/wood/stone overflow: clamp + event (chosen; alt-overtone semantics in
  OQ-1 must be resolved *before* buildings make overflow a real choice).
- Max level: engine guard 1 000 000; no soft cap designed yet (OQ-3).

## Open questions (user decisions)

- OQ-1 — Storage overflow semantics: **clamp + event** (this doc's choice;
  zero jag), OR waste/bank the overflow for later. Affects fun when buildings
  arrive; cheap to change while Phase 1 is the only consumer.
- OQ-2 — XP while stockpile is full: **continue accruing** (rewards real id
  time, supports P3) vs pause (production-honest but makes caps painful).
  Phase-1 sim currently continues XP independent of caps — matches this
  doc's recommendation as drafted.
- OQ-3 — Deep-level soft cap or mastery return at high levels: deferred vs
  level 50+ (needs role power mechanics that don't exist yet).
- OQ-4 — Hero aura: range, strength, powerup scaling deferral (balance doc
  later — the idle-loop doc already scopes it as a bonus).

## Changelog

- 2026-11-22 — Initial draft: pacing targets P1–P4 derived from approved
  docs, scale chain, Phase-1 draft numbers (caps 800/900/600, rates 3/2,
  XP curve retained), open questions OQ-1–OQ-4.

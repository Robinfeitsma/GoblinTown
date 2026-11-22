---
title: Idle Loop & Town Economy
status: approved
updated: 2026-02-11
placeholders: true
---

# Idle Loop & Town Economy

## Summary

The idle loop is what runs while the player dreams: role goblins produce per
tick, buildings process, the stockpile fills, and on return the player reads a
report and spends 10–20 active minutes assigning, building and preparing the
next run. Early in a founding the hero does the work himself; as roles come
online he can step back. Nothing in the loop requires the player's presence
beyond assignment decisions, and those decisions persist.

## Links

- Depends on: [Core Game Loop](02-core-loop.md) · [Game Concept](00-game-concept.md)
- Rules: H1 (determinism), H2 (bounded offline catch-up) in `01-technical-requirements.md`

## Player-facing behavior

| Trigger (what the player does) | State change | Observable outcome |
|---|---|---|
| Assigns a goblin to a building/role | assignment added to sim | goblin walks to post; production rate changes next tick |
| Builds a building | site placed on grid; construction ticks | building completes in T ticks; may unlock a town tier |
| Town tier reached | role knowledge check → unlock or re-recruit | new role becomes recruitable; notification on return |
| Player closes the game | nothing (state already on disk or autosaved) | offline catch-up computed on return |
| Player returns after absence | offline tick math runs | **Return report** (see below) |

## Production model (proposal)

```
Tick (base unit)
├── each assigned goblin: role output per tick, modified by
│     building level × role XP × hero auras (if adopted, see Q1) × town tier bonus
├── buildings with inputs consume from stockpile, then produce
├── construction sites add +1 progress per assigned tick
└── stockpile caps apply (overflow behavior: PLACEHOLDER)
```

- All rates integer/fixed-point per H1; no floating point in sim math.
- Rates live in data tables (H5): `role`, `building`, `tier` tables with
  production/consumption per tick. Phase-1 numbers are approved in
  [Economy Balance](08-economy-balance.md) and live in
  `data/economy.json`; building/tier tables remain `PLACEHOLDER`.
- Offline catch-up: production for elapsed time is computed in closed form
  per building (rate × ticks, respecting caps and unlocks), NOT simulated
  tick-by-tick (H2). Where interactions make closed form impossible, chunked
  stepping with a step budget (see `rules/02` §3).

### The hero in the idle loop — resolved (Q1): aura anchor

- Founding phase: the hero is the first "worker" (only unit) — thematically
  right, teaches the production model.
- After the first roles come online: the hero parks at the throne/campfire and
  radiates a modest production aura to **nearby buildings** (range and strength
  `PLACEHOLDER` — balance doc). Grid-tile placement becomes a spatial puzzle:
  *where* the hero parks matters.
- The aura is a bonus, never a requirement: idle production works with the
  hero away on a run (particularly important since runs pause only when
  offline — resolved Q4 — and the hero should never be spread across two jobs).
- Hero aura strength should scale with acquired hero powerups so hero growth
  is felt in the idle loop too (pillar 2: "you are the goblin").

### Assignments persist

- Assignments, building layouts and pending plans are part of the saved town
  state (H4). Returning requires zero re-setup.
- **Automation is a reward (resolved Q3):** in the early town every assignment
  and new building is staffed manually. Unlockable town-tier tech **"Overseer"
  slots** introduce tiered auto-fill (per building → per zone → global
  priority queue; slot counts/costs `PLACEHOLDER`). Micromanagement demand
  therefore *shrinks* as the town matures — consistent with pillar 3 and the
  wipe-pacing arc (early game = hands-on chaos, late game = organized warren).

## Return report (resolved Q2: full timeline)

On return after absence ≥ some threshold (`PLACEHOLDER`: 5 min):

1. One headline line: time away, stockpile delta, notable events (tier
   reached, construction finished, squad healed).
2. **Full ordered timeline** of everything that happened offline — however
   long the absence. Constraint this imposes on the sim:
   - events are compact, sequential records in game state (H4 save data), not
     scratch strings;
   - the timeline is paginated/virtualized in UI; nothing is "summarized away";
   - offline catch-up (H2) must emit the same timeline as live play — closed-form
     catch-up still generates per-interval event rows, in bounded time;
   - storage guard: absence duration is capped by disk math, not ad hoc (save
     size budgeted in the save-format doc, `PLACEHOLDER`).
3. One clear **call to action** (usually: prepare/run a dungeon).

Events are produced by the deterministic sim (part of game state → saved →
must survive offline math identically). This is a design constraint, not UI
garnish: offline catch-up and live play generate byte-identical events (H1/H2).

## Edge cases

- **Offline past stockpile caps:** rate × time, capped at cap; no negative
  side effects during absence; report states "storage full since HH:MM".
- **Offline during a run (resolved Q4):** the run **pauses** exactly where it
  is when the player goes offline. Implications: run state is checkpointed
  into the save (H4); plan resolution must be resumable — trivially true for a
  deterministic sim (run continues from saved state on return); the return
  report's call to action becomes "the run is waiting". UI must clearly show
  run-in-progress state before the player closes the game.
- **Idle with zero assignments:** town still ticks (buildings may passively
  produce at reduced rate per building type; `PLACEHOLDER` table exists in
  balance doc).
- **Hero assigned + leaving on a run in the same tick:** ordering defined in
  run doc; hero cannot be in two places (guard clause in design, not UI).

## Formulas (all PLACEHOLDER — structure is the binding part)

| Asset | Base rate/tick | Modifiers | Notes |
|---|---|---|---|
| Gatherer (wood) | `PLACEHOLDER` | building level ×1.15^lvl, round down | first role |
| Miner (stone) | `PLACEHOLDER` | as above | tier 1 role |
| Warrior (none) | consumes; produces run-power | XP per training tick | no town output |
| Construction | +1 progress/tick/worker | site multiplier | — |

## Open questions

- [ ] Hero aura specifics: range (in tiles), strength, which powerups scale it (balance doc)
- [ ] Overseer tech: slot costs, per-building vs per-zone granularity details (balance doc)
- [ ] Storage overflow semantics: clamp, waste, or bank overflow (affects fun & balance)
- [ ] Report absence threshold, save-size budget for the full timeline (save-format doc)

## Changelog

- 2026-02-11 — Resolved Q1 (hero = aura anchor at throne/campfire), Q2 (full
  timeline report; sim must emit bounded-time identical events offline), Q3
  (automation = unlockable Overseer tech tiers), Q4 (runs pause offline; run
  state checkpoints into saves). Status → review.
- 2026-02-11 — Initial draft: production model, persistent assignments,
  return report, edge cases; Q1–Q4 opened.

---
title: World & Environment — The Underground Realms
status: approved
updated: 2026-02-11
placeholders: true
---

# World & Environment — The Underground Realms

## Summary

GoblinTown takes place in a vertical underworld. The game opens at the very
bottom: a single goblin in the dark, with a thirst to found his own tribe. The
player growth arc is **conquest of the underground realms, layer by layer**,
culminating in reaching the surface world. "Migration" (the voluntary wipe from
the core loop) is the mechanism of vertical movement: the tribe abandons its
current warren and strikes roots in a new stratum, trading the town layer for a
permanent meta bonus.

## Links

- Depends on: [Core Game Loop](02-core-loop.md) (migration = voluntary wipe) ·
  [Game Concept](00-game-concept.md)
- Feeds: dungeon/run environments (04-run-system.md), H6 rendering budgets

## The vertical world (proposal)

```
SURFACE  ────────────────────────────  the ascension goal (open question V3)
  ▲  Skyrealm threshold: foraging, weather, dangerous light
  │
  │  STRATUM: Underworld crust — caverns, roots, burrowed beasts
  │
  │  STRATUM: Deep dark — rarer ores, blind predators, rival goblin tribes
  │
  │  STRATUM: Underdeep — ancient ruins, magic wards, hostile elderthings
  │
GROUND ZERO  ◄─────────────────────────  game start: a lone hero goblin
```

- **Strata** are the major world divisions. Each stratum hosts a full
  founding-wipe cycle: a town lifecycle happens *inside* one stratum.
- **Resolved (V1): many strata, plus endgame endlessness.** A substantial
  authored/semi-procedural base ladder (`PLACEHOLDER` count, target ≥ 6–8)
  of themed strata; beyond the authored arc the world keeps generating — an
  endgame "endless depths" mode. (`PLACEHOLDER` how endless mode unlocks; likely
  tied to the surface prestige, V3.)
- Depth = difficulty + rarity. Deeper resources and encounters raise town tier
  ceilings and run loot tables. The player **can** ascend, camp, or descend —
  resolved V4 below.
- Each stratum regenerates a fresh map deterministic from seed (like dungeons,
  matching H1) — the sim's map model is identical between strata.

## Conquest of a stratum (proposal)

The underground realms are not empty:

- **Fauna & hazards** — the baseline threat layer: cave beasts, roots, floods,
  gas pockets. Town-level defenses matter even when idle (dark corners produce
  attrition until lit/claimed).
- **Rival squatters** — monster nests and rival goblin/other-tribes camps of
  varying scale. Small nests are dungeon-run targets; large rival settlements
  are conquest objectives (`PLACEHOLDER` mechanics — see V2).
- **Wonder sites** — ancient ruins, shrines, underdeep gates: the "wow" layer
  that carries lore and high-rank follower hunts (ties into run system R2 rewards).

Conquering a stratum culminates in unlocking the passage up (or the "abandon
this realm" migration), coupling territory to the founding-wipe loop.

### Conquering rival settlements — resolved (V2): mixed

- **Small nests** (beasts, minor camps): dealt with by dungeon **runs** — the
  standard plan-then-watch dive.
- **Large rival settlements**: defeated by **both play modes combined** —
  repeated runs (raids) weaken them until the player's own warren expands to
  reach their site and finishes the conquest. Each raid costs resources +
  risks a wipe; expansion pressure grows with raid outcomes
  (`PLACEHOLDER` exact weakening/expansion formulas — balance doc).
- This makes large conquests a strategic layer spanning idle (expansion) and
  active (raids) play, and gives raids a purpose beyond loot.

## Stratum movement — resolved (V4): free risk/reward choice

- Migration (voluntary wipe) can move the tribe **up one stratum, stay in the
  current one (re-found), or move down**.
- Deeper = harder but richer; the player balances risk/reward, and may
  deliberately re-farm easier strata for specific resources or safer framing
  (a legit "grind" consumer of migration).
- Migration to an already-visited stratum regenerates its map from a new seed.
  (`PLACEHOLDER`: does revisiting cost less than a fresh descent? Probably — balance doc.)

## How strata interact with the existing layers

| Existing layer | Stratum effect |
|---|---|
| Town building | New stratum = new terrain palette, resource nodes, hazards; grid rules unchanged |
| Role unlocks | Role knowledge is meta and persists across strata; deeper strata gate advanced roles' *effectiveness* rather than knowledge |
| Runs (dungeons) | Dungeon flavor + loot tables + enemy pools per stratum; a dungeon run in the current stratum uses that stratum's seed spaces |
| Wipe economics | Failed run → town wiped, hero + followers walk on *in the same stratum*; voluntary migration → new stratum |
| Idle loop | No change to math; darkness/light and hazard attrition are town-layer modifiers, not separate sims |

## The surface world — resolved (V3): prestige + NG+

- Reaching the surface is the **climax of the authored arc**: a capstone
  milestone with cinematic payoff.
- It grants a **huge permanent meta reward** (`PLACEHOLDER` reward shape:
  hero powerups / new follower slots / permanent multipliers — balance doc).
- Afterwards the player may **start a new cycle of the world (NG+)**: a fresh
  descent with deepened difficulty, new biome themes, and prior meta intact —
  the roguelite grand loop repeats at a higher plane.
- NG+ also gates the **endless depths** mode (V1): after NG+ is unlocked,
  depth generation continues indefinitely.

## Open questions

- [ ] Authored strata count + theme list (a game-plan deliverable; content doc)
- [ ] Raid weaking / settlement expansion formulas (balance doc)
- [ ] Migration revisit-cost rules; NG+ reward shape (balance doc)
- [ ] Dark/light attrition numbers (balance doc)
- [ ] Map generation seed spec per stratum (map-gen system doc)
- [ ] Surface-layer gameplay detail in postgame (town rules above ground — later doc)

## Changelog

- 2026-02-11 — Resolved V1 (many strata + endgame endless depths), V2 (mixed
  conquest: raids weaken, expansion finishes), V3 (surface = prestige + NG+,
  unlocking endless mode), V4 (free stratum movement: up/stay/down). Status →
  review.
- 2026-02-11 — Initial draft from user direction: vertical underground start,
  conquest arc, surface as ascension. V1–V4 opened.

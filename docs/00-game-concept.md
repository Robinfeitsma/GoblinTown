---
title: Game Concept — GoblinTown
status: approved
updated: 2026-02-11
placeholders: true
---

# Game Concept — GoblinTown

## Summary

GoblinTown is an isometric grid-tile town-builder crossed with an idle game and
a roguelite, set in a **vertical underworld**. The player **is a goblin** — a
lone survivor deep underground, with a thirst to found his own tribe. The hero
persists skills, gear and powerups across the whole game (roguelite
meta-identity); the rest of the tribe progresses per-role, so tribe goblins are
interchangeable and their losses are cheap. The player directs the tribe in
short active bursts (build, assign, equip), dungeon runs are planned then
watched, everything ticks along while the player is away — and the long arc is
**conquest of the underground realms, ascending stratum by stratum to the
surface world** (prestige + NG+ + endless depths).

## Genre pillars

| # | Pillar | One-line test |
|---|--------|---------------|
| 1 | **The town never sleeps** | Leaving for 8 hours must feel better than staying |
| 2 | **You are the goblin** | The hero's skills/gear carry over everything; he is never reset |
| 3 | **Direct, don't babysit** | No action requires more than ~10 seconds of attention; long-term goals are automated |
| 4 | **Everything is goblins** | Every system is expressed through goblin units, not abstract sliders |
| 5 | **Dig toward the light** | The vertical arc (depth → surface) is the game's spine; every stratum must feel earned |

## Core loops (draft)

```
Idle loop:      Town state → offline tick math → production report → player returns
Active loop:    Player directs goblins (build/assign/expand) → production ticks
Run loop:       Player-herogoblin + followers loadout → plan route/orders →
                watch run resolve → loot/deaths → town + hero upgrades
Meta loop:      Failed/finished run → hero keeps skills & gear →
                role upgrades + hero powerups → next run stronger
```

## Resolved decisions

| Decision | Resolution | Consequence |
|----------|------------|-------------|
| Combat control | **Plan, then watch**: orders/route given up front, run resolves autonomously, watchable/speedable | Idle-friendly; combat design must be mechanical (legible outcomes), not twitch |
| Goblin progression | **Per-role levels** for tribe goblins | Run deaths are cheap — recovery is retraining time/role XP, not item loss |
| Terrain & placement | **Grid tiles**, isometric | Cheaper pathing/saves; classic builder feel; simplifies H6 perf budget |
| Session shape | **Medium bursts** (10–20 min active) between idle stretches | H2 offline expectation stays "up to overnight"; content chunked into burst-sized goals |
| Platforms | **Windows + Linux** | H3 filled; macOS & mobile are non-goals in phase 1 |
| Team / art | **Solo** (+ LLMs), **placeholder art first** | Language familiarity weighs lighter; art pipeline weighs lighter; game logic asset-agnostic from day one |
| Town vs. hero+followers | **Two-layer model**: the town is the roguelite (reset) layer; hero skills/gear/loot and followers are the meta (persist) layer | Core Loop doc (`02-core-loop.md`) is now the authority on the wipe model |
| Wipe pacing | Dynamic: wipes get rarer and more dramatic over time; late-game wipes are mostly voluntary migrations | See core loop "Wipe pacing" |
| Hero idle role | **Aura anchor**: the hero parks at a throne/campfire and boosts nearby buildings; never mandatory for production | Idle loop doc (`03-idle-loop.md`) |
| Assignment automation | Manual early; unlockable "Overseer" tech tiers auto-fill later | Idle loop doc — micromanagement shrinks as the town matures |
| Vertical world | Underground start → conquest of strata → surface = prestige + NG+ + endless depths; migration moves up/stay/down | World doc (`05-world-environment.md`) |
| Conquest model | Small nests = dungeon run targets; large settlements = raids (runs) + warren expansion | World doc, resolved V2 |

### The hero goblin (new, central mechanic)

- The game opens with exactly one goblin: the **hero** (the player character).
- The hero is **per-unit persistent**: skills, gear and powerups are never
  lost between runs or on failure.
- The hero may accumulate **loyalist followers** (persistent personal retinue
  inside runs — persistence scope `PLACEHOLDER`).
- The hero grows through **item/skill/powerup acquisition**, not role levels —
  this is the deliberate contrast to the per-role tribe (see pillar 2).
- Tension flagged: per-role tribe (cheap deaths) + persistent hero (irreplaceable)
  means **run failure must never kill the hero** without an explicit
  roguelite rule. Resolved: wipes destroy the town layer only — see
  [Core Game Loop](02-core-loop.md) (wipe pacing, L1–L4 resolutions).
- Tension flagged: idle (no wipes = no pressure) vs roguelite (wipes = pressure).
  Resolved via dynamic pacing: early game wipes are frequent and cheap,
  late game wipes become chosen migrations — the idle loop finally "wins"
  without ever deleting the tension. See core loop "Wipe pacing".

## Open questions

- [ ] Follower slots cap + recruitment source list (run system R2, partially resolved: two follower tiers)
- [ ] Isometric rendering target number of animated units per grid-tile scene (`PLACEHOLDER` — feeds H6)
- [ ] Authored strata count/theme list, raid & migration balance formulas (world doc follow-ups)

## Glossary

- **Hero** — the player's persistent goblin; never reset, never permanently lost.
- **Follower** — loyalist NPC bound to the hero; part of the persistent (meta) layer, survives wipes.
- **Role** — a progression track tribe goblins level (miner, warrior, ...); units are interchangeable within a role.
- **Role knowledge** — meta-permanent record that a role has been unlocked; survives wipes.
- **Town layer** — the reset layer: buildings, layout, stockpile, squad goblins.
- **Wipe** — deletion of the town layer after a failed run or voluntary migration.
- **Run** — one planned dungeon expedition led by the hero, ending in success, partial, or wipe.
- **Tick** — the base unit of simulated time; all rates are per tick.
- **Stockpile** — the town's shared resource pool.
- **Offline tick math** — computing production for a past, unattended period.

## Changelog

- 2026-02-11 — Fourth draft: vertical underworld setting adopted (user
  direction); new pillar 5 ("Dig toward the light"); summary rewritten; world
  decisions synced from world doc.
- 2026-02-11 — Third draft: run system resolutions synced (success-only loot
  banking, tiered followers, squad gear-drop/reclaim), remaining open
  questions slimmed to three.
- 2026-02-11 — Second draft: resolved 6 open decisions from user review (hero
  goblin identity, plan-then-watch combat, per-role progression, grid tiles,
  W+L platforms, solo/placeholder art, medium session bursts). New open
  questions flagged from run-failure consequences.
- 2026-02-11 — Initial draft from concept discussion.

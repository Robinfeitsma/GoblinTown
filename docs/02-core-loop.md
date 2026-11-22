---
title: Core Game Loop
status: approved
updated: 2026-02-11
placeholders: true
---

# Core Game Loop

## Summary

GoblinTown's core cycle is a **founding-wipe cycle**: the hero arrives alone,
founds a goblin camp, grows it into a warren using idle production
(role-goblins, town-tier role unlocks), then leads followers on dungeon runs.
Failure does not end the game — it **wipes the town completely** (buildings,
stockpile, squad goblins), while the hero keeps skills, gear and loot, and
loyalist followers persist. Early cycles are short and wipe-heavy; as the
hero's persistent power grows, cycles lengthen and wipes become rare,
deliberate, and dramatic. That acceleration *is* the progression.

## Definitions (shared with the concept doc)

- **Hero** — the player's persistent goblin. Loses nothing but time, ever.
- **Follower** — persistent special NPC bound to the hero; survives wipes.
- **Role goblin** — interchangeable tribe goblin; levels per-role; rebuilt freely.
- **Town / Camp / Warren** — the built settlement layer; **fully resets on wipe**.
- **Wipe** — destruction of the entire town layer; the cycle restarts from a founding.
- **Run** — hero + squad + followers dive a dungeon under plan-then-watch combat.
- **Role knowledge** — meta-permanent record of which roles have ever been unlocked.

## The full cycle

```
            ┌──────────────────────── MORNING OF A NEW AGE ───────────────────────┐
            │                                                                      │
            ▼                                                                      │
  1. FOUNDING        2. GROWTH                3. PREPARATION         4. THE RUN        5. RESOLUTION
  Hero founds   →   Idle ticks + assign  →   Loadout + squad    →   Plan-then-   →   Success : town rewards,
  a new camp        role goblins to          + follower pick,       watch dive        hero keeps loot
  (buildings,       buildings; town          plan route &           autonomously       Wipe : town destroyed,
  resource nodes)   tiers re-recruit         orders                                    hero + followers walk on
            ▲        known roles instantly                                              |
            │                                                                        │
            └─────────────────── 6. NEW FOUNDING (hero + followers persist) ◄────────┘
```

## Phase detail

### 1. Founding
- Starts from a single hero goblin on a map with resource nodes (`PLACEHOLDER` map generation rules).
- The hero personally does starter work (he is the only unit); first buildings enable first role goblins.

### 2. Growth (the idle core)
- Role goblins produce per tick; town tiers unlock roles.
- **Resolved (L1):** once a town tier has ever unlocked a role, that role is
  *role knowledge* — it re-recruits instantly in every future founding.
  Buildings must still be rebuilt; wipes cost rebuild time and stockpile, never
  knowledge.
- Player presence: assign/reassign, build, expand — burst-sized (10–20 min).

### 3. Preparation
- From the town's output: give the hero gear, equip squad + followers, set the dive plan (route, orders, contingencies).
- Town production and run power are one economy — no separate run currency, unless a doc later says otherwise (`PLACEHOLDER`).

### 4. The run (plan-then-watch, H9)
- Fully non-interactive once launched (watchable, speedable, skippable).
- Legible outcome states: success / partial / wipe (see resolution).

### 5. Resolution
- **Success:** loot returns (all carried loot is real only on extraction —
  run system R3), hero keeps everything, town gains (building progress, new materials).
- **Partial:** voluntary abort before extraction: carried loot is lost, the
  town and surviving units come home.
- **Wipe (resolved L4):** **everything town** is deleted — buildings, layout,
  stockpile, squad role-goblins. Two cleanly separated layers: town (reset) vs
  hero + followers + role knowledge (persist). Wipe cost = rebuild time +
  all carried loot (`PLACEHOLDER` outcome pacing in run system doc). Followers:
  high-rank are scarred, ordinary ones permadeath (run system R2).

### 6. New founding
- The cycle restarts; acceleration comes from hero power + followers + role knowledge.

## Wipe pacing (resolved L3, draft)

- **Trigger set (resolved L2):** wipes come from (a) failed runs and (b) a
  voluntary **migration** reset that trades the town for a permanent meta
  bonus (`PLACEHOLDER` bonus formula). Since the world doc (05-world-environment.md),
  migration is also **vertical movement**: up one stratum, stay (re-found),
  or down — the player's risk/reward dial.
- **Dynamic pacing rule:** the longer the game has been played, the rarer and
  more dramatic wipes become. Mechanism draft: hero power + role knowledge make
  runs safer over time, so *failed* wipes fade out; late-game wipes are
  predominantly **voluntary migrations** — chosen, cinematic era endings rather
  than punishments. Early game is churn-tolerant (fast wipe-rebuild cycles).
- Cycle length targets: early ≈ one play burst; late = multi-day per founding,
  ended deliberately (`PLACEHOLDER` tuning curve lives in a balance doc later).

## Open questions (post-loop)

- [ ] Hero's role in the idle loop (worker / aura / absent) — deliberately deferred until the loop above is approved.
- [ ] Follower cap, recruitment source and follower death handling inside runs.
- [ ] Migration bonus formula and what "a new land" regenerates (map, biome?).

## Changelog

- 2026-02-11 — Resolved L1 (role knowledge persists), L2 (failed runs +
  voluntary migration), L3 (dynamic pacing: wipes rarer/more dramatic over
  time, voluntary dominates late), L4 (wipe deletes the whole town layer).
  Status → review.
- 2026-02-11 — Initial draft after user design session: wipe = town reset,
  hero loot/skills persist, followers persist. L1–L4 opened as loop blockers.

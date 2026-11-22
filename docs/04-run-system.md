---
title: Run System (Dungeons & Combat)
status: approved
updated: 2026-02-11
placeholders: true
---

# Run System (Dungeons & Combat)

## Summary

A run is a fully planned, fully autonomous dungeon dive. The player prepares a
loadout (hero gear, followers, squad of role goblins), draws a route with
standing orders, and launches. The deterministic sim then resolves everything —
watchable at 1×/fast/skip, pausable, checkpointed into the save. Outcomes are
legible: success, partial (retreat), or wipe — and only the wipe touches the
town layer.

## Links

- Depends on: [Core Game Loop](02-core-loop.md) · [Idle Loop](03-idle-loop.md)
- Rules: H1, H9 (`01-technical-requirements.md`), rules/03 test categories

## Player-facing behavior

| Trigger | State change | Observable outcome |
|---|---|---|
| Opens dungeon map | dungeon layout generated (seeded) | branches, wards, exit beacons shown |
| Equips hero / picks followers+squad | loadout in run plan | power/summary panel updates |
| Draws route + orders | plan entries added | plan simulation preview (optional, `PLACEHOLDER`) |
| Launches run | run started; town keeps ticking away from the dive | run view: live tick resolution, speed controls |
| Presses speed/skip | sim advances N ticks / to next decision point | outcome reveals |
| Run ends | resolution applied | loot report / retreat report / wipe cinematic |

## Dungeon structure (proposal)

- A dungeon = a **branching graph of zones** (2–4 wide, depth per run `PLACEHOLDER`), rendered on the same isometric grid language as the town.
- Each zone holds one event type: encounter (fight), room (loot/hazard), shrine (choice/offer), **beacon** (heal/regroup point), or boss.
- **Beacons** are heal/regroup points only — they do *not* bank loot
  (resolved R3: loot is safe only on successful extraction).
  Beacons create safe harbor decisions mid-run, not extraction decisions.
- Dungeons regenerate per launch from a seed; seeds are recorded (H1) so any history run is reproducible.

## The plan (resolution input)

A plan is pure data, ordered:

1. **Route:** ordered list of zones (with branch fallbacks, `PLACEHOLDER` UI).
2. **Stance per zone or per situation:** aggressive / defensive / evade (`PLACEHOLDER` stance list).
3. **Contingencies (resolved R1 — middle depth):** triggers like "if hero HP < 30% → retreat to last beacon". Route + stances + a bounded contingency set; no free-form scripting.
4. **Non-combat standing orders:** loot priority, wound priority, follower protection flag.

The plan is saved game state (H4): any interrupted/abandoned run resumes from
checkpoint exactly (Q4 resolution in `03-idle-loop.md`).

## Combat resolution (H9)

- Tick-based, deterministic, no player input after launch. Hero, followers and
  squad act from stance rules + role XP, strictly evaluated in defined order
  (initiative order spec: `PLACEHOLDER`).
- Speed control and skip are presentation-only: they may not change outcomes —
  enforced by sim/presentation split (rules/02 §1) and tested (rules/03 cat. 1).
- Legibility over realism: every outcome must be explainable post-run by the
  battle log ("goblin #3 died to ward trap because stance was aggressive").

## Outcome ladder (resolved R3, R4; follower rule R2)

| Outcome | Hero | Followers | Squad | Carried loot | Town |
|---|---|---|---|---|---|
| Success (full clear or voluntary extraction) | +loot, +XP | as survived | as survived | **all kept** | rewards flow |
| Partial (voluntary abort before extraction) | retreats | as survived | as survived | **all carried loot lost** | survives intact |
| Wipe | hero auto-retreats | high-rank: scarred · others: lost (R2) | dead — gear drops | **all carried loot lost** | **whole town layer destroyed** (L4) |

- **Banking (resolved R3):** all-or-nothing on extraction. Beacons only heal and
  regroup. The risk dial is therefore binary and brutally legible: in the
  dungeon your loot exists, out of it, only success made it real. Early-game
  balance must account for how harsh this is (`PLACEHOLDER` outcome pacing).
- **Squad death & gear (resolved R4):** squad role goblins can really die. A
  dead goblin **drops his possessions** (equipment) on the dungeon floor; if
  the run later *succeeds*, the possessions of fallen squad members are
  reclaimed and return to the stockpile. A wipe loses them forever. So gear on
  squad members is itself part of run risk.
- **Follower tiers (resolved R2):**
  - *High-rank followers* (each one an earned reward, e.g. boss-clear or
    milestone; count `PLACEHOLDER`) are **scarred, not lost**: death or defeat
    puts them gravely wounded, unavailable for the next N cycles
    (`PLACEHOLDER`), never permanently gone.
  - *Ordinary followers* face **permadeath**. Their loss is permanent but they
    are replaceable from town milestones/diplomacy sources
    (`PLACEHOLDER` source list).
- The hero can never permanently die (pillar 2); auto-retreat is a rule with a
  story justification, tuned so it is rare and dramatic (`PLACEHOLDER` trigger).
- Squad death cost = retrain time/role XP + dropped gear risk, never a meta
  loss — the cheap-death contrast vs. followers is the emotional design core.

## Followers (resolved R2, details open)

- Followers are persistent meta-layer NPCs: they survive town wipes and join
  runs by player choice.
- Two tiers: **high-rank followers** (earned rewards; scarred-not-lost on
  defeat) and **ordinary followers** (recruited from town milestones and
  dungeon events; permadeath). See Outcome ladder.
- Open details: slot cap, source list, town presence when idle at home.

## Edge cases

- Launch while town is under construction caps: allowed, production continues.
- Run launched, then game closed mid-run: run paused (Q4), resume on return.
- Hero "auto-retreat" + wipe in same tick: retreat event precedes wipe event in
  the ordered event log (H1 ordering spec).
- All squad dead but run not wiped: continue possible only if plan says so
  (`PLACEHOLDER` min-force rule or auto-partial).

## Open questions

- [ ] High-rank follower count/wow-factor pacing; gravely-wounded cycle count (`PLACEHOLDER` — balance doc)
- [ ] Follower recruitment sources list (dungeon events, town milestones, diplomacy), cap slots
- [ ] Initiative/eval order spec, stance list, contingency trigger list (follow-up system-design doc)
- [ ] Min-force rule: can a decimated-but-not-wiped run continue if the plan says so?
- [ ] Lantern/plan preview: simulate-run-preview before launch — allowed or does it leak too much tension?

## Changelog

- 2026-02-11 — Resolved R1 (route+stances+contingencies), R2 (tiered: high-rank
  followers scarred-not-lost as earned rewards, ordinary followers permadeath),
  R3 (success-only banking; beacons = heal/regroup), R4 (real squad death; gear
  drops, reclaimed on success). Status → review.
- 2026-02-11 — Initial draft: dungeon zones/beacons, plan data model,
  deterministic resolution, outcome ladder; R1–R4 opened.

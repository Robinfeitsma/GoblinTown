---
title: Coding Rules (Language-Neutral)
status: approved
updated: 2026-02-11
---

# Coding Rules

> **Phase 0 note:** no language or engine is chosen yet. This file defines
> binding, language-neutral requirements. Language-specific sections
> (`10-Language-specific`) stay empty until Decision Record `0001` is approved.

## Architecture principles

1. **Simulation separated from presentation.** Game state lives in plain data
   structures; rendering, audio and input never mutate game state directly,
   only through defined commands. This is required by the idle genre (state
   must advance without a frame being drawn) and by testing.
2. **Deterministic simulation.** Given the same initial state and the same
   command list, the simulation must produce identical results — including
   offline/idle catch-up calculation. Randomness comes from a seeded, recorded
   RNG.
3. **Time is accumulated, not sampled.** Idle progression is computed with
   elapsed-time math ("delta compression" of ticks where possible), not by
   looping over missed frames — an 8-hour offline return must resolve in
   bounded time.
4. **Data-driven content.** Content (buildings, upgrades, items, goblin types)
   is data in a serializable format, not hardcoded logic. Balance edits must
   not require code changes.
5. **Save-first, versioned.** Decide the save format early
   (see `docs/01-technical-requirements.md`); every save carries a version
   number and migration plan from day one.

## General code rules

- Small pure functions over global state; no hidden mutable singletons.
- Every gameplay constant referenced from data/config — no magic numbers
  in logic.
- Public functions and data shapes documented in-code; the *why* lives in the
  design doc, the *how* in the code.
- The folder layout is agreed in a Decision Record before the first commit.

## 10-Language-specific

Filled per DR-0001 (approved 2026-02-11): **libGDX + Java**.

### Module layout (Gradle, compiler-enforced modularity)

| Module | Depends on | Contains | Must never contain |
|---|---|---|---|
| `sim` | nothing (no gdx, no framework) | the entire deterministic simulation: town economy, units, tiers, run resolution, world-gen, save/load data model, balance tables schema | any `com.badlogicgames.*` import; any float/double; any I/O-side-effects (files opened outside the save layer); UI concepts |
| `core` | `sim`, `gdx` | libGDX glue: screens, renderers, input→command translation, scene2d UI | game rules; balance numbers; simulation logic |
| `lwjgl3` | `core`, gdx-desktop | desktop launcher, window config | anything not launch-related |
| `headless` | `sim` (NOT through core) | CLI launcher: run headless simulations, catch-up checks, save tools | rendering |

The dependency direction M1–M6 is enforced by the Gradle module graph itself:
`sim` cannot reach the engine because it does not depend on it.

### Java rules

- Java 17+ (LTS). Immutability first: `record` for all data carriers, `final`
  where possible; no public mutable fields anywhere.
- **`sim` ring is int64 fixed-point only**: no `float`/`double` anywhere in
  `sim` (determinism, H1). Fixed-point convention (bits per unit) is fixed in
  a Decision Record before the first balance numbers land.
- `sealed interface`s for command and event hierarchies; pattern matching
  `switch` for command dispatch. Commands and events are records (M3:
  serializable boundary).
- No static mutable singletons; services are passed explicitly.
- Clock/time never enters `sim`: ticks are the only time (determinism, H1);
  the platform layer converts wall-clock → tick counts.
- Package base `dev.goblintown.*`, module mirrors: `dev.goblintown.sim.*`,
  `dev.goblintown.gfx.*` (core/lwjgl3/headless).
- Jackson for JSON (H4); saves are written gzipped, version-stamped,
  migrations live in `sim` (data model owns its save version).
- No checked exceptions thrown from `sim`; invalid states are validation
  errors at data-load or assertion failures at runtime.
- Game constants live exclusively in data tables (H5) under `data/` at repo
  root (plain JSON, loaded and validated at startup).

### libGDX rules

- The engine never owns game state. Renderers read state snapshots; input
  translates into commands; the game loop calls `sim.tick(n)` (A3: fixed
  timestep, tick rate from config, save-constant).
- Speed control (1×/fast/skip) changes how many ticks are run per frame —
  nothing else. Presentation-only by construction (H9).
- SpriteBatch with a single texture atlas; isometric depth-sorting is our own
  code; horde scale built on batching, never per-sprite objects.

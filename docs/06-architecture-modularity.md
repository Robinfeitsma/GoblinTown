---
title: Architecture & Modularity
status: approved
updated: 2026-02-11
placeholders: true
---

# Architecture & Modularity

## Summary

GoblinTown is built as a **strict three-ring architecture**: a pure
deterministic simulation core with zero platform dependencies, a thin command
/event boundary, and a replaceable presentation shell. The engine/language
choice (DR-0001) must *fit* these boundaries — not fight them. This matters
because the same sim must run headless in tests (H7), inside an engine frame,
and over long offline catch-ups (H2) — three hosts, one sim.

## Links

- Depends on: rules/02 (sim/presentation split, determinism, save-first) ·
  `01-technical-requirements.md` (H7, H10)
- Feeds: DR-0001 stack selection

## Module map (proposal)

```
┌─────────────────────────────────────────────────────────────┐
│  PRESENTATION RING (replaceable, engine-bound)              │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐  │
│  │ renderer (iso│  │ ui (plan draw,│  │ shell (game      │  │
│  │ grid, hordes,│  │ reports, over-│  │ loop, screens,   │  │
│  │ anims, camera│  │ lays, menus)  │  │ platform, audio*)│  │
│  └──────┬───────┘  └──────┬────────┘  └───────┬──────────┘  │
│         │        reads state snapshots        │            │
└─────────┼────────────────────┼────────────────┼────────────┘
          ▼                    ▼                ▼
┌─────────────────────────────────────────────────────────────┐
│  COMMAND / EVENT BOUNDARY (thin, serializable)              │
│     commands IN: build, assign, plan-run, set-speed…        │
│     events OUT: tick events, timeline report events         │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  SIM RING (pure, deterministic, platform-free)              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐  │
│  │ sim-town │ │ sim-run  │ │world-gen │ │ data/tables    │  │
│  │ economy, │ │ dungeon  │ │ strata,  │ │ (content load  │  │
│  │ units,   │ │ resolution││ maps,    │ │ + validation)  │  │
│  │ tiers    │ │(H9, H1)  │ │ dungeons │ │                │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ save (JSON+gzip serialisation, versioned migrations) │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
* audio ignored in phase 1 (non-requirement)
```

## The rules that make it modular

| # | Rule | Why |
|---|------|-----|
| M1 | Dependency direction is **downward only**: presentation → boundary → sim. The sim ring must not import anything from the presentation ring — not a type, not a call | Three-host sim (test, live, offline) |
| M2 | The sim ring is a **self-contained library/package**: engine-free, headless-runnable, buildable and testable with one command (H7) | rules/03 determinism tests; stack portability |
| M3 | All cross-ring communication is **serializable data**: commands in, state snapshots + events out. No callbacks into the sim, no live object handles crossing the boundary | Save-checkpoint mid-run (Q4), replay, offline math |
| M4 | Content is **data tables only** (H5) — the sim consumes validated tables; no engine asset types inside the sim | JSON saves, moddability-revisit, balance iteration |
| M5 | `world-gen` is pure: `seed (+depth, +biome params) → map data` | Determinism (H1), endless strata (V1) |
| M6 | The engine is a **slave, not a landlord**: its update loop ticks the sim, but game truth lives in sim state — the engine may be replaced without touching the sim ring | Long-lived project; engine replacement must be possible |

## What this demands from the stack (candidates get scored on it)

- A module/package system able to express the two rings (or the discipline to
  keep folders pure without compiler help — acceptable, weaker).
- A data serialization path to readable JSON (H4) that the sim controls.
- Flowing 300+ sprite draws (H6) with batched/instanced rendering *in the
  presentation ring only* — the sim must never know about sprites.
- A story for "engine loop ticks the sim": fixed-timestep integration
  (`PLACEHOLDER` tick rate target — 30 Hz? 60 Hz sim ticks; balance doc).

## Open questions

- [ ] Fixed timestep implementation details per chosen stack (follow-up system doc)

## Resolved

- **A1 (one language):** one language for both rings is the **default and the
  scoring preference**; a polyglot split (sim in a compiled language, shell in
  a scripting one) is allowed but must demonstrate an outsized benefit in the
  stack comparison to win. Polyglot carries a standing penalty (build/debug
  surface for a solo dev) unless the benefit argument explicitly overcomes it.
- **A2 (granularity):** keep the coarse 8-module map for now; fine sub-modules
  inside `sim-town` appear naturally while coding and are documented when they
  stabilize.
- **A3 (tick rate):** the simulation runs a **fixed timestep, a save-level
  constant — default 60 Hz, configurable down to 30 Hz**. Because all
  production/rates are defined per tick, changing tick rate changes real-time
  pacing only, never balance outcomes or determinism. It must therefore be
  process-start (or save-load) constant — **never flopped mid-run** — so
  offline catch-up math stays unambiguous. Horde pressure affects
  *presentation* cost (batching, LOD), not the tick rate, unless the player
  deliberately sets 30 Hz.

## Changelog

- 2026-02-11 — Resolved A1 (one language default; polyglot needs outsized
  wins), A2 (coarse map), A3 (fixed timestep, save-constant 60 Hz default /
  30 Hz option, rates defined per tick). Status → review.
- 2026-02-11 — Initial draft: three-ring architecture, 8-module map, rules
  M1–M6, stack demands; A1–A3 opened.

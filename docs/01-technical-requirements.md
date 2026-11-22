---
title: Technical Requirements
status: approved
updated: 2026-02-11
placeholders: false
---

# Technical Requirements

## Summary

Technology-neutral requirements the future language/engine choice must
satisfy. This document is the input to `rules/04-stack-selection.md`; the
stack may not be chosen until this doc is `approved`. All prior placeholders
were resolved in review on 2026-02-11 (see changelog).

## Hard requirements (must)

| # | Requirement | Rationale |
|---|-------------|-----------|
| H1 | Deterministic simulation with seeded RNG | Roguelite fairness, replay tests, offline math (rules/02, rules/03) |
| H2 | Bounded-time offline catch-up (e.g. 8h in < 1s), including generation of the full offline event timeline (03-idle-loop.md) | Pillar 1 + full-timeline report |
| H3 | Runs on Windows and Linux (the developer's machines); no distribution platform requirement in phase 1 (personal project — resolved 2026-02-11) | Dev targets |
| H4 | Serializable, versioned save format with forward migration saves; human-readable **JSON, gzipped on disk** (resolved 2026-02-11; LLM-debuggable, diffable, moddable-by-hand) | Long-lived idle game |
| H5 | Data-driven content tables editable without recompilation | Balance iteration speed |
| H6 | Grid-tile isometric rendering with **crowd-scale budgets: late-game hordes are a design goal** (uncapped-feeling goblin crowds); requires batched/instanced rendering and/or spatial streaming; concrete frame-rate acceptance test at 300 animating units on mid-range hardware as the floor (`PLACEHOLDER` exact hardware profile) | Horde fantasy + RTS-like scenes (resolved 2026-02-11) |
| H7 | Simulation layer must be testable headlessly (no window/rendering needed) | rules/03 test categories 1–5 |
| H8 | Cross-platform CI-capable test runner, single-command execution, on Windows and Linux | rules/03; H3 |
| H9 | Combat "plan, then watch": run resolution must be fully non-interactive (resolvable at any speed/skip) | Resolved 2026-02-11; keeps combat inside the deterministic sim |
| H10 | In-game plan UI: drawing routes and contingency orders on the iso grid; needs a real 2D/UI tooling story, not just rendering | Run system R1 |
| H11 | Modularity per `06-architecture-modularity.md`: sim ring is an engine-free, self-contained package; dependency direction downward only; stack must express (or at least not undermine) these boundaries | Architecture doc M1–M6 |

## Soft requirements (should)

| # | Requirement | Rationale |
|---|-------------|-----------|
| S1 | Hot-reloadable data tables | Balance iteration |
| S2 | Local-first: no server required for core loop | Trivially satisfied by design; personal project has no backend |
| S3 | Development velocity: how quickly you + LLMs can iterate on gameplay (language ergonomics with LLM training-data depth, engine iteration loop, data tooling) | Solo + LLM-assisted personal project — resolved 2026-02-11 |
| S4 | Deployment story: build runs on the dev machines with one command; packaging polish deferred | Phase 1 scope |

## Explicit non-requirements (Phase 1)

- Distribution platforms (Steam/itch), achievements, workshop, cloud saves.
- Modding support (revisit after the game is fun).
- Music and audio middleware (SFX can be revisited later; no engine choice
  may hinge on mid-tier audio features).
- Multiplayer, console ports, macOS, mobile.

## Risks to validate during stack selection

- R1: Engines with float-based main-loop physics may fight H1
  (determinism). Mitigation: integer/fixed-point core tick math.
- R2: Scripted engines (e.g. interpreted languages) must still meet H2; catch-up
  math must not be n-iteration per second of absence.
- R3: Horde rendering (H6) — does the stack offer batched/instanced 2D-isometric
  sprite rendering or a clean path to it?
- R4: Linux toolchain quality for the chosen stack (H8) — Windows-only
  middleware is a disqualifier, not a penalty.
- R5: Off-the-shelf pathfinding (A*/navmesh/jump-point) or smooth integration
  of a library — writing pathfinding from scratch is acceptable but weighs.

## Decision inputs — resolved 2026-02-11

1. Target platforms: **Windows + Linux dev machines; no distribution in phase 1** (game is for the developer first).
2. Art style & asset source: **placeholder programmer art first**, custom art later.
3. Team: **solo developer with LLM assistance**.
4. Save format: **readable JSON, gzipped**.
5. Crowd budget: **horde fantasy** — 300 animating units is the floor, uncapped-feeling crowds are a design goal.
6. **Developer profile** (resolved 2026-02-11): advanced programmer —
   test automation engineer by trade (strongly aligned with rules/03),
   experienced with Java (+ Spring Boot), JavaScript/HTML/CSS, Python, low-code
   (Mendix); hobby/jam game dev experience; has tried Godot, Unity/Unreal/GameMaker,
   web game libraries, and built a small custom engine. **Open to learning**
   new languages/engines when they clearly fit the game — comfort zone is not
   a constraint, but prior Java/web depth must score as a real asset.
7. Language split (from architecture doc A1): **one language for sim + shell is
   the default preference**; polyglot split only wins on demonstrated outsized
   benefit; fixed timestep with save-constant tick rate (60 Hz default → 30 Hz option).

## Changelog

- 2026-02-11 — Second review draft from stack-readiness pass: H2 expanded
  (timeline emission), H4 pinned to JSON, H6 hardened to horde budgets, H10
  added (plan UI), S3 redefined as dev velocity, non-requirements list rebuilt
  (no distribution/modding/music phase 1), R3/R5 updated.
- 2026-02-11 — Review draft: placeholders resolved (H3 Windows+Linux; decision
  inputs solo / placeholder art; H9 added from concept resolution; R3 demoted,
  R4 added).
- 2026-02-11 — Initial draft.

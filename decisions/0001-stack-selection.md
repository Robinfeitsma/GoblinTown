---
title: DR-0001 — Stack Selection (Language & Engine)
status: approved
updated: 2026-02-11
---

# DR-0001 — Stack Selection (Language & Engine)

- **Date:** 2026-02-11
- **Status:** approved (user's explicit selection)
- **Supersedes:** none

## Context

All seven design documents approved. Binding constraints: determinism (H1),
bounded offline catch-up + full timeline (H2), W+L dev machines (H3), readable
JSON saves (H4), horde-scale batched rendering (H6), headless sim testing
(H7), strict modularity with the engine as slave (H11/M6), solo dev + LLM
velocity (S3), developer profile: advanced test automation engineer, strong
vanilla Java (+ Spring Boot), JS/HTML/CSS, Python; hobby game jams; open to
learning. Full scoring: `docs/07-stack-comparison.md`.

## Options considered

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| Godot 4 + GDScript | Best raw iteration velocity; best UI tooling (H10); horde rendering solved | Float-first language → determinism discipline weaker; long-term typing maturity | Recommended by agent |
| **libGDX + Java** | **Dev's strongest language; headless JUnit paradise (H7/H8); engine-as-slave designed into libGDX's backend abstraction; strongest typing longevity** | **No editor UI tooling; more hand-rolling (scene2d, atlases, tooling); slower feature velocity** | **CHOSEN by user** |
| Godot 4 + C# | Typed + full tooling | Assembly-reload pauses; .NET export nuances | Safe middle |
| Unity 6 + C# | Best raw rendering; huge ecosystem | Engine-state ownership pulls against M6; glue for JSON saves | Rejected |
| Bevy + Rust | Purest determinism posture | First-time Rust sole project; weak UI tooling | Rejected for v1 |
| LÖVE + Lua | Simple code-first | Catch-up/horde perf risks; weakest skill transfer | Rejected |

## Decision

**libGDX + Java** — selected by the user directly, overriding the agent's
Godot/GDScript recommendation. The user's call matches the second-ranked
stack (79/90) and the best hard-requirement score (51/55, tied with Bevy).
The deciding weights are the developer profile fit: Java mastery (decision
input #6), test-automation-first workflow mirroring rules/03, and typed
longevity for a long-lived, endlessly-deep project. The spike checklist from
the original recommendation converts from *gate* to *first validation tasks*
(a determinism-focused codebase gives these for free via the first tests):

1. Integer (long, fixed-point) tick economy determinism — covered by
   rules/03 category 1 tests from the first commit (JVM trivially passes).
2. Timeline-emitting offline catch-up under a step budget — rules/03
   category 2 tests.
3. Horde rendering fixture: 300+ animated isometric sprites via SpriteBatch
   at 60fps (`PLACEHOLDER` acceptance test lands in the render module).
   **Result 2026-11-22: PASS.** `core/HordeRenderFixture` (320 sprites, one
   SpriteBatch) on dev hardware locks at 60fps vsync — avg 16.67ms,
   worst 16.7–25.2ms across 30× 1s windows (worst frames during warmup).
   Boot also smoke-tested headless (`core` BootSmokeTest, 100+ frames via a
   GL shim, speed not measured). Timeline: `--assign gatherer=4 --ticks
   1668000 --catch-up 3600 --timeline` covers task 1–2 in CI.
4. Plan-overlay drawing + hit testing — part of the first plan UI task.

## Rationale

The engine-as-slave rule (M6) maps natively onto libGDX: the framework is
already an abstraction over backends — `sim` can be a Gradle module with **no
gdx dependency at all** (compiler-enforced M1/M2), `headless` and `lwjgl3`
are just two backends over the same sim. UI tooling sacrifice (H10) is
mitigated: route/plan drawing is custom canvas work on any stack, and
scene2d covers the widget-y parts (reports, menus, loadouts).

## Consequences

- Gradle multi-module project (see DR-0002): modules `sim` (pure, gdx-free),
  `core` (gdx glue), `lwjgl3` (desktop launcher), `headless` (CLI launcher).
- Java 17+ (LTS), records for immutable data, sealed interfaces for
  commands/events, `long` fixed-point math (no float/double in `sim` —
  enforced at review + determinism tests).
- `rules/02-coding.md` and `rules/03-testing.md` §10-Language-specific are
  filled as part of this decision's execution.
- JSON via Jackson in `sim`/`core` (readable, gzipped saves, H4).
- Project group/package base: `dev.goblintown` (changeable via DR if the user
  prefers).
- Bootstrap requirements for dev machines: JDK 17+, Gradle (see repository
  README → *Building from scratch*).

## Changelog

- 2026-02-11 — Approved: user selected libGDX + Java, overriding the
  Godot/GDScript recommendation; spike checklist converted to first
  validation tasks; consequences updated for the JVM path.
- 2026-02-11 — Draft: recommendation (Godot 4 + GDScript) conditional on
  spike; decision tree covers spike outcomes.

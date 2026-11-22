---
title: Stack Comparison — 6 Candidates
status: approved
updated: 2026-02-11
supersedes:
---

# Stack Comparison — 6 Candidates

Scored against `01-technical-requirements.md` H1–H11, S1–S4, and the
developer profile (decision inputs #6–#7). Scores 1 (fails) – 5 (ideal).
Evidence notes for scores ≤ 2 and ≥ 4 are cited inline in notes. Scores are
design-analysis based (no benchmarks run); the top 2 face a prototype spike
before DR-0001 approval (see DR-0001).

Candidates (all derived from requirements + dev profile):

1. **Godot 4 + GDScript** — engine tried by dev; Python-like = Python fluency transfers
2. **Godot 4 + C#** — same engine, typed language
3. **Unity 6 + C#** — industry default
4. **Bevy + Rust** — purist sim performance playground
5. **libGDX + Java** — dev's strongest language; headless-sim-friendly JVM
6. **LÖVE 11 + Lua** — lightweight code-first favorite of idle-game modders

Rejected without full scoring (evidence): Phaser/TypeScript (web stack: desktop
packaging = Electron-class weight, 300+ sprite loops on GC'd canvas are riskier
than native batching; save-file handling clunky — H4/H6) · custom engine from
scratch (dev has mini-engine experience, but solo scope + S3 velocity forbid
rebuilding renderer/tooling; violates M6 spirit: the engine should be a
commodity).

## Scores

| Req | Requirement | Godot/GD | Godot/C# | Unity/C# | Bevy/Rust | libGDX/Java | LÖVE/Lua |
|-----|-------------|----------|----------|----------|-----------|-------------|----------|
| H1 | Deterministic sim | 3¹ | 4 | 4 | 5 | 5 | 3¹ |
| H2 | Bounded offline catch-up + timeline | 4 | 4 | 4 | 5 | 5 | 3² |
| H3 | Windows + Linux, no distribution | 5 | 5 | 5 | 5 | 5 | 5 |
| H4 | Readable JSON saves (gzipped) | 5 | 4 | 3³ | 4 | 5 | 4 |
| H5 | Data-driven tables | 5 | 5 | 4 | 4 | 4 | 4 |
| H6 | Horde budget (300 units, instancing) | 4⁴ | 4⁴ | 5 | 4 | 4⁴ | 3⁵ |
| H7 | Headless sim testing | 5 | 5 | 4 | 5 | 5 | 5 |
| H8 | CLI test runners (W+L) | 4 | 5 | 5 | 5 | 5 | 4 |
| H9 | Non-interactive run resolution | 5 | 5 | 5 | 5 | 5 | 5 |
| H10 | Plan-UI tooling (2D draw + widgets) | 5 | 5 | 4 | 2⁶ | 3⁷ | 3 |
| H11 | Modularity M1–M6 (engine as slave) | 4 | 4 | 3⁸ | 5 | 5 | 4 |
| S1 | Hot-reloadable data | 4 | 3 | 4 | 5 | 3 | 5 |
| S3 | Dev velocity (solo + LLM) | 5⁹ | 4¹⁰ | 4 | 2¹¹ | 4 | 3 |
| S4 | One-command build on dev machines | 5 | 4 | 4 | 4 | 4 | 5 |
| — | Dev fit: languages (Java/JS/Py/C) | 4¹² | 4 | 4 | 1 | 5 | 3 |
| — | Dev fit: engines tried | 4 | 4 | 3 | 1 | 2 | 2 |
| — | Dev fit: learning appetite | 4 | 4 | 3 | 5 | 5 | 3 |
| — | Longevity / ecosystem judgment | 4 | 4 | 5 | 4 | 5 | 3 |

### Totals

| | Godot/GD | Godot/C# | Unity/C# | Bevy/Rust | libGDX/Java | LÖVE/Lua |
|---|---|---|---|---|---|---|
| Hard total (H1–H11, /55) | **49** | **49** | 47 | **51** | **51** | 43 |
| Soft + fit (7 rows, /35) | **31** | 28 | 27 | 23 | 28 | 23 |
| Grand (/90) | **80** | 77 | 74 | 74 | **79** | 66 |

## Evidence notes

1. H1=3 (GDScript/Lua): both languages are float-first; strict integer
   discipline (int64 fixed-point) is achievable but easy to drift into float
   creep. Flagged as spike-testable, not disqualifying.
2. H2=3 (Lua): interpreter JIT-less number crunching for huge catch-up sums
   with per-interval event rows is a real risk.
3. H4=3 (Unity): JsonUtility is weak; Newtonsoft works, but the save pipeline
   is more glue than Godot/libGDX.
4. H6=4 (Godot, libGDX): 2D batched sprite rendering handles hundreds–thousands
   of sprites easily (Godot MultiMesh/CanvasItem culling; libGDX SpriteBatch).
   Not 5 because iso depth-sorting + animation-atlas management is your code.
5. H6=3 (Lua): canvas-style immediate draw in Lua with large counts plus GC
   pauses; workable, not comfortable.
6. H10=2 (Bevy): bevy_ui is young, no editor tooling; plan-drawing menus and
   reports cost real time.
7. H10=3 (libGDX): scene2d is dated but workable; route drawing is custom
   canvas work everywhere anyway.
8. H11=3 (Unity): the engine constantly pulls toward GameObject state ownership
   (M6 violation — its physics/UI/components want to BE the game state);
   possible to resist (pure C# sim assembly), costs discipline.
9. S3=5 (GDScript): zero compile cycle, live scene iteration, huge LLM
   familiarity, gentlest tail for LLM-assisted feature production.
10. S3=4 (Godot/C#): typed but assembly-reload iteration pauses.
11. S3=2 (Rust): borrow-checker iteration cost for a first-time Rust dev is
    real velocity loss despite excellent LLM Rust support.
12. Dev-fit languages (Godot/GD): Python-like syntax leverages Python fluency
    directly.

## Totals judgment

- Top group: **Godot/GDScript (80)**, **libGDX/Java (79)** — a genuine tie
  broken by posture: Godot wins S3 velocity + H10 tooling; libGDX wins
  dev-fit languages +ittesting ergonomics (JUnit/Gradle) and typing longevity.
- Godot/C# (77) is the "safe middle": typed, tooling-rich, slightly slower
  iteration.
- The riskiest requirements (H1 determinism in GDScript, H6 hordes) go to a
  prototype spike before final user sign-off — see `decisions/0001-stack-selection.md`.

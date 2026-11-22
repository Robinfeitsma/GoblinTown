# AGENTS.md — GoblinTown

GoblinTown is an idle, roguelite, isometric RTS-like RPG set in a vertical
underworld. This **is** the coding repository (libGDX + Java, per DR-0001)
and contains its design documents side by side with the code: the design
docs moved here from a pre-code workspace on 2026-02-11 (DR-0002). Design
and code share one git history from now on.

## Mandatory workflow

1. **Rules first.** Before doing any work, read the rules files that match the
   task. They are binding; if a task conflicts with a rule, stop and raise the
   conflict with the user instead of silently deviating.
   - Any task → `rules/00-core-principles.md`
   - Writing or editing a design doc → `rules/01-design-documents.md`
   - Writing or reviewing code → `rules/02-coding.md`
   - Writing or running tests → `rules/03-testing.md`
   - Choosing a language, engine or major tool → `rules/04-stack-selection.md`
2. **Template use.** New documents must be created from the matching template
   in `docs/templates/` — never from scratch.
3. **Doc before code.** No feature is implemented unless a design document for
   it exists and is in `Status: approved`.
4. **Update the index.** Whenever a document is added, moved or archived,
   update the index in `README.md`.

## Document conventions

- All content is plain GitHub-flavored Markdown. No other formats.
- File names: lowercase, hyphen-separated, zero-padded numeric prefixes fix
  the reading order, e.g. `01-technical-requirements.md`.
- Every document carries a YAML frontmatter block with `title`, `status`
  (`draft | review | approved | superseded`), `updated` (ISO date) and
  `supersedes` (path of the replaced doc, if any).
- Decisions with consequences (engine choice, save format, persistence,
  performance budgets) are recorded as Decision Records in `decisions/` using
  `docs/templates/decision-record-template.md`.

## Repository layout

```
GoblinTown/                # repository root
├── AGENTS.md              # This file: LLM entry point
├── README.md              # Project index + build instructions
├── rules/                 # Binding behavioral rules for any LLM working here
├── docs/                  # Design documents + templates (source of truth for behavior)
├── decisions/             # Decision Records (ADR-style, numbered)
├── archive/               # Superseded documents (never deleted)
├── data/                  # Content tables: plain JSON, engine-agnostic (H5)
├── sim/                   # Pure deterministic simulation — NO libGDX (M1/M2)
├── core/                  # libGDX glue: rendering, input→commands, UI
├── headless/              # CLI launcher: headless sim runs, save tools
└── lwjgl3/                # Desktop launcher (Windows/Linux)
```

The module dependency graph in `build.gradle` enforces the architecture:
`sim` cannot import the engine because it does not depend on it. Never add
engine dependencies to `sim` (rules/02 §10).

## Current phase

**Phase 1 — Simulation core first (tick-economy skeleton LANDED).**

- `sim` skeleton is green: two producer roles, stockpile with caps, role
  XP/level-ups, closed-form offline catch-up (H2), save codec v1 (canonical
  JSON, version-stamped) — `./gradlew test`, 22+ tests, 0 skipped.
- Determinism gates from `rules/03 §Phase-1 test priorities` are OPEN:
  two-run rule across golden seeds
  (`sim/src/test/resources/golden-seeds.txt`) and catch-up ==
  tick-by-tick (exact), including event-timeline byte-identity (H1/H2).
- A self-enforcing no-float gate (`NoFloatingPointInSimTest`) guards the
  Q32.32 convention (DR-0003).
- **Pending: DR-0003 approval** (user confirmation) — it unblocks the first
  real balance numbers.
- Next: balance-doc phase (replace PLACEHOLDER numbers), then the Phase-1
  render fixture (300+ sprites, `core`, DR-0001 validation task 3), then
  the plan-UI overlay.

---
title: DR-0002 — Repository Migration (Design Docs → Code Repo)
status: approved
updated: 2026-02-11
---

# DR-0002 — Repository Migration (Design Docs → Code Repo)

- **Date:** 2026-02-11
- **Status:** approved (executed as part of this record)
- **Supersedes:** none

## Context

DR-0001 approved the stack (libGDX + Java). The pre-code design workspace
`~/Documents/GoblinTownGameDesign/` was built with fully relative paths so it
could move wholesale. Its own `README.md` documented the 6-step migration
checklist; this record executes it.

## Decision

The design workspace lives at the **root of the coding repository**
`~/Documents/GoblinTown/`, next to the code, from the repo's first commit:

```
GoblinTown/                # repository root
├── AGENTS.md, README.md   # migrated, updated for phase 1
├── rules/, docs/, decisions/, archive/
├── data/                  # content tables (plain JSON)
├── sim/                   # pure sim — no libGDX dependency (compiler-enforced)
├── core/                  # libGDX glue
├── headless/              # CLI sim launcher
└── lwjgl3/                # desktop launcher
```

## Steps executed

1. Repository created; `git init` (branch `main`).
2. Gradle multi-module skeleton per DR-0001 consequences: root build +
   `sim` (Jackson only), `core` (gdx), `headless` (gdx-backend-headless),
   `lwjgl3` (lwjgl3 backend + natives). Launcher skeletons and a disabled
   determinism smoke test in place.
3. Workspace moved in: `AGENTS.md`, `README.md`, `rules/`, `docs/`,
   `decisions/`, `archive/`.
4. `AGENTS.md` updated: no longer a pre-code workspace; layout extends to
   code modules; phase 1 stated.
5. `rules/02-coding.md` and `rules/03-testing.md` `§10-Language-specific`
   filled (mandatory consequence of DR-0001).
6. README rewritten: document index, decisions, build/bootstrap instructions.

## Consequences

- Design docs and code share one git history from the first commit; all doc
  rules (frontmatter statuses, archive flow) keep applying inside the repo.
- `rules/02-coding.md` layout section now governs the Gradle modules;
  adding engine dependencies to `sim` is a rules violation, not a refactor.
- The module layout was chosen by DR-0001/this record; changing module names
  or graph needs a new Decision Record.
- Bootstrap notes (JDK 17, Gradle wrapper generation) live in `README.md`.

## Changelog

- 2026-02-11 — Approved & executed: repo created, skeleton scaffolded,
  workspace migrated, rules/AGENTS/README updated.

# GoblinTown

An idle, roguelite, isometric RTS-like RPG set in a vertical underground
world: start as a lone hero goblin at ground zero, found your tribe, conquer
the strata, ascend to the surface. Built with **libGDX + Java** (DR-0001).

**Start here if you are an LLM:** read [`AGENTS.md`](AGENTS.md) first — it is
loaded automatically by Vibe and links every binding rule in `rules/`.

## Document index (behavior = these documents)

| Document | Status | What it is |
|---|---|---|
| [`docs/00-game-concept.md`](docs/00-game-concept.md) | approved | Genre pillars, hero-goblin identity, resolved decisions, glossary |
| [`docs/01-technical-requirements.md`](docs/01-technical-requirements.md) | approved | Hard/soft requirements H1–H11 |
| [`docs/02-core-loop.md`](docs/02-core-loop.md) | approved | Founding-wipe cycle, two persistence layers, dynamic wipe pacing |
| [`docs/03-idle-loop.md`](docs/03-idle-loop.md) | approved | Idle production model, persistent assignments, return report |
| [`docs/04-run-system.md`](docs/04-run-system.md) | approved | Dungeons, autonomous plan-then-watch combat, outcome ladder |
| [`docs/05-world-environment.md`](docs/05-world-environment.md) | approved | Vertical underworld strata, conquest arc, surface = prestige/NG+/endless |
| [`docs/06-architecture-modularity.md`](docs/06-architecture-modularity.md) | approved | Three-ring architecture, 8-module map, stack demands |
| [`docs/07-stack-comparison.md`](docs/07-stack-comparison.md) | approved (record) | 6 candidate stacks scored; the input to DR-0001 |

### Rules (binding for any LLM working here)

| Rule | Governs |
|---|---|
| [`rules/00-core-principles.md`](rules/00-core-principles.md) | Source-of-truth, explicitness, failure conduct |
| [`rules/01-design-documents.md`](rules/01-design-documents.md) | When and how design documents are written |
| [`rules/02-coding.md`](rules/02-coding.md) | Coding rules — language-specific §10 filled per DR-0001 |
| [`rules/03-testing.md`](rules/03-testing.md) | Testing rules — language-specific §10 filled per DR-0001 |
| [`rules/04-stack-selection.md`](rules/04-stack-selection.md) | The process for choosing language/engine (executed; kept for future selections) |

### Decisions

| Decision | Status | Content |
|---|---|---|
| [`decisions/0001-stack-selection.md`](decisions/0001-stack-selection.md) | approved | Stack = **libGDX + Java** (user selection); first validation tasks |
| [`decisions/0002-repository-migration.md`](decisions/0002-repository-migration.md) | approved | Design workspace migrated into this repo; module layout |

Templates: [`docs/templates/`](docs/templates/) — never write a new document
from scratch.

## Repository layout

```
GoblinTown/
├── AGENTS.md              # LLM entry point (auto-loaded by Vibe)
├── rules/                 # Binding behavioral rules
├── docs/                  # Design documents + templates
├── decisions/             # Decision Records
├── archive/               # Superseded documents (never deleted)
├── data/                  # Content tables: plain JSON (H5)
├── sim/                   # Pure deterministic simulation — NO libGDX (M1/M2)
├── core/                  # libGDX glue: rendering, input→commands, UI
├── headless/              # CLI launcher: headless sim runs
└── lwjgl3/                # Desktop launcher (Windows/Linux)
```

Architecture is compiler-enforced: `sim` has no gdx dependency in the Gradle
graph, so the simulation physically cannot reach the engine (H11, M1–M6).

## Building from scratch (bootstrap on a fresh dev machine)

1. Install **JDK 17+ (LTS)** — `apt install openjdk-17-jdk` (Linux) or a
   Temurin/Adoptium MSI (Windows).
2. Get the Gradle wrapper once: with Gradle 8.x installed run
   `gradle wrapper --gradle-version 8.10` in the repo root — or let an IDE
   (IntelliJ/Eclipse) import the project and generate it. The wrapper's
   `gradlew`/`gradlew.bat` is committed from then on.
3. `./gradlew test` — runs the whole test suite (sim determinism included).
4. `./gradlew :lwjgl3:run` — launches the (skeleton) game window.
5. `./gradlew :headless:run` — CLI simulator (headless, no window).

## Repo housekeeping

- Documents are archived in `archive/`, never deleted; statuses move
  `draft → review → approved → superseded`.
- Design docs and code share one git history from 2026-02-11 (DR-0002).

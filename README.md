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
| [`docs/08-economy-balance.md`](docs/08-economy-balance.md) | approved | Phase-1 pacing targets, scale chain, approved caps/rates/XP curve; OQ-1/OQ-2 resolved |
| [`docs/09-buildings-construction.md`](docs/09-buildings-construction.md) | approved | Buildings model (abstract slots 2a), construction mechanic, founding build order, `buildings.json` schema |

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
| [`decisions/0003-fixed-point-convention.md`](decisions/0003-fixed-point-convention.md) | approved | Sim arithmetic = Q32.32 in `int64` |

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
2. The Gradle wrapper (`gradlew`/`gradlew.bat`) is **already committed** at
   Gradle 9.9.2 — `./gradlew` downloads its own Gradle, so no local Gradle
   install is needed. Note: Gradle 8.x cannot run on a JDK 25 runtime; 9.9.2
   can. Gradle needs a JDK with `javac` (a full JDK, not a JRE); if multiple
   JDKs are present, point Gradle at one with `org.gradle.java.home=…` in
   `~/.gradle/gradle.properties` (machine-local, never committed).
3. `./gradlew test` — runs the whole test suite (sim determinism included).
4. `./gradlew :lwjgl3:run` — launches the (skeleton) game window.
5. `./gradlew :lwjgl3:run --args="--fixture horde"` — horde render fixture:
   300+ animated sprites, prints frame-budget stats (DR-0001 validation 3).
   A headless boot smoke of the same fixture runs in `./gradlew :core:test`
   (BootSmokeTest: boots + renders 100 headless frames through a GL shim; it
   measures nothing about speed — visual correctness and the 60fps budget
   need this on-GPU run on your hardware).
6. `./gradlew :headless:run --args="--seed 20260211 --assign gatherer=4 --ticks 60000 --catch-up 1668000 --timeline"` —
   headless reproduction tool: any state is reproducible from seed +
   command list (H1/H2, rules/03 §10). Options: `--balance <file>`,
   `--seed <n>`, `--load <save.gz>`, `--assign <role=count>` (repeatable),
   `--ticks <n>`, `--catch-up <n>`, `--save <file>`, `--timeline`.

## Repo housekeeping

- Documents are archived in `archive/`, never deleted; statuses move
  `draft → review → approved → superseded`.
- Design docs and code share one git history from 2026-02-11 (DR-0002).

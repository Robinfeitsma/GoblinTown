---
title: Project State (session-to-session continuity)
updated: 2026-11-22
status: living document
---

# PROJECT-STATE.md

This file is the resumption point for any LLM session (or human) picking the
project up. **Read it first**, then `AGENTS.md` → the rules → the docs index.
Update it at the END of every working session (and after every approved
decision). Everything durable is git-committed; this file only names facts
and pointers, never replaces the docs themselves.

## Where the project stands (as of 2026-11-22, commit 995eba9)

| Phase | Status |
|---|---|
| Design docs 00–07 (concept, requirements, loops, runs, world, architecture, stack) | approved |
| docs/08 economy balance (caps 800/900/600, gatherer 3 wood/s, miner 2 stone/s, XP 100/50 + 120/60) | approved + wired in `data/economy.json` |
| docs/09 buildings & construction (abstract slots Phase 2a, upfront cost, full refund on cancel) | approved + implemented in `:sim` |
| docs/10 UI shell & plan overlay (screens, input→command boundary, `IsoGrid`, plan-overlay fixture) | approved + implemented in `:core` |
| Stack (DR-0001): libGDX + Java 17 release target, Q32.32 sim math (DR-0003) | approved; validations 1–3 PASS (recorded in decisions/0001) |
| GitHub | https://github.com/Robinfeitsma/GoblinTown (public), branch `main`, REMOTE MANDATORY: push after every commit |

Tests: 57 green repo-wide (`./gradlew check`). Golden seeds in
`sim/src/test/resources/golden-seeds.txt` (4 seeds; every PR extends it).

## Awaiting user action (NOT agent work)

- **DR-0001 validation 4 on-GPU record**: user runs
  `./gradlew :lwjgl3:run --args="--fixture overlay"`, confirms visuals, then
  the result is written into `decisions/0001-stack-selection.md` like
  validations 1–3.
- **foundingSupplies ratification** (optional): `data/economy.json` has
  `"foundingSupplies": {"food":0,"wood":450,"stone":0}` — agent-introduced
  deadlock-breaker (first building costs 450 wood); a stand-in for founding
  map loot until world-gen. Tunable any time, one line.

## Environment (dev machine: robin-MS-7C56, Linux)

- Project now lives at **`/home/robin/IdeaProjects/GoblinTown`** (moved from
  Documents 2026-11-22; IntelliJ: File → Open → that dir, Gradle import).
- JDK: Temurin 21 at `~/jdks/jdk-21.0.13.1+2`; wired to Gradle via
  `~/.gradle/gradle.properties` `org.gradle.java.home`. `javap`/`jar` need
  the full JDK bin path (not on PATH).
- Gradle wrapper 9.9.2 is committed; `./gradlew` self-provisions.
- GitHub auth: token is `export GH_API_MIS=…` in `~/.bashrc` (classic PAT,
  user `Robinfeitsma`). It is set only in INTERACTIVE shells — pushes must
  go through a one-shot credential helper, nothing persisted on disk:
  `bash -ic 'cd ~/IdeaProjects/GoblinTown && git -c credential.helper="!f() { echo username=x-access-token; echo password=$GH_API_MIS; }; f" push'`

## Sim conventions that must not need re-learning (see rules/02, DR-0003)

- No float/double in `:sim`; Q32.32 in `Fixed`; per-tick rates truncate ONCE
  at load, so expected values in tests compute via `amountPerTickFixed()`.
- Canonical save JSON sorts keys alphabetically (state before version).
- Java 17: no pattern-matching switch; sealed same-file hierarchies without
  `permits`; `instanceof` patterns are fine.
- Expected test values derive from design docs + the real `data/*.json`
  tables on the test classpath — synthetic tables only for edge cases.
- Two-run determinism rule (rules/03): a flaky hash diff is a P0 bug, never
  retried.
- dates in doc frontmatter/changelogs: "now" per user context = 2026-11-22.

## Deferred (deliberately, per resolved docs — do not "fix" silently)

- `town-tiers.json` — tier CONTENT comes with the tiers phase (tier itself
  is already derived from `grantsTier` of completed buildings).
- Builder population is unbounded — bounded by the recruitment phase
  (one-goblin-one-job cross-check also deferred there, docs/09).
- Grid placement of buildings — world-gen phase (docs/09 Phase 2a abstract
  slots were the approved resolution).
- Run-system sim (docs/04 plan data model, combat) — its own doc phase;
  `DungeonGraphFixture` in `:core` is THROWAWAY (docs/10 OQ-1) and gets
  replaced by real plan data.
- Real art pass — current screens use procedural Pixmap glyphs.

## Next steps (agreed order; each starts with a design doc per rules/01)

1. Record validation 4 in DR-0001 (after the user's GPU run — see above).
2. **Return-report UI** (docs/03 §Return report: headline + full paginated
   timeline + call to action) — design doc, then `:core` + `:headless` work.
3. Construction/building screen (docs/09: build menu, sites, builder pills).
4. Then per docs/02: recruitment/personnel, town tiers content, world-gen.

## Session log (one line per session, newest first)

- 2026-11-22 (session 3): repo → GitHub (Robinfeitsma/GoblinTown), repo moved
  to ~/IdeaProjects/GoblinTown; docs/10 drafted + approved (synthetic-first,
  click-to-append, scene2d) + implemented (Phase-UI-1, 57 tests green).
- 2026-11-22 (session 2): docs/08+09 approved + buildings implemented in sim,
  save v2 + migration, headless CLI buildings support, 48 tests.
- 2026-11-22 (session 1): repo created (docs+rules+Gradle sim skeleton),
  docs 00–07 approved, DR-0001/2/3, horde fixture, Phase-1 economy.

---
title: Testing Rules (Language-Neutral)
status: approved
updated: 2026-02-11
---

# Testing Rules

> **Phase 0 note:** no language or engine is chosen yet. These rules bind and
> any chosen stack must be able to satisfy them. Concrete tooling is named only
> after Decision Record `0001`.

## Test categories (all mandatory, in priority order)

1. **Simulation determinism tests** — run the simulation from a fixed seed and
   command list, hash the resulting state; the hash must be stable. These
   protect the idle/roguelite contract above everything else.
2. **Offline catch-up tests** — simulate the same elapsed duration twice:
   once as fast-forward, once pre-computed; results must match within the
   defined tolerance (target: exact).
3. **Save round-trip tests** — serialize, deserialize, re-serialize; the
   output must be identical. Test saves from every released save version can
   still be loaded (migration tests).
4. **Balance tests** — property-based tests over the balance tables: e.g.
   costs are monotonically increasing, no tier makes an earlier upgrade
   strictly worthless, a full run's power curve stays inside the target range
   defined in the design docs.
5. **Unit tests** — pure logic: formulas, level curves, drop tables.
6. **Smoke test** — the game boots and reaches the first interactive frame.

## General rules

- **Design docs define expected values.** When writing a gameplay test, the
  expected value comes from the design document; if the doc lacks the number,
  the test cannot be written — fix the doc first.
- Tests run from the command line with a single command, and that command is
  recorded in the coding-folder README.
- Every bug fix ships with the regression test that would have caught it.
- Determinism tests must run in CI-like conditions on every commit once the
  coding folder exists.
- No test may depend on wall-clock time, network, or machine speed; time in
  tests is injected.
- Coverage targets are not a number worship, but the simulation core (economy,
  progression, save/load) should approach full coverage — it is the part the
  whole project stands on.

## 10-Language-specific

Filled per DR-0001 (approved 2026-02-11): **libGDX + Java**.

### Test stack

- **JUnit 5** (+ AssertJ assertions), in every module; the `sim` module's
  tests carry the determinism core (categories 1–5) — they run with **zero
  gdx on the classpath**, which is itself the ongoing H7 enforcement.
- Single command: `./gradlew test` (all modules), `./gradlew :sim:test` for
  the simulation core. CI = `./gradlew check` on both Windows and Linux.
- Headless runs via the `headless` launcher (`./gradlew :headless:run`):
  simulate arbitrary tick counts / offline catch-up from command line — used
  to reproduce any reported state from its seed (H1).

### Determinism test conventions

- Golden seeds list (`sim/src/test/resources/golden-seeds.txt`): every merged
  PR extends it; a test loops all seeds, ticks N, hashes state, compares.
- Hash = canonical serialization of full sim state (stable field order) —
  effectively the save path double-testing itself (categories 1+3 intersect
  by design).
- Two-run rule: every determinism test runs the sim twice fresh and compares
  hashes; a flaky hash diff is a P0 bug, never a retry.
- Battle-log events are asserted structurally (types + ordering), not stringly.

### Phase-1 test priorities (first month)

1. `sim` cart skeleton with a tick, a producer, a stockpile — determinism + save round-trip tests green before any rendering exists.
2. Offline catch-up vs tick-by-tick equivalence test (category 2) — lands with the first idle math.
3. Horde render fixture (`core`, not `sim`): 300 animated sprites, frame budget measured on dev hardware — the DR-0001 validation task 3.

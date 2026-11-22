---
title: DR-0003 — Fixed-Point Convention (sim ring)
status: review
updated: 2026-11-22
---

# DR-0003 — Fixed-Point Convention (sim ring)

- **Date:** 2026-11-22
- **Status:** review (approval → user confirmation pending; needed before the
  first balance numbers land, per `rules/02-coding.md` §Java rules)
- **Supersedes:** none

## Context

DR-0001 mandates: "`long` fixed-point math (no float/double in `sim` —
enforced at review + determinism tests)" but does not fix the format. The
coding rules require the convention (bits per unit) to be fixed in a Decision
Record before the first balance numbers land. Phase 1 (tick economy skeleton,
`docs/03-idle-loop.md`) is the first code to carry per-tick rates, so the
convention must land now.

Constraints driving the choice:

- **H1 (determinism):** all arithmetic must be exact and identical across
  JVMs, platforms and runs — this rules out both `float`/`double` and any
  dependence on `Math.pow`/`strictfp` subtleties. Only `+ - * /` and shifts
  on `long`/`int` are used.
- **Idle genre (docs/02, 03):** stockpiles are integers (whole logs, stones,
  mushrooms), but *rates* are fractional (e.g. 6 wood/second at 60 ticks/s
  = 0.1/tick). Rates must be representable without accumulated drift.
- **Longevity (S3):** expected total lifetime production is unbounded
  (endless depths mode); overflow headroom in the *whole-unit* part matters.

## Options considered

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| `double` + rounding discipline | Trivial to write | Violates H1 outright; drift; banned | Rejected |
| **Q32.32 in `int64`** (32 integer bits / 32 fraction bits) | Exact `+`/`-`; `*` exact via 128-bit `Math.multiplyHigh` + shift (JDK 9+, integer-only); ~4.3 billion whole units of headroom (fit for any stockpile); fraction precision ≈ 2.3e-10 — far below one unit over any realistic horizon | Slightly more code (one helper class); JSON stores plain `long` raw values, which are less human-readable in saves | **CHOSEN** |
| Q48.16 | Easier mental math for coarse rates | Only 65k whole units — stockpile caps exceed that | Rejected |
| Custom 128-bit type | Unlimited headroom | Java has no `int128`; hand-rolled overflow-prone arithmetic for no phase-1 need | Rejected for v1 |
| Big decimals per tick | Exact | Allocation per tick, slow, serialization bloat | Rejected |

## Decision

**Q32.32 fixed point in `int64`**: one whole unit = `1L << 32`
(`0x1_0000_0000`). All amounts, rates and XP inside `sim` are `long` values
in this representation. Multiplication is `Fixed.mul` (128-bit intermediate
via `Math.multiplyHigh`, integer-only). Division by `long` is `long`
division after scaling; fraction bits never carry meaning across additions.
Truncation only ever happens on explicit division / conversion, is
deterministic, and is part of the sim contract.

Additionally, integer-unit values (worker counts, levels, tick numbers) are
plain `int`/`long` **not** fixed — fixed-point is used only where fractional
values exist (rates, XP pools with fractional accrual, future aura math).

Conversion helpers (`Fixed.fromUnits`, `Fixed.unitsFloor`) are the only
place the shift width appears; changing the convention later is a save
migration (v→v+1), never a logic edit.

## Rationale

- H1: exact integer ops everywhere; the same `long` math runs identically on
  both dev machines (H3) and on the JVM in general.
- H2 (offline catch-up): closed-form catch-up over linear production needs
  only one multiply — exact with 128-bit intermediates; no drift means
  tick-by-tick and closed-form paths agree *bit-for-bit*, which is what
  `rules/03` category 2 (exact tolerance) demands.
- H4 (readable saves): a stockpile of 37.0 wood serializes as
  `"stock": {"wood": 158913789952}` — raw but exact; the save layer documents
  this ("all sim amounts are Q32.32 raw `long`s; divide by 4294967296 to
  read whole units").
- S3 (LLM velocity): a single `Fixed` class with four operations is easy to
  keep consistent; a no-float rule check greps for `float|double` in `sim`.

## Consequences

- `sim/src/main/java/dev/goblintown/sim/Fixed.java` is the single authority
  for the convention; `SHIFT = 32` is referenced nowhere else.
- No `float`/`double` anywhere in `sim` (existing rule, now concretely
  enforceable by grep + review).
- Data tables in `data/` express rates in **whole units per second**
  (`unitsPerSecond`); the sim converts to per-tick fixed once at load:
  `per-tick = unitsPerSecond * ONE / 60` (`TICKS_PER_SECOND = 60` is a
  save-version constant per DR-0001; changing it is a migration).
- Overflow: 32 integer bits hold ±2,147,483,647 units — any balance or
  runtime amount beyond that is a data problem, guarded by validation
  (the `Balance` loader rejects caps/rates implying overflow at load).
- If the convention must change, a new DR supersedes this one and a save
  migration converts raw storage; no logic edits elsewhere.

## Changelog

- 2026-11-22 — Initial draft, submitted for review together with the Phase-1
  tick-economy skeleton it unblocks.

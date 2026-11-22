---
title: UI Shell & Plan Overlay (first UI phase)
status: approved
updated: 2026-11-22
placeholders: true
supersedes:
---

# UI Shell & Plan Overlay (first UI phase)

## Summary

The first UI phase lands the screens every later phase builds on: a screen
graph (founding view → town HUD → run view skeleton), the strict
input→command / snapshot-only-render boundary, and the **plan-overlay
fixture** — drawing zones/beacons on an isometric grid and hit-testing the
route the player draws. The overlay fixture is DR-0001 validation 4 and runs
against a *synthetic* dungeon graph: the run-system sim phase (docs/04) does
not exist yet, and the overlay's geometric core (projection, hit testing,
route order) must not wait on it. Everything the fixture proves transfers
unchanged to the real plan once docs/04's plan data model lands in `sim`.

## Links

- Depends on: [Run System](04-run-system.md) (plan = pure data, ordered) ·
  [Idle Loop](03-idle-loop.md) (return report, personnel)
- Related: [Architecture](06-architecture-modularity.md) (three rings, M6) ·
  [DR-0001](../decisions/0001-stack-selection.md) (validation task 4) ·
  [Stack](07-stack-comparison.md) (libGDX + Java)
- Rules: H1 (determinism never depends on UI), H5 (data-driven content), H9
  (comprehensible combat), M3 (command boundary), M6 (engine is a slave),
  RULES-03 (no wall-clock in tests)

## Player-facing behavior

| Trigger (what the player does) | State change (via boundary) | Observable outcome |
|---|---|---|
| Launches the skeleton build | none | founding view: isometric ground plane, standing hero goblin |
| Opens the overlay fixture (dev screen) | in-fixture only | synthetic dungeon graph drawn on the isometric grid |
| Clicks a zone node | route entry appended (fixture list) | node highlight + route line extends to it |
| Clicks an invalid/non-adjacent node | nothing appended | rejected-feedback (shake/red) — first UX surfacing of the plan gates |
| Clicks an existing route node | route truncated there (docs/04: branch fallbacks) | line redraws |
| Resizes the window | none | viewport letterboxes; overlay math maps through the same projection |

## Design

### Screen graph (Phase-UI-1 scope)

```
GoblinTownGame (Game)
├── FoundingView        — first render task: isometric ground plane + hero sprite
├── OverlayFixtureView  — dev-only screen: DR-0001 validation 4 (this doc)
└── TownHudView         — skeleton only in this phase: stockpile line + tier
                          read from a real SimEngine snapshot (no commands yet)
```

Navigation is dev-routed (a launch flag / key), not menu-driven — menus are a
later phase. Explicitly OUT of scope here: run view, return report UI,
construction UI, save/load screens. Each lands with its sim phase.

### The boundary (rules/02 §1, M3, M6 — binding)

- **Render reads**: every screen renders from `GameState` snapshots (or
  fixture-local synthetic state). No screen owns game truth.
- **Input writes**: every player input crossing into the sim must be an
  `Command` (existing `dev.goblintown.sim.command` records). In this phase:
  zero sim commands are issued by UI (fixture state is core-local) — but the
  plumbing (an input-to-command queue whose consumers are the sim only)
  exists from day one so later screens cannot grow shortcuts.
- **Presentation may use float/double** (positions, lerps, ease curves);
  nothing derived from them ever re-enters `sim` (DR-0003 scope is the sim
  ring).
- **Speed/skip are presentation-only** (docs/04): they may tick a *copy* or
  issue timed batches of sim ticks — never alter outcomes. Enforced later by
  rules/03 cat. 1 tests; the fixture already follows it.

### Isometric projection (the one transform, shared everywhere)

Standard 2:1 diamond ("iso tile"), pixel units `PLACEHOLDER`:

```
W = 128, H = 64                    // tile size px, PLACEHOLDER
screenX = originX + (col − row) · W/2
screenY = originY + (col + row) · H/2      // +y down (libGDX screen coords)
inverse:  col = ((x−originX)/(W/2) + (y−originY)/(H/2)) / 2
          row = ((y−originY)/(H/2) − (x−originX)/(W/2)) / 2
```

Used by: overlay fixture, founding view ground plane, and later the town HUD.
The transform lives in ONE class in `core` (`IsoGrid`), unit-tested against
its inverse for exact round-trips on grid coordinates (rules/03 cat. 5: UI
hit-testing is tested, with synthetic tables or fixtures per project layout).

### Plan overlay: drawing + hit testing (validation 4)

- Synthetic dungeon = seeded graph: nodes on integer grid cells, edges
  between neighbors; 1 start node + `PLACEHOLDER` branching (reuse the
  horde-fixture seeding approach — no sim involvement).
- **Nodes**: iso-diamond sprites; type = encounter / room / shrine / beacon /
  boss (visuals: tint + icon glyph for now, `PLACEHOLDER` art).
- **Route line**: polyline through node centers of the drawn plan; last
  segment animates toward the cursor (snap ≤ 1 tile, `PLACEHOLDER`).
- **Hit testing**: click → inverse transform → floor to cell; a node within
  Manhattan distance 0 of the cell (exact cell match) is "hit". Adjacency
  check falls back to graph edges (the route is a WALK on the graph).
- **Route data**: ordered list of node ids; truncation on re-click is the
  docs/04 branch-fallback behavior; the fixture exposes the list printed to
  console (text assertable in the headless test).

### Acceptance (DR-0001 validation 4)

1. Headless (automated, GL shim like BootSmokeTest): synthetic graph with a
   known seed → inject synthetic clicks at computed screen coordinates →
   assert hit-tested route list = expected ids, including one rejected click
   (non-adjacent) and one truncation.
2. On-GPU (manual, dev hardware): visuals + 60fps line/node redraw during a
   cursor-move stress (`PLACEHOLDER` budget: match horde fixture 16.67ms),
   RESULT recorded in DR-0001 like validations 1–3.

### Edge cases

- Resize / DPI: viewport letterboxes; `IsoGrid` re-bases on stage resize;
  hit tests always route through the live transform (never cached screen px).
- Click spam / re-entry: route mutation is idempotent-safe (append-or-truncate
  only); no input can produce a route not expressible as the docs/04 data.
- Interaction with runs: n/a yet (no run sim) — by design the fixture's route
  type is intentionally the same ORDERED-NODES shape docs/04 defines, so the
  swap to the real plan type is a type change, not logic surgery.
- Headless CI: GL calls only through the shim-exercised path; fixture must
  render at least N headless frames in tests (mirrors BootSmokeTest harness).

## Open questions

- **OQ-1 (scope):** RESOLVED 2026-11-22 — **synthetic fixture first** (user
  selection). Seeded fake dungeon graph in `core`; the overlay's geometric
  core transfers to the real plan data later as a type change.
- **OQ-2 (input):** RESOLVED 2026-11-22 — **click-to-append** (user
  selection). Click a zone → route extends; click an earlier routed node →
  truncates there (docs/04 branch fallbacks); non-adjacent → rejected with
  feedback.
- **OQ-3 (HUD tooling):** RESOLVED 2026-11-22 — **scene2d widgets** (user
  selection) for HUD panels; custom SpriteBatch drawing stays for the iso
  map. Respec later if stock look blocks art direction.

## Changelog

- 2026-11-22 — Initial draft: screen graph, input→command boundary, shared
  isometric transform, plan-overlay fixture spec (DR-0001 validation 4),
  OQ-1–OQ-3 opened.
- 2026-11-22 — **Approved**: OQ-1 synthetic-first, OQ-2 click-to-append,
  OQ-3 scene2d HUD; implementation begins.

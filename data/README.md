# Data Tables (H5 — data-driven content)

Content lives here as plain, human-readable JSON, loaded and validated at
startup by the `sim` module's data layer. Balance edits must never require a
code change (H5) and must never appear as constants in game logic (rules/02).

Present tables:

- `economy.json` — **landed Phase 1, approved 2026-11-22 (docs/08)**: resources
  + capacities, roles + per-second rates, XP/level-up tuning, and
  `foundingSupplies` (the Phase-2a stand-in for founding map-node loot,
  docs/09 — exactly one wood camp's cost; retune when world-gen arrives).
- `buildings.json` — **landed Phase 2a, approved 2026-11-22 (docs/09)**:
  building costs (deducted upfront), `progressCost` in progress-points
  (builders add 1/tick), `hosts`+`slots` (completed-site worker capacity),
  `requires` gates, `grantsTier`, `capacityBonus`.

Planned (schemas land with their mechanics-doc phase):

- `roles.json` — role definitions, base output/tick, level curves
- `town-tiers.json` — tier thresholds, role unlocks (role knowledge)
- `followers.json` — follower earning/definitions (high-rank vs ordinary)
- `dungeons.json` — zone/event tables, loot tables, stratum flavor
- `strata.json` — stratum list, biome palettes, hazard/attrition numbers

All numeric values are PLACEHOLDER until their balance doc phase.

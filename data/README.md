# Data Tables (H5 — data-driven content)

Content lives here as plain, human-readable JSON, loaded and validated at
startup by the `sim` module's data layer. Balance edits must never require a
code change (H5) and must never appear as constants in game logic (rules/02).

Planned (schemas land with their mechanics-doc phase):

- `roles.json` — role definitions, base output/tick, level curves
- `buildings.json` — building costs, production/consumption, tier requirements
- `town-tiers.json` — tier thresholds, role unlocks (role knowledge)
- `followers.json` — follower earning/definitions (high-rank vs ordinary)
- `dungeons.json` — zone/event tables, loot tables, stratum flavor
- `strata.json` — stratum list, biome palettes, hazard/attrition numbers

All numeric values are PLACEHOLDER until their balance doc phase.

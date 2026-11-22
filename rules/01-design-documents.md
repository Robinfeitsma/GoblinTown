---
title: How to Write Design Documents
status: approved
updated: 2026-02-11
---

# How to Write Design Documents

## When a design document is required

Any change that affects one of the following must have a design document
**before** implementation:

- game mechanics, balance, progression
- data / save formats
- UI/UX flows
- architecture or tooling choices (as a Decision Record instead)

Trivial fixes (typos, formatting, numerical copy-edits) do not need one.

## Procedure

1. Copy the closest template from `docs/templates/` into `docs/` with a
   numbered filename.
2. Fill only the sections you can support with the current concept and
   requirements docs; mark the rest `TODO` rather than deleting them.
3. Set `status: draft` and have the user review it. Move to `review` while
   being discussed, `approved` only when the user confirms.
4. If the doc replaces an older one: set the old doc's `status: superseded`,
   move it to `archive/`, and link both directions (`supersedes` in the new
   doc, "superseded by" note in the old one).
5. Update the document index in `README.md`.

## Structure rules

- Start every doc with a 3–5 sentence **Summary** a new reader can understand
  without context.
- Use tables for anything tabular (tiers, costs, drop rates, level curves).
  Balance lives in tables, not prose.
- Every mechanic must state: player-facing trigger → underlying state change →
  observable outcome. Example: *player assigns goblin to mine → mine tick adds
  ore to stockpile → ore counter and town level bar update.*
- Cross-reference other documents by relative path
  (`[Core Loop](00-game-concept.md)`), never "see above".
- Specify **failure and edge cases** explicitly: what happens while offline,
  when a run ends, when storage is full, at max level, at zero resources.
- Placeholder numbers must be written as tables and tagged `PLACEHOLDER` in
  the doc's frontmatter (`placeholders: true`) so they are easy to find later.

## Language rules

- Present tense, active voice. "The mine produces 5 ore/tick", not "ore will
  be produced".
- One term per concept, defined once in a glossary section of the game concept
  doc and reused verbatim everywhere (e.g. always "run", never "expedition"
  sometimes).
- No marketing language. Documents describe what the game does, not how it
  feels to sell.
- Numbers: always state units and per-what (per tick, per second, per run).

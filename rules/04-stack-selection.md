---
title: Stack Selection Process
status: approved
updated: 2026-02-11
---

# Stack Selection Process

How we choose the programming language and engine, and how the ruleset grows
afterwards.

## Trigger

This process runs only when `docs/01-technical-requirements.md` is in
`Status: approved`.

## Procedure

1. **Derive candidates.** List at least 4 candidate stacks (e.g. Godot/GDScript,
   Godot/C#, Unity/C#, Bevy/Rust, Phaser/TypeScript, LÖVE/Lua, libGDX/Java-or-Kotlin,
   custom C++). Derive them from the technical requirements AND the developer
   profile (docs/01-technical-requirements.md → decision inputs #6) — don't list
   tools first and bend requirements to fit. The developer's languages
   (Java, JS/TS, Python, some C-family) and engine familiarity
   (Godot, Unity-family, web, mini custom engine) must appear in the field
   either as scored assets or as explicitly rejected candidates.
2. **Score against requirements.** Build a comparison table from
   `docs/templates/stack-comparison-template.md`. Score each stack 1–5 per
   requirement. Cite evidence (docs, benchmarks, prior projects) for every
   score ≤ 2 as well as ≥ 4; unsupported scores are struck.
3. **Test the riskiest requirement.** For the top 2 candidates, verify the
   single riskiest requirement (usually: deterministic simulation + bounded
   offline catch-up, or isometric performance on target hardware) with a
   throwaway prototype, if feasible within a day.
4. **Decision Record.** Write `decisions/000N-stack-selection.md` from the
   decision record template: chosen stack, rejected candidates with reasons,
   and the consequences. `Status: approved` requires explicit user
   confirmation.

## After the decision

1. Fill the `10-Language-specific` sections of `rules/02-coding.md` and
   `rules/03-testing.md`; review them with the user.
2. Create or update the folder structure section of `README.md` and the
   *Moving to the coding folder* checklist.
3. Set up the coding repository, move this design workspace into it (README
   checklist step by step), and record that move itself as a Decision Record.
4. Archive this file's now-obsolete phase note if the process is finished.

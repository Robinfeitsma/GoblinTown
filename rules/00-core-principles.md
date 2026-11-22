---
title: Core Principles
status: approved
updated: 2026-02-11
---

# Core Principles

These rules apply to **every** task an LLM performs in this workspace.

## 1. Design documents are the source of truth

- Behavior is defined in `docs/`, never invented on the fly.
- If the answer to "how should X behave?" is not in a document, the correct
  action is to write the design first (or ask the user), not to improvise.
- Code implements documents; documents never describe code that does not have
  an approved design.

## 2. Explicit over implicit

- Prefer explicit numbers, tables and formulas over vague words.
  - Bad: "goblins get stronger over time"
  - Good: "each goblin level adds +15% HP and +10% attack (multiplicative,
    rounded down)"
- Every formula must be reproducible by a reader without asking questions.

## 3. Articulate the contradiction, don't hide it

The genre mix (idle + roguelite + RTS-like town management) has genuine
tensions: idle games reward absence, roguelites reward mastery through resets,
RTS-like play rewards constant presence. When a design decision resolves one of
these tensions, name the tension in the document and state **why** the chosen
resolution wins.

## 4. Small reversible steps

- One document = one concern. Don't grow a doc past ~400 lines; split it and
  cross-link instead.
- Never silently rewrite an `approved` document: first set the proposal to
  `draft`/`supersedes`, get it approved, then archive the old version in
  `archive/`.
- Keep a changelog section at the bottom of every document.

## 5. Failure conduct

- If information needed for a task is missing, list precisely what is missing
  and stop — do not guess game-affecting values.
- Balance numbers in drafts are placeholders and must be marked
  `PLACEHOLDER` until approved.
- Report honestly: state what was done, what is incomplete, and what was not
  verified.

## 6. Portability

- All paths in documents are relative, so the folder can be moved into a
  coding repository without edits.
- No tool-specific or machine-specific assumptions inside `docs/`.

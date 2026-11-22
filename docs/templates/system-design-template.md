---
title: <System Name> — System Design
status: draft
updated: <ISO date>
---

# <System Name> — System Design

## Summary

<What this system is responsible for, and — equally important — what it is
explicitly NOT responsible for.>

## Responsibilities

- 

## Architecture

<Data-flow or component diagram in a fenced block, e.g.:

```
Input/Command layer ──> Simulation (deterministic, tick-based) ──> Game State
                                  │                                   │
                          Precomputed offline math            Save/Load (versioned)
                                  │                                   │
                              RNG (seeded, recorded) ──> Persistence layer
```

>

### State model

<Tables or code-agnostic pseudocode of the data shapes. Plain data, no
business logic.>

| Field | Type | Meaning |
|---|---|---|

### Interfaces

<What this system exposes and what it consumes. Commands, events, data
contracts — described without implementation language.>

## Determinism & time notes

<How this system stays deterministic (H1) and bounded under offline catch-up (H2).>

## Failure modes

<Focused on the engineering risks of this system, test obligations follow
rules/03.>

## Changelog

- <ISO date> — Initial draft.

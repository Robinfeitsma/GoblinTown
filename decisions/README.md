# Decisions

Decision Records (ADR-style) with consequences. Numbered `0001`, `0002`, …

- Use the template `../docs/templates/decision-record-template.md`.
- Statuses: `draft → review → approved` (approval requires the user's explicit
  confirmation).
- An approved decision may only be changed by a new record that supersedes it.

- DR-0001 — stack selection (libGDX + Java) — **approved**
- DR-0002 — design workspace migrated into coding repo — **approved**
- DR-0003 — fixed-point convention (Q32.32 in `int64`) — **review**

Pending decision: **DR-0003 fixed-point convention** — unblocks the Phase-1
balance numbers; approval requires the user's explicit confirmation.

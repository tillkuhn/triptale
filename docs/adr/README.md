# Architecture Decision Records

Lightweight records of significant decisions — the *why*, not the *what* (the code already
shows what). One file per decision, numbered sequentially, never renumbered or deleted once
merged; a reversed decision gets a new ADR that supersedes the old one (mark the old one's
status `Superseded by ADR-00xx` rather than editing its original reasoning away).

| # | Title | Status |
|---|-------|--------|
| [0001](0001-coordinate-display-format.md) | Display coordinates in DDM, not DMS or raw decimal degrees | Accepted |

## Format

```markdown
# ADR-00xx: Title

Status: Proposed | Accepted | Superseded by ADR-00yy

## Context
What's the situation that forces a decision? What constraints apply?

## Decision
What did we choose?

## Consequences
What becomes easier or harder as a result? What did we explicitly give up?
```

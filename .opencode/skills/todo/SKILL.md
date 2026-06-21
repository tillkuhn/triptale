---
name: todo
description: Use when the user asks to "implement", "fix", or "plan" a todo by number (e.g. "implement todo 02", "fix 03", "plan todo 04"). Looks up the matching second-level item in TODO.md, resolves ambiguity via the grill-me skill, implements it, then marks it DONE.
---

# Todo

Implement, fix, or plan a numbered item from `TODO.md` at the project root.

## Trigger

The user references a todo by number, e.g.:

- "implement todo 02" / "implement 02"
- "fix 03"
- "plan todo 04"

The number is the leading numeric token (preserve any leading zeros, e.g. `02`).

## Workflow

1. **Locate the item.** Read `TODO.md` at the project root. Find the heading
   whose text starts with the requested number. Headings may use any level
   (`#`, `##`, …); match on the number token regardless of level. Example:
   for "implement 03", match `## 03 show current memory consumption in about`.

   - The heading text after the number is the title.
   - Any non-heading lines below it (until the next heading) are the item's
     details/requirements.

2. **Skip DONE items.** Ignore any heading whose text starts with `DONE`
   (e.g. `## DONE 01 ...`). Do NOT implement a DONE item unless the user
   explicitly asks to redo or reopen it. If the requested number is only
   present as a DONE item, tell the user it's already done and ask whether
   they want it redone.

   - If no matching heading exists at all, report that and list the available
     open todo numbers and titles.

3. **Check `docs/` for prior context.** Before grilling, look in the `docs/`
   directory for a file whose name starts with the requested number (e.g.
   `docs/04_fixbug.md` for todo `04`). Match on the leading numeric token,
   ignoring the separator/suffix. Such files may contain plans or analyses
   from previous sessions. If one exists, read it and use it as context for
   the grilling and implementation. If multiple match, read all of them; if
   none match, proceed without it.

4. **Grill first — always.** This step is mandatory and happens BEFORE any
   planning or implementation, for every verb ("plan", "implement", and
   "fix"). Load and run the `grill-me` skill to interview the user and resolve
   every ambiguous or underspecified requirement in the item. Do NOT produce a
   plan, write code, or make any concrete changes until the grilling is
   complete and the requirements are fully clear.

5. **Act based on the verb.** Only after step 4 (grill-me) is complete:
   - "plan" — produce an implementation plan only; do not write code.
   - "implement" / "fix" — implement the change in the codebase, following the
     project's conventions (see CLAUDE.md), and verify it (build/tests where
     applicable).

6. **Add tests and update README where appropriate
   - If test makes sense, add a unit test
   - if the change is relevant for the user to understand the app, update README.md


7. **Mark DONE.** Once an "implement"/"fix" item is completed (and verified),
   edit `TODO.md` to add the `DONE` prefix immediately before the number,
   preserving the rest of the heading verbatim. including ## (2 hashes) to indicate the h2 headline. Example:

   - Before: `## 03 show current memory consumption in about -> info`
   - After:  `## DONE 03 show current memory consumption in about -> info` 

   For "plan" requests, do NOT mark the item DONE.

## Notes

- Preserve the original heading level and text exactly when adding `DONE`.
- Keep the leading-zero formatting of the number as written in `TODO.md`.

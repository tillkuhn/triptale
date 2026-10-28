# Todo 76 — "Add Private Notes" + bullet-style Octobot inbox landing

## Problem

Two things that became one:

1. The 🔒 Add Private Section menu item (todo 48, `MainController.onAddPrivateSection`) inserted
   `## Internal` + a prose placeholder. Renamed to **"🔒 Add Private Notes"**, inserting
   **`Markdown.PRIVATE_DEFAULT_HEADING`** (`## Private Notes 🔒`) and a placeholder **bullet**
   `* …` instead. No change needed in `Markdown.isPrivateHeading` — the heading still starts with
   the keyword `private` followed by whitespace, so the existing keyword-matching rule (todo 48)
   already recognizes it unchanged; only the literal text inserted for a *new* section changed.
   `Markdown.privateHeadingOffset` is still how "does a private section already exist" is
   detected (an existing `## Internal` heading still counts).

2. The mobile inbox (todo 68, `inbox.InboxNotes`, previously its own `## Inbox` heading) and the
   private section feature (todo 48) were two separate concepts living in the same tale body.
   Octobot capture items (Telegram, "🐙 Octobot's Inbox" dialog, ➕ Add) are personal/raw notes by
   nature, so they now land in the tale's **private** section instead of a separate public
   heading:
   - `InboxNotes.append` finds an existing private section via `Markdown.privateHeadingOffset`
     and appends *inside* its block (`Markdown.privateBlockEnd`, new — see below), instead of
     looking for/creating a separate `## Inbox` heading.
   - If no private section exists yet, it creates one using the same
     `Markdown.PRIVATE_DEFAULT_HEADING` that "🔒 Add Private Notes" uses — one shared concept.
   - The `## Inbox` heading and `InboxNotes.HEADING` constant are gone; existing entries that
     already have an `## Inbox` heading are **not** migrated (left as plain existing text) — only
     new Octobot adds changed behavior.

## Bullet formatting

Octobot items land as a single bullet line inside the private block, not a free-text paragraph:

- The note's internal newlines are collapsed into single spaces (`\s+` → `" "`) — a multi-line
  Telegram message becomes one line.
- A fixed suffix marks the source: **`— added by Octobot 🐙`** (em dash, space before the emoji).
- Example — Telegram message:
  ```
  Nice Cooking School
  https://asia.cooking.school/
  Has good ratings
  ```
  becomes, appended inside the nearest/created private block:
  ```
  * Nice Cooking School https://asia.cooking.school/ Has good ratings — added by Octobot 🐙
  ```
- Applies equally to a photo item's caption (todo 74) — one code path (`InboxItem.text()`
  already carries both plain notes and photo captions into `InboxNotes.append`).

## Insertion mechanics

- **New private section** (none exists yet): same shape as "🔒 Add Private Notes" — appended at
  the end of the tale, `(blank line if non-empty) + PRIVATE_DEFAULT_HEADING + "\n\n" + bullet`.
- **Existing private section**: `Markdown.privateBlockEnd(body)` (new method, mirrors
  `stripPrivate`'s block-scanning logic) finds the block's end — the start of the next heading at
  the same or higher level, or `body.length()` if the block runs to the end of the tale — so a
  bullet lands *inside* the block even when the private section isn't the very last thing in the
  tale (todo 48 allows public content after it, even though typical usage puts it last).
- **Joining rule**: if the block's last non-blank line already looks like a bullet (`^[*+-]\s`),
  the new bullet is appended directly on the next line (tight list, no blank line between
  bullets). Otherwise — e.g. the block still holds old freeform prose like "Komoot said 87 km" —
  one blank line is inserted first, then the bullet.

## Where it applies (final)

- `src/main/resources/fxml/main.fxml` — `addPrivateMenuItem` text → "🔒 Add Private Notes"
  (fx:id and `onAddPrivateSection` method name kept as-is, cosmetic-only rename skipped to
  minimize churn).
- `MainController.onAddPrivateSection` — inserts `Markdown.PRIVATE_DEFAULT_HEADING` +
  `"\n\n* "` + `PRIVATE_PLACEHOLDER` (now just `"…"`, the ellipsis character, selected for
  replace-by-typing same as before).
- `util.Markdown` — new `PRIVATE_DEFAULT_HEADING` constant and `privateBlockEnd(String)` method;
  `isPrivateHeading`/`privateHeadingOffset`/`stripPrivate` unchanged.
- `inbox.InboxNotes` — fully reworked `append`; `HEADING` constant removed.
- Docs updated: `docs/48_private_sections.md` ("🔒 Add Private Notes" section + new "Todo 76"
  section), `docs/68_mobile_inbox.md` ("Landing spot" bullet).

## Tests

- `MarkdownTest`: `privateBlockEnd` — runs to end of tale, stops at next same/higher heading,
  keeps subheadings inside the block, null/empty/no-private-section → `-1`; the new default
  heading is recognized as private by `stripPrivate`.
- `InboxNotesTest`: rewritten for the new bullet/private-section behavior — creates a new private
  section on first capture, reuses an existing one (tight bullet stacking vs. blank-line-before-
  bullet after freeform content), inserts inside the block when the private section isn't last,
  collapses a multi-line note into one bullet.
- Full suite (`mvn test`) green after the change — 555 tests.

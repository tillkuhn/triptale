# 69 Emoji picker

A searchable popup to insert a travel-relevant emoji into the current tale, or copy it to the
clipboard otherwise. Design agreed in a grilling session on 2026-10-21.

## Scope

**A small curated, flat, searchable list — not the full Unicode emoji catalog.** Travel-related
emoji only (transport, hiking, food, drink, nature, weather), realistically 50–150 entries, no
categories/tabs. Rendering and filtering that many short-lived `Label`/`Button` nodes is trivial
for JavaFX; this is explicitly *not* a performance-sensitive feature as long as scope stays this
small. If "all emoji" is ever wanted, that is a materially different feature (bigger data file,
likely categorized) and should get its own todo rather than growing this one.

Also dropped: the "shopping cart" multi-select idea from the original todo text. One click picks
one emoji and closes the popup (see Lifecycle below).

## Action on click

- **Tale editor has focus (or had it when the picker was invoked):** insert the emoji at the
  caret position in `talesArea`, replacing the current selection if there is one — same
  convention as typing a character. Use `replaceText(start, end, emoji)` (not `setText`), as in
  `MainController.onAddPrivateSection` (`MainController.java:1416-1433`), so the edit stays
  undoable. The caret position/selection must be captured *before* the modal dialog opens and
  steals focus, then restored (`talesArea.requestFocus()`, `positionCaret(...)`) after the emoji
  is inserted and the dialog closes.
- **Otherwise (no entry loaded, or focus was elsewhere):** copy the emoji to the clipboard via
  `Clipboards.putString` — useful for pasting into the trip name, a dialog field, etc.
- The menu item is **always enabled**, regardless of whether an entry is loaded — the clipboard
  fallback makes it useful even with nothing open.

## Dialog mechanism

A standard modal `javafx.scene.control.Dialog` under `ui/dialog/` (e.g. `EmojiPickerDialog`),
styled via `Dialogs.applyStylesheet(dialogPane)` like every other dialog in the app. No new
"lightweight non-modal popup" infrastructure — the codebase has no precedent for that
(`Popup`/custom `Stage`/`PopOver` all absent, see research), and since the dialog closes right
after a single pick, a plain modal `Dialog` is simplest and keeps the app's existing dialog
conventions intact.

## Lifecycle

Picking an emoji closes the dialog immediately. One emoji per invocation; invoke again
(menu/shortcut) for another.

## Menu placement & shortcut

- New `MenuItem` under the existing **"Tale Entries"** menu (`main.fxml`), next to
  `👁 View Source…`, e.g. `😀 Emoji Picker…`.
- Accelerator **`Shortcut+E`** (⌘E / Ctrl+E). The todo's original "fn E" is not usable: `fn` is
  intercepted by macOS firmware on most keyboards and isn't exposed as a JavaFX
  `KeyCombination` modifier at all — no precedent for it anywhere in the codebase.
  `Shortcut+E` is free and mnemonic, consistent with the app's other single-letter shortcuts
  (`Shortcut+S` save, `Shortcut+K` sync).

## Search

Plain case-insensitive substring match against each entry's English keyword(s) (e.g. typing
"hike" matches 🥾/🚶 entries tagged `hike, hiking, trail`). No `FilteredList` precedent exists in
this codebase yet; a manual filter over a small in-memory list is more than fast enough at this
scale.

## Data file

New `src/main/resources/emojis.txt`, following the existing flat-text-resource convention
(`wisdoms.txt`, `greetings.txt`, `dj-lines.txt` — one entry per line, `#`-comments and blank
lines tolerated, loaded via `getResourceAsStream`). Format: `emoji | comma, separated, keywords`.
Content curated as part of this todo's implementation (travel categories from the original todo
text: transport, hiking, food, drink, nature, weather); not a user-supplied list.

Emoji selection must follow **ADR-0004** (`docs/adr/0004-emoji-in-ui-labels.md`): no trailing
variation selector (U+FE0F), and prefer glyphs whose monochrome Segoe UI Symbol fallback on
Windows is still recognizable — same rule already applied when curating `domain.Activity`
(`docs/55_activity_per_entry.md`).

## Non-goals

- Full Unicode emoji catalog / search-everything — explicitly out of scope, see Scope above.
- Multi-select "shopping cart" checkout action — dropped in favor of one-pick-and-close.
- Categories/tabs/grouped sections — flat list only, for now.
- A new non-modal/lightweight popup framework — reuses the existing modal `Dialog` pattern.

## Relevant files (for implementation)

- New: `ui/dialog/EmojiPickerDialog.java`, `src/main/resources/emojis.txt`
- `fxml/main.fxml` (new `MenuItem` under "Tale Entries")
- `ui/MainController.java` (`talesArea` caret capture/restore, menu handler)
- `ui/Clipboards.java` (clipboard fallback, existing)
- `ui/Dialogs.java` (`applyStylesheet`, existing)

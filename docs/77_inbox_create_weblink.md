# 77 — Octobot inbox: convert a detected link into a Weblink

## Problem

An inbox capture (`InboxDialog`) often contains a place name plus a maps/website URL, e.g.:

```
Beldibi Turkish Bath
https://maps.app.goo.gl/74mAg3pfu8rukoWB8?g_st=atm
```

Today, Add (`MainController.handleInboxItem`) only appends the whole caption as a private-notes
bullet (`InboxNotes.append`) — the URL is never turned into a structured `url_<verb>` weblink
(`domain.LinkKind` / `domain.Link`, edited normally via `ui/dialog/LinksDialog.java`). The user
has to re-type it manually in 🕸 Weblinks Manager afterwards.

## Decisions (resolved via grill-me, 2026-10-28)

**Scope**: only the first URL found in `item.text()` is considered (same tokenizer
`InboxDialog.TOKENS` / `UiText.isValidHttpUrl` already used for the clickable-hyperlink
rendering). Additional URLs in the same message are ignored for v1.

**Row UI** (`InboxDialog.row`): one new `CheckBox` "Create Weblink", appended *after* the
existing Add/Dump buttons in the same `HBox actions` row (`actions.getChildren().addAll(attach,
discard, createWeblink)`). Disabled whenever the item's text contains no detected URL (tooltip:
"No link found in this message"). **Checked by default whenever a URL is present** (unticking is
the opt-out) — changed from the original "unchecked by default" after testing showed most
captures with a link do want one, so pre-checking saves a click on the common case. No
verb/name/url fields on the row itself — those only appear in the follow-up dialog (below),
since picking a verb needs to know the target day's already-used verbs, which can only be
resolved once Add actually runs.

**Add flow** (`MainController.handleInboxItem`): unchanged up through `inboxDir.markProcessed`.
After a successful Add — note appended, photo imported (if any), entry saved — if the checkbox
was ticked **and** a URL was detected:

`MainController.applyWeblink` runs **before** the note bullet is built (not after the save, as
originally planned) so the bullet's origin text can name the verb — see "Bullet names the verb"
below. It only applies the result to the in-memory `links` field; it does not save on its own.

1. Compute untaken verbs for that day: same filter `LinksDialog` already uses
   (`Arrays.stream(LinkKind.values()).filter(k -> !links.containsKey(k.key()))`), against the
   controller's current `links` field (already reloaded for the target date by the existing
   `datePicker.setValue(date)` navigation).
2. If the untaken list is **empty** (all 12 verbs already used for that day): skip the dialog
   entirely, just `status("All weblink kinds already used for " + date + " — add manually in 🕸
   Weblinks")`. Add/removal from the inbox still completes normally either way.
3. Otherwise open a new minimal dialog (see below); on confirm add the result to `links` and
   call `updateLinksButton()` — the controller then appends the note bullet (if any) and does
   **one** combined `saveTheDayIfPossible()` for both the bullet and the link.
4. Cancelling that dialog does **not** undo the Add — the photo (if any) is already imported and
   `markProcessed` runs regardless; only the weblink step is skipped, and the note bullet (if
   any) falls back to the plain "added by Octobot 🐙" suffix.
5. The URL stays in the private-notes bullet text as-is (no stripping) — `InboxNotes.append`'s
   collapsing of the raw note is untouched; the weblink is an additional structured extraction,
   not a replacement.

**Bullet names the verb**: when the Add also created a weblink, the private-notes bullet's
origin suffix becomes `"added and <verb>-linked by Octobot 🐙"` instead of the plain `"added by
Octobot 🐙"` (e.g. `"added and eat-linked by Octobot 🐙"`), via a new
`InboxNotes.append(String, String, LinkKind)` overload — the two-arg overload still exists and
delegates with `null` for every other caller. This is why the dialog must run before the bullet
text is assembled rather than after the save, as first sketched above.

**New dialog**: `ui/dialog/QuickWeblinkDialog.java`, following the existing
dialog contract (plain object, `Optional<Result> showAndWait(...)`, no controller-state
mutation). Single row: `ComboBox<LinkKind>` (items = the untaken-verbs list computed above),
`TextField` url (pre-filled, editable), `TextField` name (pre-filled, editable), Add/Cancel
buttons. Validation mirrors `LinksDialog.Row.problem()` — url must be `Link.isHttpUrl`, OK
disabled otherwise. Returns `Optional<Map.Entry<String, Link>>` (verb key → `Link`), or empty on
Cancel.

**Default verb** pre-selected in the combo:
1. Scan the item's **non-URL** text (the `item.text()` with the matched URL token removed) for
   any `LinkKind.key()` as a **whole word**, case-insensitive (word-boundary match, so "eating"
   does not match "eat").
2. Only verbs **not yet used** on that day are eligible candidates for this scan — if the text
   says "sleep" but `sleep` is already taken, keep scanning for another keyword match instead.
3. If more than one keyword matches, the one that appears **first in the text (left-to-right)**
   wins.
4. If no keyword matches, fall back to the first untaken verb in `LinkKind.ORDER` (alphabetical
   — same default `LinksDialog`'s own "add new" row already uses).

This lets a user write something like "Dive and eat at the harbor" or just type the bare verb
("eat") into their Telegram message to steer the pre-selected verb, without any NLP.

**Default name**: all of `item.text()` with the matched URL token removed, then collapsed to a
single line (same whitespace/newline-collapsing `InboxNotes` already does for multi-line
captures) and trimmed. From the example above: `"Beldibi Turkish Bath"`.

**Plus-icon contrast fix** (screenshot): the `➕` emoji on `InboxDialog`'s `Button attach = new
Button("➕ Add")` renders near-invisible against the button's dark teal gradient — JavaFX/Prism
falls back to a flat monochrome glyph for this particular emoji that happens to sit close to the
button's own background tone, unlike `🗑`/multi-tone emoji which render fine. Fix: swap `➕` for
a plain typographic character (e.g. `+`) so it's rendered as ordinary text and inherits
`-fx-text-fill` like the rest of the button label — guaranteed contrast, no CSS/graphic
plumbing needed. **Scoped to this one button only** — `LinksDialog`'s `＋` (different,
full-width character, not reported as broken) and any other emoji-plus buttons are left alone.

## Implementation notes

- "Strip first URL from text, collapse to one line" ended up as `InboxLinks.nameWithoutUrl`
  (`inbox/InboxLinks.java`), alongside `InboxLinks.firstUrl` and `InboxLinks.guessVerb` — a
  small pure-function sibling to `InboxNotes`, not a method on `InboxDialog`.
- `QuickWeblinkDialog` duplicates the small `field`/URL-validation pattern from `LinksDialog`
  rather than extracting a shared base — small enough that sharing wasn't worth the coupling.

## Relevant files

- `ui/dialog/InboxDialog.java` — row UI, checkbox, URL/verb-keyword detection
- `ui/MainController.java` — `handleInboxItem`, `applyWeblink`, `onLinks` as the apply-pattern
  reference
- `ui/dialog/QuickWeblinkDialog.java` — the follow-up verb/url/name dialog
- `inbox/InboxLinks.java` — pure helpers: first-URL detection, name-without-url, verb guessing
- `ui/dialog/LinksDialog.java` — untaken-verb filter pattern reused, validation pattern
- `domain/LinkKind.java`, `domain/Link.java` — verb enum, link value type
- `inbox/InboxNotes.java` — private-notes bullet append (unchanged, URL stays in it)
- `src/main/resources/fxml/triptale.css` — no new CSS needed if the icon fix is a plain `+`
  character instead of an emoji

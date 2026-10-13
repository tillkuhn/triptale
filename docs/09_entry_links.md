# 09 Links per entry (`url_<verb>`)

Reopened (formerly WON'T DO: a `links:` array of YAML objects was rejected as too complex).

## Original request

we need a flexible way to store multiple links per entry, currently it's only trackurl.
but structure should be simple, similar to other props (avoid complex yaml objects).
suggest a concept: url_<verb> such as url_eat. 
verb should come from an enumerated value, start with. "sleep, eat, drink, track, dive".
in the app each verb is associated with an emoji and a text used in the exported diary, so we can add a list to the end for references e.g.

* 🛏️ Sleep at: value-of sleep url
* 🍲 Eat at: value-of eat url

each verb occurs max once, we want to keep url list deliberately short as this is not an advertising app.
value in frontmatter either only url, or quoted markdown style url "[SuperTours](https://supertour-app.com)" if a name is attached. refactor existing trackurl values to use new fieldname url_track and remove old field.
suggest to replace existing input field in Entry UI for track url by a button that triggers a new link dialogue

Suggested layout of button:  [ URLs: 🛏️ 🍲 ] (emojis indicate the links already present). if none are present, label is "No URLs yet)

Suggested layout of dialogue: One row for each existing link with a delete icon ad the end, new row with dropdown for link type, url (mandator for new entry) and name (optional, url itself is enought see storage hint above). dropdown for new entry should only show verbs (with emoji) that are not yet stored. Save can simply save everything, close must be available to discard.

Example frontmatter
```
---
date: 2026-07-24
route: 'Essen → München'
url_eat: https://mucfood.com/
url_sleep: https://nice.hotel.com/
url_tour: "[SuperTours](https://supertour-app.com)"
url_track: <migrated from former trackurl key> 
```

## Decisions (grilled 2026-10-11)

- **Unknown verbs are preserved.** Any `url_<verb>` key is read and written back. The UI offers only the curated verbs. Unknown verbs show as 🔗 + verb name and can be deleted but not added.
- **Migration** uses a one-off `sed` outside the app (`s/^trackurl: /url_track: /`), with no legacy code in the app. Keys are not re-sorted; the next save fixes the order.
- **Value format** is a bare URL or `"[Name](url)"`, parsed with `^\[(.+?)\]\((.+)\)$` so URLs containing `(…)` work. `[`/`]` are stripped from names. A name that is blank or equal to the URL is stored as a bare URL. Only http(s) can be added (`UiText.isValidHttpUrl`). Invalid values are kept and flagged with ⚠️, and exported as plain text.
- **Domain:** a `domain.LinkKind` enum with `key()`, `emoji()` and `label()`; its declaration order is the export order. `DiaryEntry.trackUrl` → `SequencedMap<String, Link> links`, where `Link(url, name, valid)` has `parse`/`format`.
- **Kinds, in export order:** 😎 Chill at: (not translated) · 🗺️ Track: · 🐠 Dive at: · 🍲 Eat at: · 🍺 Drink at: · 🛏️ Sleep at: · 🎵 Listen to: · 🎬 Watch: — then unknown verbs alphabetically as `🔗 <Verb>:`.
- **YAML:** global alphabetical key order is kept, so `url_*` keys come after `type`.
- **Export:** the list goes at the end of each entry. `entry-track.md` → `entry-links.md`, one line per link. Link text is the name, otherwise the domain only; the href is always the full URL. WordPress gets the same list as a `<ul>`, replacing the inline `Track` part.
- **Naming:** "Links" in code; "🕸️ Weblinks" in the UI (button, and the "🕸️ Weblinks Manager" dialog, renamed 2026-10). The YAML prefix stays `url_`.
- **Entry form:** the track URL field and the 🔗 button are replaced by a fixed-width button (room for six verb emojis) labelled `🕸️ Weblinks: 🗺️ 🍲` (🔗 for unknown verbs, ⚠️ if any value is invalid), or `🕸️ No Weblinks yet`. It's disabled when no entry is loaded.
- **`LinksDialog`** (`ui/dialog/`) returns `Optional<Map<…>>`. The controller applies it as form state and marks the entry dirty; the normal entry Save persists it. Buttons are OK and Cancel. Existing rows have the verb fixed, URL and name editable inline, plus 🔗 open and 🗑 delete. The new row's dropdown shows only verbs not yet used. On OK, a filled-in new row is added if valid; an invalid one blocks OK with an inline error.
- **Tests:**
  - `Link.parse`/`format` round-trips (parentheses in URLs, `]` in names, invalid values).
  - a `MarkdownStore` round-trip that preserves `url_tour`.
  - link-list output of both exporters.

## Implementation notes

- Domain: `domain.LinkKind` (the curated verbs) and `domain.Link`. `DiaryEntry.links()` is never null and is ordered by `LinkKind.ORDER`.
- Storage: `MarkdownStore.parseLinks` picks up every `url_*` key. `saveEntry` writes them after `type`, sorted alphabetically.
- Export: `entry-links.md` (one `* {{emoji}} {{label}}: {{link}}` line per link) goes after the tale. `WordPressExporter.links` writes a list block after the tale and before the gallery.
- UI: `linksButton` in `main.fxml` and `ui/dialog/LinksDialog`.

## Migration (one-off, not part of the app)

Run in the data dir. Commit first so the change shows up as a clean diff:

```bash
grep -rl --include='*.md' '^trackurl: ' . | xargs sed -i '' 's/^trackurl: /url_track: /'
```

The keys are not re-sorted, so `url_track` stays in `trackurl`'s old position until the entry is next saved in the app.

# 53 Entry fact header in exports

Show an entry's key facts as a "ticket stub" strip under the entry title: 1–n centered items
side by side, each with a big emoji, a bold value line and an optional small muted line. Our
own look, echoing the app's scalloped tale paper and teal accent: thin teal perforated edge,
dotted teal "perforation" separators between items, the day number as a small round teal
stamp badge. The title loses its ISO date, weekday and day suffix.

```
╭┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄╮
┆ (3) Mittwoch      ┊ 🚴 123 km         ┊ 🧭 50°06′N 8°40′E  ┆
┆     23. Juli 2026 ┊    100 m bergauf  ┊    Startpunkt       ┆
╰┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄╯
```

Decisions (grilled 2026-10-12):

- **Everything localized** via `ExportTexts` (trip language): weekday, long date, day label,
  numbers, "climbed", "Starting point"/"Destination". No ISO date anywhere readers see.
- **Heading** = title only. Empty or `DiaryEntry.DEFAULT_ROUTE` title → localized day label
  (`## Tag 3`); without a trip start date → weekday and date (`## Mittwoch, 23. Juli`).
- **Date item**: day stamp badge, weekday, long date.
- **Activity item** (todo 55): the activity emoji leads distance and climb (`altitude` is the
  climb: `123 km` / `100 m bergauf`). Activity without values (🏖 relaxing, 🏰 sightseeing) →
  emoji + localized activity name. Values without activity → neutral 📏. Zero values hidden
  (train days), as the WordPress export already does.
- **🧭 item**: start-focused, coordinates in the app's existing format (`Coordinates.toDdm`),
  not DMS. Start only (the common case) → coordinates + muted "Starting point". Start and
  stop → second line `→ <stop>`. Stop only → `→ <stop>` + "Destination". No coordinates → no
  item. Place names from todo 51 later take line 1 (`Frankfurt → Darmstadt`).
- Items without data drop out; no item at all → no strip.
- **Markdown** stays clean Markdown: one italic byline under the heading, replacing today's
  `Distance:` / `Altitude:` / `Start:` lines:
  `*Mittwoch, 23. Juli 2026 · Tag 3 · 🚴 123 km, 100 m bergauf · 🧭 50°06′N 8°40′E → 49°52′N 8°39′E*`
- **HTML export** renders from that Markdown: in HTML mode the exporter emits the strip as a
  raw HTML block (no blank lines inside, so commonmark passes it through) instead of the
  byline; simpler than a marker. Styles in `html-shell.html`.
- **WordPress**: native `wp:columns` block, one column per item (centered emoji / bold value /
  small muted line); editable, stacks on phones, blog theme look. The preview shell gets a
  rough stand-in style. No real draft until the preview is approved.
- The trip default activity (todo 57) is an author-side helper only and never exported.
- Verify: unit tests for byline, HTML strip and columns markup (all item combinations, `en`
  and `de`); then the user reviews a live HTML export and WordPress preview and we refine.

## Implementation (PoC, 2026-10-12)

- `export.EntryFacts`: builds 0–3 `Item`s (emoji or day `Stamp`, value, note, `inline`) from
  trip start + entry + `ExportTexts`; renders `byline()` and `html()`. WordPress columns are
  rendered in `WordPressExporter.facts(...)` from the same items.
- `DiaryExporter.headingLine` → title only, with the fallbacks above; `entry-heading.md` is
  `## {{title}}`, `entry-facts.md` wraps the byline in `*…*`. The old `entry-distance.md`,
  `entry-altitude.md`, `entry-startpoint.md` and the `stats.start` key are gone.
- Trip header dates in the export now use the long date too (`1. Juli 2025 → 3. Juli 2025`).
- New i18n keys: `facts.climbed`, `facts.start`, `facts.stop`.
- WordPress: every column is a big emoji paragraph (preset size `x-large`, 📅 for the date)
  over one paragraph `<strong>value</strong><br><small>note</small>`. Same rows in every
  column so they line up, and only one theme paragraph gap per column (first live test had
  "Tag 2" as large text misaligning the rows and loose spacing). The day joins the weekday
  (`Sonntag · Tag 2`); the stamp is HTML-only. The preview shell has stand-in CSS.

## Open (after the live review)

- Does the ticket look work on real data and narrow screens? Tune in `html-shell.html`.
- Do the WordPress theme's preset sizes look right on the blog?
- Place names from todo 51 take line 1 of the 🧭 item once they exist.

# 58 Show start/stop place in exports

Todo 51 stores `start_place` / `stop_place` on entries (free text like `"Warstein, DE"`,
filled by the Coordinator's 🔍 lookup or typed by hand, see
[docs/done/51_reverse_geocoding.md](done/51_reverse_geocoding.md)). Exports still only show
the DDM coordinates. This todo puts the place names into the exports.

## Where

`export.EntryFacts` builds the facts for all three exporters:

- Markdown: italic byline
- HTML: "ticket stub" block (styled in `html-shell.html`)
- WordPress: `wp:columns` block

See `docs/done/53_entry_fact_strip.md` for how the facts strip is built.

## Country flags (discussed 2026-10-13)

A flag is two Unicode regional-indicator characters (🇩🇪 = 🇩 + 🇪), so turning a code into a
flag is a plain character mapping with no table.

Rendering support:

| Where | Flags? |
|---|---|
| macOS, iOS, Android, Linux (Noto Color Emoji) | yes |
| Windows, Firefox | yes (Firefox ships its own emoji font) |
| Windows, Chrome/Edge/most apps | **no**: shows the two letters, roughly "DE" (Segoe UI Emoji has no flags on purpose) |

Per export:

| Export | Rendered by | Flags reliable? |
|---|---|---|
| WordPress | WP core's emoji script tests for flag support and swaps in Twemoji images | yes, unless the site disabled the emoji script (some themes and performance plugins do) |
| HTML | reader's browser | not on Windows Chrome/Edge. A polyfill font ([country-flag-emoji-polyfill](https://github.com/talkjs/country-flag-emoji-polyfill), about 80 KB, flags only) would fix it, but it comes from a CDN (not offline-first) or has to be embedded (bigger export). Rejected for now. |
| Markdown | whatever viewer opens it | depends on the OS; nothing we can do |

Leaning towards:

- **The flag replaces the country code** instead of sitting next to it: `🇩🇪 Warstein → 🇩🇪 Hagen`.
  Then the Windows fallback reads like `DE Warstein`, which says the same thing.
  `Warstein, DE 🇩🇪` would read `Warstein, DE DE` there, which looks like a bug.
- No polyfill font. The two-letter fallback is acceptable.
- Only turn the part after the last `", "` into a flag if it is exactly two ASCII letters.
  Otherwise print the stored text unchanged (the code isn't enforced, so it's free text).
- Codes that aren't ISO but look like one (`UK` instead of `GB`) render as two letters
  everywhere, which is harmless. Nominatim always returns valid ISO codes. England/Scotland
  use tag-sequence flags; Nominatim returns `gb` for them, so they aren't an issue.

## Open questions

- Replace the DDM coordinates with the place, or append it (place first, coordinates
  smaller or in a tooltip/title attribute in HTML)?
- Flag noise: on a single-country trip every entry shows 🇩🇪. Options: hide flags when all
  places of the trip/export share one country; or a trip-level "home country". The first
  needs no new data.
- Fallbacks: a place without coordinates shows the place only; coordinates without a place
  stay as today (DDM).
- Attribution: exports that contain looked-up names should carry a small
  "Place names © OpenStreetMap contributors" line (ODbL). We can't tell whether a name was
  looked up or typed, so add the line whenever any place is present? Translate via
  `ExportTexts` (`export.properties` / `export_de.properties`).

## Relevant files

- `src/main/java/net/timafe/triptale/export/EntryFacts.java`
- `src/main/resources/export/` (templates, `html-shell.html`)
- `src/main/java/net/timafe/triptale/export/WordPressExporter.java`
- `src/main/resources/i18n/export*.properties`

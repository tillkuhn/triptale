# 65 Weather per day

Store a day's weather on the entry (temperature range + condition), fetched automatically from
a free, keyless API once the entry has a start point, and show it in exports next to the other
entry facts (todo 53/58).

## Service: Open-Meteo

[open-meteo.com](https://open-meteo.com) — no signup, no API key for non-commercial use,
informally ~10k req/day. Two endpoints; this todo only uses the first:

- **Forecast API** `GET https://api.open-meteo.com/v1/forecast` — covers roughly the past 92
  days through 16 days ahead via `past_days`. Good enough for a diary that's written during or
  soon after a trip.
- **Historical archive** `archive-api.open-meteo.com/v1/archive` (ERA5, back to 1940) — **out of
  scope for now**; only the forecast endpoint is wired. An entry whose date falls outside the
  forecast window simply gets no weather (silent, see below) — a natural place to plug the
  archive endpoint in later without changing the frontmatter shape.

Request shape: `?latitude=..&longitude=..&start_date=yyyy-MM-dd&end_date=yyyy-MM-dd` (same day
twice) `&daily=weather_code,temperature_2m_min,temperature_2m_max&timezone=auto`. Response is
JSON with parallel `daily.time[]` / `daily.weather_code[]` / `daily.temperature_2m_min[]` /
`daily.temperature_2m_max[]` arrays; pick the index matching the date (there should be exactly
one). A date outside the supported range comes back as an API error (400) or an empty `daily`
block — both treated the same: no weather, no alert.

## Frontmatter

One new key, **last alphabetically** (after `url_<verb>`) in `MarkdownStore.saveEntry`:

```
weather: 15;22;1
weather: "15;22;1;Soest, DE"
```

Semicolon-separated, fixed position, quoted automatically by the YAML writer whenever the
value needs it (a comma in the place field, or the usual leading-dash/colon cases — never
hand-forced). Semicolon, not comma: a comma is the German decimal separator and also turns up
inside place names (`"Essen, DE"`), which made the packed value confusing to read by eye.

1. `temperature_2m_min`, whole °C, rounded.
2. `temperature_2m_max`, whole °C, rounded.
3. WMO `weather_code`, the raw numeric code — decoded to a label/emoji only at export time
   (`i18n`), never stored as text.
4. *(optional)* a place name — **not** returned by Open-Meteo itself (it only takes
   coordinates and returns numbers), so this is the entry's own `startPlace` echoed in, purely
   informational so the weather line reads standalone without cross-referencing `start_place`.
   Omitted if `startPlace` is null. Not re-validated against `start_place` later — if the user
   edits the place afterwards the two can drift; acceptable since this field is sugar, not the
   source of truth.

Rejected: a key-per-field shape (`weather_temp_min`, `weather_temp_max`, `weather_code`) like
`start_lat`/`start_lon` — frontmatter is already bloated and coordinates earn that treatment
because they're load-bearing; weather is sugar, so one packed scalar it is.

Carried on `DiaryEntry` as a small value record, e.g. `domain.Weather(int tempMin, int tempMax,
int code, String place)`, with `Weather.parse(String raw)` / `Weather.toRaw()`
(`parse`/`toRaw` tolerate the 3- or 4-field form; an unparseable string is treated as absent
rather than thrown, consistent with tolerated-but-flagged values elsewhere, e.g. `url_<verb>`).
`null` on the entry means "no weather recorded".

## WMO code → label/emoji

A small bucket table, not all ~30 raw WMO codes — in a new `domain.WeatherCode` enum
(parallel to `domain.Activity`): `CLEAR`, `PARTLY_CLOUDY`, `CLOUDY`, `FOG`, `DRIZZLE`, `RAIN`,
`SNOW`, `THUNDERSTORM`, each with an emoji (`emojiFor`, like `Activity.emojiFor`) and a WMO code
range it claims:

| Bucket | WMO codes | Emoji |
|---|---|---|
| Clear | 0 | ☀️ |
| Partly cloudy | 1, 2 | 🌤️ |
| Cloudy | 3 | ☁️ |
| Fog | 45, 48 | 🌫️ |
| Drizzle | 51, 53, 55, 56, 57 | 🌦️ |
| Rain | 61, 63, 65, 66, 67, 80, 81, 82 | 🌧️ |
| Snow | 71, 73, 75, 77, 85, 86 | ❄️ |
| Thunderstorm | 95, 96, 99 | ⛈️ |

An unrecognized code falls back to `CLOUDY` rather than erroring. Labels go in
`i18n/export.properties` / `export_de.properties` as `weather.<key>` (lowercase enum name),
same mechanism as `activity.<key>`, defaulting to the English base when a language omits one.

## Fetch client: `weather.OpenMeteoClient` (no JavaFX, mirrors `map.PlaceLookup`)

- `java.net.http`, Jackson for parsing (both already on the classpath).
- Timeout ~5 s, same ballpark as `PlaceLookup`. No rate limiting needed (one request per save,
  well under Open-Meteo's informal non-commercial ceiling) — unlike `PlaceLookup`'s 1 req/s
  guard against Nominatim's usage policy.
- `User-Agent` reused from wherever `PlaceLookup` gets its `TripTale/<version> (+...)` string
  (shared helper if not already factored out).
- Returns a result/exception the caller can log at DEBUG and otherwise ignore — never a modal
  alert, since this is background enrichment, not a user-initiated action.

## Auto-fetch on save

Weather fills in automatically on a plain save, **and** can be fetched/cleared by hand in the
Coordinator dialog (added after the first cut of this todo shipped — see "Coordinator row"
below):

- Hook into `MainController.saveTheDayIfPossible()` (~line 1216 area), after the entry is
  persisted: if the just-saved entry has `startLat`/`startLon` set and **no** `weather` yet,
  kick off an async fetch (`Task`, background thread, same shape as `ConnectivityService`'s
  check — never block the FX thread).
- On success: set the result on the entry and persist again (`MarkdownStore.saveEntry` +
  `addPending(...)`), so the weather shows up without the user doing anything, folded into
  whatever Smart Sync already has pending. This is a **second**, independent save after the
  user's own save completes — it must not clobber any edits made in the meantime (reload the
  current on-disk entry, or guard on "controller still showing the same date", before writing).
  The controller's own `weather`/`baselineWeather` fields are updated too (only when the form
  is still showing that exact day), so `isDirty()` doesn't flag a change the user never made.
- On failure (network, offline, date outside the ~92-day forecast window): leave `weather`
  unset, log at DEBUG, no UI feedback at all — silent is fine for the automatic path.
- The automatic path never overwrites an existing `weather` value — only fires when the field
  is absent. The manual 🔄 in the Coordinator dialog (below) **does** overwrite, same as the
  place lookup's 🔍.
- Coordinates source: **`start_lat`/`start_lon` only**, even when a stop point exists — weather
  doesn't vary enough within a day's travel to justify a second lookup, and it sidesteps the
  "what if there's no stop point" case entirely.
- Re-fetch: the automatic path only triggers by *adding* `start_lat`/`start_lon` to an entry
  that didn't have them yet (since the trigger is "coordinates present, weather absent").
  Changing an existing start point does **not** auto-re-fetch — use 🔄 in the Coordinator.

## Coordinator row (manual fetch/clear, added after the first cut)

`ui.dialog.CoordinatesDialog` gained a weather row between the Start/End rows and "Parse
from:", resolving the "no manual control" gap from the first cut:

- Shows the day's weather named with its date *and* the place it was fetched for — without the
  place suffix it read as disconnected from the Start point, so this was added in a follow-up
  pass: `"🌫 12–25° foggy on October 16th 2026 @ Rome, IT"` (`w.place()` appended as `" @ " +
  place"` only when non-null; always English — the UI stays English per the trip-language rule;
  `ExportTexts.of(TripLanguage.EN)` is reused purely as a label source, not because this dialog
  participates in the trip-language setting). Empty/fetching states preview the **current**
  Start row's place (else its DDM coordinates, else nothing) rather than just the date, so it's
  clear *where* 🔄 would fetch for before the first fetch: `"Not recorded for October 16th 2026
  yet — fetch for Rome, IT."` / `"Fetching weather for October 16th 2026 (Rome, IT)…"`.
- 🔄 **Update** fetches for the **Start** row's *current* field values (not necessarily what's
  saved yet — same "operate on the live row" precedent as 🔍's forward/reverse lookup) and
  **overwrites without asking**, like 🔍. Disabled offline, without a start point, or without a
  date (same gating/tooltip style as 🔍). Echoes the Start row's current place into the result,
  same as the automatic path.
- 🗑 **Clear** sets it back to absent; disabled when already absent.
- The weather row's `HBox` needs an explicit `.setAlignment(Pos.CENTER_LEFT)` — unlike the
  Start/End rows (individual same-height controls placed directly in `GridPane` cells, which
  align fine on the default baseline), this row collapses label + 2 buttons into one wide `HBox`
  spanning 7 columns, and `HBox`'s default top-left alignment visibly misaligns a plain `Label`
  against taller `Button`s. Every other dialog's multi-control `HBox` row in this codebase
  already sets this explicitly (`ImageViewerDialog`, `ExportDiaryDialog`, `TripDetailsDialog`,
  etc.) — this one just missed it when first added.
- The dialog's `Result` gained a `weather` field, applied by `MainController.onCoordinates()`
  like the other fields, then persisted through the normal `saveTheDayIfPossible()` path — this
  is why `weather`/`baselineWeather` needed to join the dirty-tracking fields (`isDirty()`):
  without that, closing the dialog after only touching the weather row (not lat/lon/place)
  wouldn't register as a change, and `saveTheDayIfPossible()` would skip the save.

## Tofu glyph in the weather row (JavaFX, not a data problem)

`domain.WeatherCode`'s `FOG`/`DRIZZLE`/`RAIN`/`PARTLY_CLOUDY` emoji were defined with a trailing
`\uFE0F` variation selector (`"🌫️"` etc.) and rendered as a missing-glyph box in the Coordinator
dialog. Root cause: these four are Supplementary Multilingual Plane pictographs that already
default to emoji presentation — the selector was redundant everywhere — but JavaFX's text
renderer appears to choke on the invisible trailing codepoint and draws a tofu box for it
specifically (the base glyph renders fine). Fixed by dropping `\uFE0F` from just these four
(`"🌫"`, `"🌦"`, `"🌧"`, `"🌤"`); since browsers already default these codepoints to color
regardless of the selector, exports (Markdown/HTML/WordPress, which share `WeatherCode.emoji()`
with the dialog) are visually unaffected. `CLEAR`/`CLOUDY`/`SNOW`/`THUNDERSTORM`
(`☀️☁️❄️⛈️`) were left untouched: their default presentation genuinely is plain text, so the
selector isn't redundant there, and they weren't confirmed broken.

## Export placement (`export.EntryFacts`, todo 53/58)

Joins the existing per-entry fact strip (date, activity, start/stop place) as one more
`Item`, not a separate line:

- Emoji from `WeatherCode.emojiFor(code)`.
- Value: `"15–22°"` (min–max, en dash, degree sign, no space — matches the terse style of the
  other facts); if `tempMin == tempMax`, just `"15°"`.
- Note: the localized bucket label (`texts.weatherLabel(code)`, wired like
  `texts.activityLabel(activity)`).
- No `detail` (no coordinates-style secondary line needed).
- Inline form for the Markdown byline: `"🌤️ 15–22° partly cloudy"`.
- Shows in all three exporters (Markdown byline, HTML ticket stub, WordPress columns) since
  it's a plain `EntryFacts.Item` — no exporter-specific code needed beyond what todo 53
  already built.

## Open questions / later

- Archive API fallback for entries older than ~92 days (noted above, deliberately deferred).
- Whether `weather.OpenMeteoClient` and `map.PlaceLookup` should share a small `http`/user-agent
  helper package now that there'd be two keyless `java.net.http` clients with near-identical
  shapes — not done as part of this todo to avoid scope creep.

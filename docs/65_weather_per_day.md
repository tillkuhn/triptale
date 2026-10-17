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
weather: "15,22,1"
weather: "15,22,1,Soest, DE"
```

Comma-separated, fixed position, **always double-quoted** (avoids any YAML flow-sequence
ambiguity around commas):

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

No manual button (unlike the Coordinator's 🔍) — weather fills in automatically:

- Hook into `MainController.saveTheDayIfPossible()` (~line 1216 area), after the entry is
  persisted: if the just-saved entry has `startLat`/`startLon` set and **no** `weather` yet,
  kick off an async fetch (`Task`, background thread, same shape as `ConnectivityService`'s
  check — never block the FX thread).
- On success: set the result on the entry and persist again (`MarkdownStore.saveEntry` +
  `addPending(...)`), so the weather shows up without the user doing anything, folded into
  whatever Smart Sync already has pending. This is a **second**, independent save after the
  user's own save completes — it must not clobber any edits made in the meantime (reload the
  current on-disk entry, or guard on "controller still showing the same date", before writing).
- On failure (network, offline, date outside the ~92-day forecast window): leave `weather`
  unset, log at DEBUG, no UI feedback at all — silent is fine for a sugar field with no retry
  button in this iteration.
- Never overwrites an existing `weather` value (including a value the user hand-typed or
  cleared) — only fires when the field is absent, same "don't fight manual edits" rule as the
  Coordinator's 🗑.
- Coordinates source: **`start_lat`/`start_lon` only**, even when a stop point exists — weather
  doesn't vary enough within a day's travel to justify a second lookup, and it sidesteps the
  "what if there's no stop point" case entirely.
- Re-fetch: only triggered by *adding* `start_lat`/`start_lon` to an entry that didn't have
  them yet (since the trigger is "coordinates present, weather absent"). Changing an existing
  start point does **not** re-fetch (weather is already set) — a manual re-fetch path is future
  work if this turns out to matter in practice.

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
- A manual re-fetch/retry action, if the silent auto-fetch turns out to miss too often in
  practice (flaky network, entries saved before coordinates are set).
- Whether `weather.OpenMeteoClient` and `map.PlaceLookup` should share a small `http`/user-agent
  helper package now that there'd be two keyless `java.net.http` clients with near-identical
  shapes — not done as part of this todo to avoid scope creep.

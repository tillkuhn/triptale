# 72 Elevation alongside weather

Capture the station elevation Open-Meteo already returns in its forecast/archive response
(`"elevation":38.0` at the JSON root, meters, no extra request needed) and show it below the
existing weather fact in exports, e.g. `124 m a.s.l.`

## Decisions

**Packed value shape.** `domain.Weather` currently stores `"min;max;code[;place]"`. New shape:
`"min;max;code;elevation[;place]"` — elevation goes **after `code`, before `place`**, not right
after the temperatures. `min;max;code` is the "what happened" core (temp + WMO condition,
always read together); `elevation`/`place` are both "where" context, grouped at the tail. This
keeps the first three fields stable and lets a hypothetical future field extend the tail rather
than splicing the core again.

To keep both optional tail fields (`elevation`, `place`) unambiguously positional — not just for
migrated data, but for every value this and future app versions write — `elevation` is **never
skipped once `place` is present**, even when elevation itself is unknown: it's written as an
explicit empty field instead (`"15;22;1;;Essen, DE"`). So:

| Shape | Meaning |
|---|---|
| `min;max;code` | neither elevation nor place |
| `min;max;code;elevation` | elevation, no place |
| `min;max;code;;place` | place, no elevation (empty 4th field) |
| `min;max;code;elevation;place` | both |

`parse()` splits on `;` with `limit=5` (was `limit=4`). `parts.length==4` always means
"elevation present, no place" (place-only is always written as 5 parts with an empty 4th slot),
so there's no runtime ambiguity for anything this scheme itself produces. `Weather.toRaw()` /
`Weather.parse()` are the only places that need to change for the data model; `elevation` is a
nullable `Integer` (meters, rounded, same convention as the existing `altitude` entry field —
not `Double`, since "not recorded" is the only reason it'd be absent, no real reading is ever
fractional-only-when-missing like the `Double`-means-unset fields elsewhere).

**No backward-compat parsing for the old 4-field place form.** Old data (`"15;22;1;Essen, DE"`)
is positionally identical to the new "elevation, no place" 4-field case and can't be told apart
reliably — consistent with this project's existing precedent (coordinate key rename,
`url_track` rename: ad-hoc one-time script, no fallback shipped in the app, see `AGENTS.md`). An
unmigrated old value just fails `Integer.parseInt` on the place text and is treated as absent
(same tolerant behavior `Weather.parse` already has for any unparseable value) — no crash, no
silent corruption, just a dropped weather line until the data dir is migrated.

**Migration script**: `scripts/migrate_weather_elevation.py` (same shape as
`scripts/migrate_belongs_to.py` — argparse, `--dry-run`, walks `<data-dir>/<year>/<slug>/*.md`).
Reformats every legacy `weather: "min;max;code;place"` value into the new, disambiguated
`"min;max;code;;place"` form (explicit empty elevation slot) so it stays parseable under the new
scheme. **Reformat only — no network calls, no elevation backfill.** Historical entries simply
have `elevation == null` after migration; elevation for an existing day can be backfilled later,
one entry at a time, via the Coordinator's existing 🔄 (which will now capture elevation for
free, from the same forecast/archive response, once `OpenMeteoClient` reads it). Rationale:
keeps the migration script simple/offline, avoids rate-limiting/caching logic for a
one-time, non-essential bulk backfill.

**`weather.OpenMeteoClient`**: `parse(String body, LocalDate date)` additionally reads
`root.path("elevation")` (present in both the forecast and archive response shapes — verified
live against `api.open-meteo.com/v1/forecast`) and rounds it into the new `Weather.elevation`
field. No new request, no new endpoint.

**Export label.** New `i18n/export.properties` key `weather.elevation` (English: `"a.s.l."`,
the standard geography abbreviation for "above sea level" — not `"AMSL"`, which reads as an
aviation term, and not spelled out). German (`export_de.properties`): `"üNN"` (über Normalnull),
already the natural, unambiguous abbreviation. Rendered as e.g. `"124 m a.s.l."` /
`"124 m üNN"`, via `ExportTexts` (same `weatherLabel`-style lookup, defaulting to English if a
language omits the key — though both ship it from day one here).

**Placement in `export.EntryFacts`.** Not a standalone fact — a `detail` line on the existing
weather `Item` (`EntryFacts.weather()`), the same slot `location()` uses for the muted
coordinates line under a place name. Renders as a third, smaller line under temp/condition in
the HTML ticket stub, and folds into the Markdown byline:
`"🌤️ 15–22° partly cloudy · 124 m a.s.l."`. Omitted (no `detail`) when `elevation == null`.

**`ui.dialog.CoordinatesDialog` weather row.** The existing preview text
(`"🌫 12–25° foggy on October 16th 2026 @ Rome, IT"`) gains elevation when present:
`"... @ Rome, IT · 124 m a.s.l."` (same `" · "` join style as the export byline).

**`MainController` toolbar weather badge.** The small badge button (`weatherBadge`, e.g.
`"🌤️ 24°"`) stays as-is — already tight for space. Its **tooltip**
(`weatherTooltip`) gains elevation, e.g. `"🌤️ 15–24° · 124 m a.s.l."`, so there's still a way
to see it without opening the Coordinator.

## Open / not decided here

- Exact wording/format for `weatherTooltip`'s elevation segment (separator style, whether it
  reuses the export `ExportTexts` label or a plain hardcoded `"m"` since the toolbar/dialog
  stay English regardless of trip language, per the existing `CoordinatesDialog` precedent of
  reusing `ExportTexts.of(TripLanguage.EN)` purely as a label source).
- Whether `OpenMeteoClientTest` fixtures need updating for the new `elevation` root field in
  mocked responses (likely yes, to avoid a null if tests assert on it).

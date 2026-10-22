# Todo 70 — Derive activity from a GPX track's Komoot `<type>`

## Problem

Komoot's "export tour" GPX files carry an optional `<type>` element as a direct child of
`<trk>` (sibling of `<name>` and `<trkseg>`), e.g.:

```xml
<trk>
  <name>🌉 Familientaugliche Wanderung rund um die Müngstener Brücke 🌉</name>
  <type>hike</type>
  <trkseg>...</trkseg>
</trk>
```

`GpxImport` ignored it entirely. Todo 24/66's import dialog derives title/coords/distance/
altitude but never activity, so every GPX import left `activity` untouched even though the
source data usually tells us hiking vs. cycling outright.

## Survey (506 real exports from `export-komoot/export/tour_recorded`)

```
303 hike
163 touring_bicycle
 30 mtb
  7 mtb_easy
  1 mtb_advanced
  1 mountaineering
  1 snowboard
```

(`text/html` also matches a naive `<type>` grep but belongs to `<link>`/metadata elements, not
`<trk>` — every file has exactly one `<trk>` and exactly one `<type>` under it.)

## Decision: mapping table

Only the two curated `Activity` values the app actually has a close fit for are derived;
anything without a good fit is left unmapped (`Parsed.activity() == null`, existing/typed-in
value untouched):

| Komoot `<type>` | `Activity` |
|---|---|
| `hike` | `HIKING` |
| `mountaineering` | `HIKING` (closest fit — on-foot, no dedicated "climbing"/"alpine" activity exists) |
| `touring_bicycle` | `CYCLING` |
| `mtb`, `mtb_easy`, `mtb_advanced` | `CYCLING` |
| `snowboard` | *(unmapped — no winter-sport `Activity` value exists)* |
| anything else/missing | *(unmapped)* |

The map lives as a `Map<String, Activity>` in `GpxImport` (lowercase, trimmed lookup key) —
add new Komoot type strings there if a future export surfaces one (e.g. `road_bicycle`,
`e_mtb`, `jogging`) rather than guessing ahead of real data.

## Design

1. **`GpxImport.Parsed`** gets a new field `String activity` — already the mapped `Activity`
   key (`"hiking"`/`"cycling"`), or `null` when the GPX had no `<type>` or an unrecognized one.
   No change to the "empty when X" failure modes; a missing/unmapped `<type>` never aborts the
   parse, it just leaves this field `null`.
2. **`GpxImportDialog`** gets a 7th row, "Activity", following the exact pattern of the
   altitude row: omitted entirely when `parsed.activity() == null`. Default checkbox state
   follows the same "checked when the old value is empty" rule as the other fields — checked
   when `existing.activity() == null`. Rendered with `Activity.emojiFor`/`labelFor` like
   `ActivityChoices` does, not a bare string.
3. **`MainController.onImportGpx`** applies it the same way as the other fields:
   `if (sel.activity()) activityCombo.setValue(p.activity());` — after `datePicker.setValue(...)`
   has already run `loadEntry()` and repopulated the combo for the new day.

## Non-goals

- No change to which GPX elements are read for anything else (title/coords/distance/altitude
  parsing untouched).
- No attempt to map every possible Komoot sport type — only what's evidenced by real exports
  plus the two target activities the user asked for.
- Trip-level default activity (todo 57) is unaffected — this only ever touches the entry's own
  `activity`, same as every other GPX-derived field.

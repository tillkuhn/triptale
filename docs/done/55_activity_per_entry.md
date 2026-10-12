# 55 Activity per entry

Each tale gets one main activity of the day (frontmatter `activity`, deliberately single-valued).
It puts a day into context and lets todo 53 pick fitting icons for the distance/altitude facts.
Design agreed in a grilling session on 2026-10-12.

## Values

Modeled like the link verbs of todo 09 (`domain.LinkKind`): enum `domain.Activity` with key,
emoji and English label, declared alphabetically by key.

| key          | emoji | EN label     | DE label (export) |
|--------------|-------|--------------|-------------------|
| backpacking  | 🎒    | Backpacking  | –                 |
| bikepacking  | 🚵    | Bikepacking  | –                 |
| cycling      | 🚴    | Cycling      | Radeln            |
| hiking       | 🥾    | Hiking       | Wandern           |
| railtripping | 🚆    | Railtripping | –                 |
| relaxing     | 🏖    | Relaxing     | –                 |
| roadtripping | 🚗    | Roadtripping | –                 |
| sightseeing  | 🏰    | Sightseeing  | –                 |

- English label = key with uppercase first letter.
- Only `cycling` and `hiking` are translated (`activity.<key>` in `export_de.properties`); the
  others stay English in German exports too (bundle falls back to `export.properties`).
- Emojis avoid ones already used elsewhere (🧭 explore, 🏛 visit, 🗺 track, 📷 impressions,
  📍 coordinates, 🛏 sleep link).

## Null and unknown values

- **Null = not set.** Nothing is written to frontmatter. The UI shows `😴 No activity yet` — pure
  UI sugar, it does *not* mean "rest day" (a rest day is `relaxing`). Exports omit the activity.
- **Unknown values are tolerated** (hand-edited in Tolaria, or from a newer app version): kept
  verbatim on load and save, shown as `❓ Kayaking` (❓ + capitalized value).
- **Normalization on load:** the value is trimmed; blank counts as null. If the lowercased value
  matches a known key it is stored as that lowercase key (`Hiking` → `hiking`, a one-time diff).
  Unknown values keep their original spelling.

## Storage

`activity` is the first frontmatter key of an entry (alphabetical order, before `altitude`),
written only when not null. `DiaryEntry.activity` is a `String` (not the enum) so unknown values
survive.

## UI

Non-editable `ComboBox` without label in the entry form grid, row 1, column 4 (after Altitude,
the cell was empty; the title above spans columns 1–4, so both rows stay aligned).
Items: `😴 No activity yet`, then the 8 curated values with emoji; an unknown value of the current
entry is added as one extra item for that entry only. Changing it marks the entry dirty like the
other fields.

## Exports

Not part of this todo. `ExportTexts.activity(key)` returns emoji + localized label so todo 53's
fact header can use it; templates are unchanged.

## Relevant files

- `domain/Activity.java`, `domain/DiaryEntry.java`
- `storage/MarkdownStore.java` (`saveEntry`, `parseEntry`)
- `ui/MainController.java`, `fxml/main.fxml`
- `i18n/ExportTexts.java`, `i18n/export*.properties`

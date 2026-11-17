# 96 Trip Totals: Track totals per default activity (L)

# Trip Totals: track totals per default activity

## Problem
Totals sum all entries regardless of activity: two 50 km bike days plus a 1000 km train ride show 1100 km. Standard use case is bikepacking, so totals should be scoped to the trip's default activity.

## Decisions
- **Flag**: checkbox "Track Totals" behind Default activity in New Trip / Trip Details. Disabled and shown unchecked while no activity is set; unchecked by default.
- **Storage**: trip README frontmatter `track_totals: true`, written only when true (absent = false), alphabetical key order. If the activity is cleared later the stored flag is kept but ignored (effective = flag && activity set), so re-picking the activity restores it.
- **Counting rule**: only entries whose `activity` equals the trip's default activity count.
- **Export** (`DiaryExporter.statsLine`, replaces the existing bullet when effective): `Distanz: 412,6 km Bikepacking, 4605 m bergauf` (English: `Distance: 412.6 km Bikepacking, 4605 m climbed`). No `> 0` requirement for distance: 0 km is shown with the activity, as a reminder that tracking is on (enter numbers or disable it). Climb dropped if not > 0. No fallback to the old all-activity bullet. Off = no stats bullet at all (revised: the old all-activity sum mixed e.g. driving and walking, which means nothing).
- **Activity name in export**: plain name, no emoji, in the trip language. **Check the German wording with the user before adding any `export_de.properties` keys**; for some (e.g. Bikepacking) the English word stays even in German.
- **Editor** (`MainController` talesLabel, ~line 1359): append after the interpunct, e.g. `🐉 Tales · 383 words updated 16 days ago · Σ 110 km of Bikepacking so far (140 m up)`; altitude omitted if 0. Shown only if tracking is effective and the day's own activity is the default activity. Sum = all default-activity entries with date <= the day; current day uses live form values, earlier days the saved files.
- **Trip Details** (`TripDetailsDialog`): merge "Total distance" and "Total altitude" into one line, e.g. `Totals: 412.6 km Bikepacking, 4605 m (avg 68.8 km / active day, 6 days)` (fixed label; the activity sits in the value so the label column doesn't change width), altitude dropped if 0. Counts only that activity's days and updates live with the checkbox/combo. Disabled: keep the row, show "tracking off" hint.

## Files
`export/DiaryExporter.java` (statsLine), `ui/dialog/TripDetailsDialog.java` (Stats), `ui/dialog/NewTripDialog.java`, `ui/MainController.java`, `storage/MarkdownStore.java` (trip frontmatter), `domain/Trip`, `domain/Activity`, `i18n/export*.properties`, AGENTS.md frontmatter key list.

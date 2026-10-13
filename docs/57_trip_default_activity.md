# 57 Trip default activity

Follow-up to todo 55 (activity per entry, `docs/55_activity_per_entry.md`). On trips like
bikepacking tours most days share the same activity, so picking it for every day is tedious. A
trip can now name a default activity that new entries start with. Design agreed in a grilling
session on 2026-10-12.

## Explicit pre-fill, not implicit inheritance

The trip's activity is **copied into new entries** and stored there; entries never fall back to
the trip value at read time. Rejected alternative: entries without `activity` inherit the trip's
value (change once, every day follows). Reasons:

- "Unset" stays expressible: with inheritance a missing key means "inherit", so a genuinely
  unset day would need a sentinel (`activity: none`), breaking "null = not set" from todo 55.
- Entry files stay self-contained: the data dir doubles as a Tolaria vault, and exporters / todo
  53 / `git grep` would otherwise all need to know the trip fallback.
- No hidden changes: editing the trip doesn't silently rewrite 20 past days.

## Behavior

- Trip activity is optional. No trip activity → new days start with `😴 No activity yet`, as
  before.
- Pre-fill applies **only to days without an entry file yet** ("here be dragons" days). The
  pre-filled value is part of the form baseline, so it doesn't mark the form dirty; it's written
  on the first save. The user can still switch to `😴 No activity yet` before saving.
- Existing entries without an activity are left alone (may be deliberate). No backfill action —
  there is little history yet, older days are set by hand.
- Other paths that create entries use the trip activity too: the first entry from the New Trip
  dialog, GPX import into a new day.
- Changing the trip activity later affects only days created afterwards.

## Storage

Trip `README.md` frontmatter key `activity` — same key as on entries, deliberately not
`default_activity` (the trip meaning is clear from `type: Trip`). Same normalization as entries
(`Activity.normalize`: known values lowercase, unknown kept verbatim, blank = null). Written first
(alphabetical, before `end_date`), only when set. `Trip` record gets an `activity` field.

## UI

- `Default activity:` row in **Trip Details** (after `Start / end date`) and **New Trip** (after
  `Start date`).
- Same ComboBox look as the entry form (`😴 No activity yet`, the 8 curated values, an unknown
  value as an extra `❓ Value` item). Extract the rendering into a shared `ui` helper so the entry
  form and both dialogs can't drift apart.

## Exports

Not shown in exports — it's only a default for new days, every entry carries its own value. A
trip-header activity (`🚵 Bikepacking` next to dates/total distance) could come with todo 53's
fact header layout.

## Relevant files

- `domain/Trip.java`, `domain/Activity.java`
- `storage/MarkdownStore.java` (`saveTrip`, `loadTrip`)
- `ui/dialog/NewTripDialog.java` (`Spec`), `ui/dialog/TripDetailsDialog.java`
- `ui/MainController.java` (`showActivity`, `loadEntry`, new-trip first entry, GPX import)

# Todo 25: Delete Tale Entry

Design from a grill-me session. Surfaced during the todo 24 grill-me session: a GPX import
that lands on the wrong day (or the wrong trip) had no in-app way to undo — the user had to go
to the filesystem/git directly. See `docs/24_gpx_import_tale_entry.md` decision 3.

## Scope

Entry-only delete for this pass. Delete-trip (todo 25's "also consider delete-trip") is
deferred to a follow-up todo — it needs its own design (recursive removal, what happens to a
trip with committed entries, etc.) and isn't needed to solve the wrong-day-import problem that
raised this todo.

## UI

- New menu item **"🗑 Delete Tale Entry…"** in the "Tale Entries" menu group, placed after
  Save/Copy Tale (with a separator), mirroring the placement style of Import GPX/View Source.
- Enabled only when an entry file exists on disk for the currently selected trip/date — same
  disk-state gating `viewSourceMenuItem`/GPX-overwrite-confirmation already use, kept via
  `entryExists` in `loadEntry()`.
- Confirmation: a plain Yes/No `Alert` naming both the date and the entry's title (quoted, on
  its own line — the title alone can be ambiguous, e.g. many entries share the placeholder
  `DiaryEntry.DEFAULT_TITLE`, so the date is still the primary identifier and the title is
  supporting context, omitted when it's blank or still the default), multiline with a widened
  dialog pane (`setMinWidth(420)`) so the text doesn't get clipped, styled via
  `Dialogs.applyStylesheet` like every other dialog in the app.

## Delete mechanics

- `MarkdownStore.deleteEntry(TripRef, LocalDate)` — `Files.delete` on the entry file, wrapped
  so an `IOException` surfaces as `StorageException` (existing convention: unchecked exceptions
  from `storage`, caught at the UI action boundary).
- `GitService.remove(Path file)` — new method using JGit's `RmCommand` with `setCached(true)`
  (index-only; the file is already gone from disk via `deleteEntry`, so there's nothing for a
  working-tree removal to do). Added because `GitService.commitAll()` stages via
  `AddCommand.addFilepattern(".")`, which — unlike modern CLI `git add .` — does **not** pick up
  deletions of already-tracked files. Without this, a deleted-but-previously-committed entry
  would never actually leave the git history.

## Pending/commit batch integration

`MainController.pending` gets a third action constant, `DELETE`, alongside `CREATE`/`UPDATE`.

At delete time, keyed by the same `"{trip.ref().path()}/{date}"` label `onSave` already uses:

- If `pending` currently holds `CREATE` for that label — the file was only ever a pending,
  never-committed addition this session, so git never tracked it. Just remove the label from
  `pending`; no `GitService.remove()` call needed (there's nothing in the index to unstage).
- Otherwise (an `UPDATE` label, or no pending label at all — i.e. committed in a prior session)
  — the file is tracked in git. Call `gitService.remove(...)`, then set `pending` for that label
  to `DELETE` (overwriting any prior `UPDATE`).

`buildCommitMessage()` gets a `Delete: ...` group alongside `Create`/`Update`, same inline/
truncated-list formatting.

## Post-delete UI state

After delete, the code re-enters the existing empty-state path rather than hand-resetting form
fields: since `entryExists` is now `false` and the trip/date selection hasn't moved, calling
`loadEntry()` again does exactly what opening a day with no entry file already does —
`DiaryEntry.empty(date)` fills the form, `snapshotBaseline()` runs, `isDirty()` comes back
`false`. This means no stale "unsaved changes" prompt fires on the next navigation, and no new
empty-state logic had to be written.

## Non-goals

- No delete-trip in this pass (see Scope above).
- No undo/trash — deletion is immediate on disk once confirmed; the pending-commit step is
  about getting the removal into git history, not about reversibility.
- No change to `confirmNavigateAway`'s dirty-check semantics — if the form has unsaved edits
  when the user chooses Delete, those edits are simply discarded along with the file itself, same
  spirit as the existing GPX-overwrite confirmation.

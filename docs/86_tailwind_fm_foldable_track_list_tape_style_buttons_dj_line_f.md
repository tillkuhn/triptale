# 86 Tailwind FM: foldable track list, tape-style buttons, DJ line fix

# Tailwind FM: track list, tape buttons, DJ line fix

## Decisions
- Listing uses `Track.title()` (extension dropped, `_` to blank). No ID3 reading: it needs
  one file read per track; the filename is enough. `RadioPlayer.label()` keeps using ID3
  for the now-playing line via JavaFX `Media`.
- Track list is a foldable section (e.g. `TitledPane`), collapsed by default, so the radio
  stays small. Columns: name, size. Size is shown human-friendly (KB/MB) but the column
  sorts by bytes. Sortable by name (nice-to-have: by size).
- Clicking a row plays that track (new `RadioPlayer.play(Track)` entry point, public); after
  it ends the player continues with a random track as now. Fine-tuning (queue, shuffle
  semantics) comes later.
- Buttons become symbol-only: play/pause, stop, next (tape recorder style). Keep the fixed
  width so play/pause doesn't make the row jump; Close stays. Add tooltips for the symbols.
- Don't widen the window: content width stays `LOGO_WIDTH` (400) + padding. Expanding the
  list grows the height only; the stage is `setResizable(false)`, so call `sizeToScene()`
  after folding/unfolding.
- `RadioLibrary` needs to expose the track list with file size (stat per file, cheap).

## DJ line disappearing / clipped buttons
Two separate effects, seen in screenshots:
1. **Short title, no DJ line, stays empty for a while: by design.** `RadioPlayer.currentDjLine`
   is cleared in `play()` and only set again in `onAirMessage()` when `announce` is true
   (always for the first track after switch-on, then one in four). Decision: keep showing
   the last DJ line until the next announced one replaces it (clear it only on stop), so the line
   doesn't look broken. `play()` must no longer reset `currentDjLine`.
2. **Long title wraps to two lines, bottom buttons are clipped.** `nowPlaying` wraps, the VBox
   needs more height than the stage (sized once at first show, `setResizable(false)`).
Decision: no wrapping anywhere. `nowPlaying` and `djLine` become single-line labels with
`setWrapText(false)` and `setTextOverrun(OverrunStyle.ELLIPSIS)` (cut with an ellipsis, the
rest can go), `maxWidth` = `LOGO_WIDTH`. `DJ_LINE_HEIGHT` shrinks to one line. Add the full
text as tooltip. The stage height is then stable.

## Final decisions made while implementing
- The section is called "Record Crate" (DJ theme); the count shows after the first expand.
- Fold/unfold: the stage is non-resizable and macOS ignores `sizeToScene()`/`setHeight` then.
  `fitHeight()` lifts the lock briefly (`setResizable(true)`), sets the height, locks again,
  and runs via `Platform.runLater` so the TitledPane skin has updated first. Animation is off.
- Sorting: columns are non-resizable/non-reorderable (the column divider swallowed header
  clicks and the sort arrow vanished); items go through a `SortedList` bound to the table
  comparator. Title column width is bound to the table width minus size column and scrollbar.
- DJ names: `dj-names.txt` (travel/activity themed, with the "DJ" prefix); one is picked per
  announced line and shown as `DJ Name: "line"` in the window and the status line
  (`Station.djName`). Name and line stay until the next announced one; cleared on stop.
- Equalizer: `EqualizerIcon` got a public constructor (bars, bar width, gap, height); an
  18-bar strip sits between the transport buttons and Close, visible and animated only while
  playing and the window is open.

## Files
`ui/dialog/TailwindFmDialog.java`, `ui/RadioPlayer.java`, `radio/RadioLibrary.java`, `radio/Track.java`.

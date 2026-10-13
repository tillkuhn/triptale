# 37 Keyboard shortcuts for Zoom In / Zoom Out (and the other menu items)

## Problem

View → Zoom In / Zoom Out could only be clicked in the menu. The goal was the usual macOS
shortcuts (Cmd + Plus / Cmd + Minus) and to show them in the menu items.

"Zoom" only changes the tale text area's font size: 13px by default, 10–28px range, 1px per
step, persisted as `taleFontSizePx` in `.state.yml`. The rest of the UI does not scale.

## Decisions (from a grill-me session)

1. **Scope: every menu item that has a shortcut, not just zoom.** Before this change, Cmd + S,
   Cmd + K and Opt + Left / Opt + Right were set up in code on the scene
   (`scene.getAccelerators()` in `MainController.initialize()`), so no menu item showed them.
   All shortcuts are now `accelerator="…"` attributes on the `MenuItem`s in `main.fxml`. JavaFX
   both registers and displays them from there. The code-based setup was removed, so there is
   one way to do shortcuts.
2. **A shortcut fires only while its menu item is enabled.** `ControlAcceleratorSupport` checks
   `menuitem.isDisable()` before firing (JavaFX 23). This replaced the old manual checks and
   changed two behaviors, both on purpose:
   - Cmd + S used to check only `isDirty()`, so it could save an entry with a blank title or
     blank tale text even though the Save button was disabled. It now follows the button
     (`isDirty() && isValidEntry()`).
   - The day-back shortcut used to go past day 1 of the trip. Now, like ◀, it does nothing on
     or before the trip's start date.
3. **German keyboard layout only.** The shortcuts are `Shortcut+Plus` (the German `+` key has its
   own key), `Shortcut+Minus` and `Shortcut+0`. There are no hidden alternatives for the US
   layout (Cmd + `=`) or the number pad; add them as extra scene accelerators if that ever
   matters. A `MenuItem` can only show one shortcut.
4. **Reset View got Cmd + 0,** the usual browser/IDE reset-zoom shortcut.
5. **Zoom In / Zoom Out are never disabled at the limits.** Each zoom reports to the status line
   instead: `Tale text size: 15px (default 13px)`, or `(maximum)` / `(minimum)` / `(default)`, so
   pressing the key at a limit doesn't look like nothing happened. Reset View stays enabled
   because it will reset more settings later.
6. **Day navigation moved from Opt + Left / Opt + Right to Cmd + Opt + Left / Cmd + Opt + Right.**
   JavaFX only runs scene accelerators in the *bubbling* phase, after the focused control had
   its chance (`KeyboardShortcutsHandler.dispatchBubblingEvent`). macOS text fields handle
   Opt + Left / Opt + Right as "move one word", so the old shortcut never fired while typing in
   the tale text or any form field. Nothing in a text field uses Cmd + Opt + arrows. Cmd + [ / ]
   was rejected because `[` is Opt + 5 on a German keyboard.
7. **The toolbar buttons show the shortcuts in tooltips** (◀, ▶, Save, Commit). The text comes
   from the matching menu item's `getAccelerator().getDisplayText()` (`installShortcutTooltip`),
   so `main.fxml` is the only place where each shortcut is defined. Tooltips are lazy, so there
   is no measurable cost.

## Relevant files

- `src/main/resources/fxml/main.fxml`: `accelerator` attributes; `nextDayMenuItem` fx:id added.
- `src/main/java/net/timafe/triptale/ui/MainController.java`: `installShortcutTooltip`,
  `zoomTaleFont`, and the removed scene-accelerator block.

## Open points

- Not yet confirmed on real hardware: that the German `+` key sends `KeyCode.PLUS` to JavaFX on
  macOS (this key mapping is native glass code, not in the sources jar). If Cmd + Plus doesn't
  fire, log the `KeyEvent` from a scene event filter to see what the key actually sends.
- Accelerators belong to the main window's scene, so they don't work while a modal dialog has
  focus. That's the same as before.

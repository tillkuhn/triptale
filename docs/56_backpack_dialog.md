# 56 Open the backpack from the main window

The 🎒 Backpack button (added with todo 55) used to go straight to the "Add to Backpack…" file
chooser. It now opens a backpack dialog for the current day and shows the file count.

## Decisions (grilled 2026-11-04)

| # | Question | Decision |
|---|----------|----------|
| 1 | Actions | Add, Open (double-click / Enter), Reveal in Finder, Copy Path, Delete. No rename: to the 33c sync base a rename is a delete plus a new upload. |
| 2 | Window | Modal (`ui/dialog/BackpackDialog`). Acts on the folder directly, like `InboxDialog`, and returns `changed` so the controller refreshes the counts. |
| 3 | What counts | Regular, non-hidden files directly in the day folder, `tripmap.png` included. Hidden files (`.DS_Store`, a pull's `.part` leftovers) and subfolders are ignored: a backpack is flat. |
| 4 | Kind column | Mapped by extension, not MIME probing (`attachments.FileKind`): 🖼 Image (jpg jpeg png heic webp gif), 🗺 Track (gpx fit kml tcx), 📄 Text (txt md), 📕 PDF, 🎬 Video (mov mp4), 📦 Other. |
| 5 | Title | "🎒 Your backpack for Day N: Title" via `UiText.entryLabel` (shared with `EntryRangePicker`): ISO date without a trip start, friendly date for a blank/default title. Uses the main window's title field, so unsaved titles show. Shown in the window title bar (the OS truncates it), no header band, so the dialog starts with the table. |
| 6 | Changed column | `yyyy-MM-dd HH:mm`, local time. Size is humanized (`UiText.fileSize`) but sorts by bytes. |
| 7 | Empty day | The button always opens the dialog; empty placeholder, Add focused. |
| 8 | Menu / button | Menu item renamed to "🎒 Open Backpack…" (same action as the button). Button reads `🎒 Backpack (3)`, plain `🎒 Backpack` at 0. The count refreshes with the Impressions count (after navigation, adds, imports, saved maps). |
| 9 | Delete | Single file only, Delete button or ⌫/Delete key, YES/NO confirmation, permanent. Only the local file goes: cloud sync removes it from the bucket via the sync base (todo 33c), git mode sees an ordinary deletion. `AttachmentsDir.deleteFile` refuses anything outside the day folder. |
| 10 | Cloud-only files | Out of scope: the dialog lists local files only. Files in the bucket that weren't pulled yet don't show and aren't counted; ⛅ Sync Backpack fixes that. |

## Implementation

- `attachments.AttachmentsDir.dayFiles(ref, date)` → `List<BackpackFile>` (sorted by name),
  `deleteFile(ref, date, name)`.
- `attachments.BackpackFile` (path, size, modified; `name()`, `kind()`), `attachments.FileKind`.
- `ui.dialog.BackpackDialog`, `MainController.onOpenBackpack` / `updateBackpackButton`.
- Reveal in Finder uses `open -R` on macOS (selects the file); elsewhere it opens the day folder.

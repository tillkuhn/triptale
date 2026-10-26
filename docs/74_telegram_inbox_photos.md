# Todo 74 — Telegram inbox: receive photos, not just text

## Problem

Todo 68 shipped the mobile inbox as **text only**; photos were an explicit non-goal ("fast-follow;
will need to reuse `attachments.AttachmentsDir` once it lands" — `docs/68_mobile_inbox.md`). This
todo is that fast-follow: a photo sent to the configured Telegram bot should become a pending
inbox item that can be attached to a day's `attachments/` folder, the same way a text message
becomes a pending note attached to a day's tale body.

## Decisions

All resolved in one grill-me session (see TODO.md for the one-line summary); no open questions
remain for v1.

- **Download timing: eager, during `poll()`.** Same as text items — the whole point of the inbox
  is "capture now, organize later," so the photo must survive on disk from the moment it's seen,
  not depend on Telegram's `file_id`/`file_path` still being resolvable whenever the user finally
  opens the Inbox dialog.
- **Storage shape: same-stem `.jpg` sidecar next to the `.txt`**, exactly mirroring the `.json` raw
  payload sidecar from todo 73. The `.txt` holds the caption (`message.caption`, possibly empty —
  not every photo has one); `listPending()` keeps globbing `*.txt` only, unchanged.
  `InboxDir.writeItem` gained an overload taking `byte[] photo`; `markProcessed`/`markDiscarded`
  now carry along both the `.json` and `.jpg` sidecar if present.
- **`InboxItem` gained `Optional<Path> photo`** rather than a sealed-type split — `parse()` just
  checks for a same-stem `.jpg` next to the `.txt`, identical pattern to the sidecar lookup already
  in `InboxDir`.
- **No media-group (album) support.** Telegram sends each photo in a multi-photo post as a separate
  message sharing a `media_group_id`; v1 ignores that entirely — each photo becomes its own pending
  item, attached individually. Matches the "simplest thing that works" bias of todo 68/73.
- **Which `PhotoSize`: always the largest.** Telegram compresses photos server-side before any
  `PhotoSize` exists in `message.photo`, so the largest listed entry is strictly the best quality
  obtainable this way — no real tradeoff, and comfortably under the Bot API's 20 MB `getFile`
  download limit for a normal compressed photo message (as opposed to an uncompressed "document"
  upload, which is out of scope).
- **Download failure -> fall back to todo 73's raw capture**, exactly like any other
  non-text/unparsed update: logged, written to `inbox/raw/`, never surfaced as an error, no pending
  item created. Keeps `poll()`'s existing resilience guarantee (one bad item never breaks the rest
  of the batch).
- **On Attach:** the photo is always copied into the day's `attachments/` folder via
  `ImpressionsService.importToAttachments(trip, date, List.of(photo))` — the same call the
  Impression Curator's import flow uses, so dedupe-by-size and gallery visibility (`ImpressionSource`
  already treats `attachments/<day>/` as a photo source) come for free. If the caption is non-blank,
  it's additionally appended under the entry's `## Inbox` heading (`InboxNotes.append`) and the day
  is saved (`saveTheDayIfPossible()`), same as a text item. A caption-less photo needs **no save**
  — there's nothing to persist in the entry's frontmatter/body, and attachments are intentionally
  **never** part of the git pending-commit system (confirmed by tracing every existing
  `attachmentsDir.addFiles`/`importToAttachments` call site — none of them call `addPending`;
  attachments sync via S3/`BucketSyncer`, not git).
- **UI:** `InboxDialog`'s row shows a `javafx.scene.image.Image` thumbnail (220px wide,
  `preserveRatio`, background-loaded) above the caption label; a caption-less photo shows a
  "📷 Photo" placeholder label instead of an empty one.

## Files touched

- `inbox/TelegramInboxClient` — package-private `URI api` constructor overload (test seam, same
  pattern as `OpenMeteoClient`/`PlaceLookup`); `getFile` + binary-download methods; `poll()` branches
  on `message.photo` when there's no `text`.
- `inbox/InboxDir` — `writeItem(timestamp, text, photo, rawJson)` overload; generalized `sidecar()`
  to take an extension; `move()` carries both `.json` and `.jpg` sidecars.
- `inbox/InboxItem` — `Optional<Path> photo` field; `parse()` detects the `.jpg` sidecar.
- `ui/dialog/InboxDialog` — thumbnail rendering.
- `ui/MainController.handleInboxItem` — photo import via `ImpressionsService`; save only when there's
  a non-blank caption.
- Tests: `TelegramInboxClientTest` (new — `TelegramInboxClient` had none before; local `HttpServer`
  with multiple `createContext` paths for the chained `getUpdates`/`getFile`/binary-download calls,
  no Mockito, matching house style), `InboxDirTest` (new), `InboxItemTest` (extended).

## Non-goals (still deferred)

- Media-group/album grouping.
- Location messages.
- Any transport other than Telegram.
- Uncompressed "document" photo uploads (full original resolution).

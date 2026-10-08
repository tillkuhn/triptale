# 47 Impressions / exporter refactor: images as attachments

Today's post images come from `impressionsFilePattern` (`storage.ImpressionsResolver`), a
machine-local glob like `${HOME}/Pictures/${TRIP_YEAR}/…/${DATE}*.jpg`. That means the HTML
export only produces usable output on the one machine that holds the photo library, and no
export can reference an image by URL, because the files exist nowhere else.

Goal: be able to **import images as attachments by selecting them**, and give the exporters a
switch between the legacy filesystem location and the attachments of the current entry.

Attachments already fit: `attachments/<year>/<slug>/<date>-Weekday/` is one folder per entry
day (`attachments.AttachmentsDir`), which maps one-to-one onto "one gallery block per day",
and `attachments.AttachmentSyncer` already pushes them to S3 — so attachment-backed images are
the ones that can get a public URL (see todo 44).

## Design points to settle first

1. **Model the source as an interface, not a boolean.** An `ImpressionSource` with
   `PatternImpressionSource` / `AttachmentImpressionSource`, both returning the same per-date
   list, keeps `DiaryExporter` and the WordPress generator free of branching. A
   `boolean useAttachments` threaded through every exporter signature will rot.
2. **Keep it orthogonal to `ImpressionsMode`** (NONE/FAVES/ALL) — do not cross-product into six
   enum values. But that leaves the open question: **what is "FAVES" for attachments?** A
   `faves/` subfolder, a filename prefix convention, or a list in the entry's frontmatter.
   Undecided, the mode silently stops meaning anything for the new source.
3. **Import is a write, so it follows the pending-commit rule**: copy into the day folder,
   `addPending(...)`, commit via Smart Sync. Never commit from inside the import.
4. **Resize on import.** In `git` attachment-sync mode the files land in the repo as binary
   blobs, and phone originals are 5–10 MB each. This is the same resizing todo 44 stage 2
   needs for the WordPress media upload — build it once. Also decide the collision strategy
   when `IMG_0431.jpg` arrives twice from different cameras.
5. **URL derivation must reuse `AttachmentSyncer`'s object-key logic**, not re-derive it:
   `publicAttachmentBaseUrl` + that key, one source of truth. Otherwise the exported URLs and
   the uploaded objects drift apart.
6. **Keep the legacy source indefinitely**, and add a one-off "import this trip's impressions
   as attachments" action — otherwise existing trips stay on the legacy path forever.

## Relation to todo 44

Separate concerns. Todo 44 only needs to prove that *some* public HTTPS host can serve images
to a handcrafted WordPress post; how the images get there is this todo. Once both land, the
WordPress export can emit gallery blocks pointing at the CDN automatically.

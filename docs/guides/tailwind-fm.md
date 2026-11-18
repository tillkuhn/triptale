# Tailwind FM 📻 (alpha)

I listen to a lot of music on the road and like to quote the soundtrack of a day in a tale.
**Tailwind FM** is TripTale's built-in radio station: it plays random tracks from a pool of
favourites in `<data-dir>/radio/` while you write, and the DJ drops a line now and then. It's
still alpha, so it's off by default: tick **Enable Tailwind FM radio** in ⚙ Edit Settings… and
restart.

- Drop `mp3`, `m4a`, `aac`, `wav` or `aif(f)` files into `radio/` (subfolders are fine;
  **📂 Open Record Crate** gets you there). FLAC and OGG don't play.
- The 🔊 toolbar button plays and pauses; the tooltip shows "Artist – Title" from the tags. When
  a track ends, the next random one starts. The **Tailwind FM** menu also has Next Random Track
  and Stop.
- `radio/` is never committed to git (the app writes its `.gitignore`). To get your tracks onto
  another machine, **⛅ Sync Tracks…** in the Tailwind FM menu syncs them with the backpack's S3 bucket
  under `<prefix>/radio/`, the same way as the backpack: copy both ways, never delete. It's
  separate from Smart Sync on purpose, so music never slows down a diary sync, and it needs
  Backpack sync mode `cloud`.
- On Linux, playback uses the system's FFmpeg libraries; a missing or too new FFmpeg shows up as
  an "unsupported" error. macOS and Windows need nothing extra.

Design notes: [docs/46_radio.md](../46_radio.md).

---

Back to the [guides](README.md) · [project README](../../README.md)

# 46 Radio

A reusable pool of favourite tracks in `<data-dir>/radio/`, playable while writing tales.

## Stage 1 — playback (done on `feature/radio`)

- **Library:** `radio.RadioLibrary` scans `radio/` recursively for `mp3`, `m4a`, `aac`, `wav`,
  `aif`, `aiff` (the formats JavaFX Media plays everywhere — **no FLAC/OGG**). Dot files such
  as macOS `._*` resource forks are skipped. `random(rnd, previous)` never repeats the current
  track unless it's the only one. No JavaFX imports (package boundary rule).
- **Git:** `radio/` is never versioned; the app writes `radio/.gitignore` (`*`, `!.gitignore`)
  at startup and adds it to the pending commit, like `attachments/.gitignore`.
- **Playback:** `ui.RadioPlayer` wraps `javafx.scene.media.MediaPlayer`. `MainController`
  creates it lazily on first use, so `javafx.media` and its natives aren't loaded at startup
  (todo 43). The Radio menu has Play / Pause, Next Random Track, Stop and Open Radio Folder. A 🎵
  toolbar button toggles playback; its tooltip shows the current track. At the end of a track
  the next random one starts.
- **Labels:** "Artist – Title" from the ID3 tags once the media is `READY`, else the file name.

## JavaFX Media pitfalls (why the code looks the way it does)

- The `MediaPlayer` is held in a field: a player nothing references is garbage-collected and
  stops mid-track.
- Errors don't throw, they arrive via `setOnError`. They are reported with `StatusSink.error`,
  and the player does **not** auto-skip to another track: if every file fails the same way
  (e.g. missing codecs), skipping would loop forever.
- File URIs come from `Path.toUri()`, which encodes spaces and umlauts correctly.
- Callbacks from a disposed player are ignored (`p != player` guards).
- **Linux:** JavaFX Media loads the system FFmpeg (`libavcodec`/`libavformat`) at runtime, and
  each JavaFX release only supports certain FFmpeg major versions. Expect "unsupported"
  errors on distros with a too-new or missing FFmpeg. macOS (AVFoundation) and Windows
  (bundled GStreamer-lite) are self-contained. The pure-Java fallback would be Java Sound plus
  an MP3 decoder SPI (e.g. JLayer/mp3spi, unmaintained).
- No gapless playback and no crossfade (that would take two players); seeking in VBR MP3s is
  approximate.
- Build: `javafx-media` is in `pom.xml`, in the Windows profile's copy list and `--add-modules`,
  and in `javafx-maven-plugin`'s `--enable-native-access`. It adds about 1.7 MB of natives to the mac jar.

## Later stages

- **Cloud sync:** own S3 prefix for `radio/`, generalized from `AttachmentSyncer` /
  `ObjectStore`. Possibly stream unsynced tracks from presigned HTTPS URLs (needs presigning
  in `SigV4`; the bucket must stay private).
- **Wikilinks:** an `.mp3` isn't a Tolaria note, so `[[Track]]` won't resolve in Tolaria.
  Idea: one stub note per track (`type: Track`, `artist`, `title`, `file: radio/x.mp3`),
  generated on import.
- **Trip soundtrack:** play all tracks linked from a trip's tales in tale order.
- **📌 Insert current track** at the tale cursor.
- **Export:** render track links as "Artist – Title" (`DiaryExporter`).
  Optionally a "🎵 Soundtrack of the day" line per day in the diary/HTML export, reusable for
  the WordPress export (todo 44).

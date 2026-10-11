# ADR-0004: Keep emoji as text in UI labels, and accept monochrome rendering on Windows

Status: Accepted

## Context

Most buttons, menu items and status labels lead with an emoji — `📷 Impressions`,
`🕸 Weblinks`, `🗺 Trip Map`, the per-verb icons in `domain.LinkKind`. They are written as
plain characters in `main.fxml` and in the Java label builders, so a label is just a string
and `LinkKind.emoji()` can be concatenated into one.

On macOS this looks exactly as intended. On Windows (JavaFX 23) the same labels render badly
in two distinct ways, and the two have different causes:

1. **Tofu boxes.** Several emoji were written with a trailing variation selector
   (U+FE0F): `🕸️`, `🗺️`, `🛏️`, `⚠️`. macOS swallows the selector; JavaFX on Windows
   treats it as its own codepoint, finds no glyph, and draws a `□` next to the emoji.
2. **No color.** Every emoji renders as a flat monochrome outline rather than the color
   glyph.

Cause of (2), which is the one that constrains the decision: JavaFX's color-emoji support
([JDK-8290866](https://bugs.openjdk.org/browse/JDK-8290866), landed in **jfx21**) probes the
font's **`sbix`** table — Apple's bitmap color format — and routes such glyphs to a
`drawColorGlyph` path that uploads a BGRA image as a texture. The review thread states the
scope outright: the change *"properly supports colour rendering of Emoji on macOS, while on
other platforms the Emoji will be rendered as ordinary greyscale glyphs — if there is font
support for the requested code point."*

Windows' Segoe UI Emoji is **COLR/CPAL** (layered vector glyphs plus a palette table), not
`sbix`. Nothing in the JavaFX pipeline reads COLR, so the font's plain outline glyph is used
instead. That is also why Windows degrades to something legible rather than to tofu: Windows
emoji fonts carry monochrome glyphs alongside the color layers.

Two non-options follow directly from that, and are worth recording so they aren't
re-investigated:

- **Upgrading JavaFX does not help.** No Windows COLR work in jfx25 or jfx26 as of 2026-10.
  The gap is a missing renderer, not a version lag.
- **Bundling a color font does not help.** A COLR font hits the same dead path, and an
  `sbix` font only works through the macOS CoreText code.

The only way to get color emoji on Windows is to stop using emoji *characters* and use
**images**: Twemoji/Noto PNGs in an `ImageView` set via `setGraphic(...)`. That is a real
chunk of work — an asset pipeline and sizing rules, plus a rework of every dynamic label
that currently concatenates an emoji into a string (`LinkKind` in the Weblinks label, the
Impressions counts) into a node graph.

## Decision

Keep emoji as plain characters in labels. Do not build an image-icon pipeline.

Strip U+FE0F from all UI sources and from `LinkKind`, fixing (1). The selector is redundant
on macOS — JavaFX picks a fallback font by codepoint coverage, and Apple Color Emoji is the
only font carrying e.g. U+1F578 / U+1F5FA, so those stay colored without it.

Accept (2): on Windows, emoji render monochrome. **macOS is the primary platform**; the
Windows look is legible, just plain, and that is not worth the cost of the image pipeline
right now.

When picking a *new* emoji for a label, prefer one whose monochrome Segoe UI Symbol glyph is
still recognizable, and never write it with a variation selector.

## Consequences

- Easier: labels stay plain strings, so `LinkKind.emoji()` concatenates into the Weblinks
  label and into exported Markdown with no node-graph detour. No assets, no licensing, no
  sizing rules, no theme-aware icon variants.
- Given up: color emoji on Windows, indefinitely. Some glyphs read poorly in monochrome —
  `🗂`, `🎒` and `👓` are the worst of the current set.
- `LinkKind` emoji also go into exported Markdown, which now carries no variation selector
  either. Markdown viewers resolve these by codepoint coverage too, so exports still show
  color.
- If this is revisited, the trigger is Windows becoming a primary platform, not a JavaFX
  upgrade — re-read the two non-options above first. Superseding ADR, not an edit here.

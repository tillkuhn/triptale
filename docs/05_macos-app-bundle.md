# macOS `.app` Bundle via jpackage

**Date:** 2026-08-08
**App:** TripTale (Spring Boot 4 fat jar + JavaFX 23)

Goal: make TripTale launchable from Spotlight/Launchpad with a real Dock/App
Switcher icon, instead of `java -jar target/triptale.jar` from a terminal.

## TL;DR

```bash
make app          # builds the jar, then target/dist/TripTale.app
open target/dist/TripTale.app
cp -R target/dist/TripTale.app /Applications/   # to install
```

Implementation lives in `packaging/macos/build-app.sh` (invoked by the `app`
Makefile target) and `packaging/macos/triptale.icns`.

## How it works

[`jpackage`](https://docs.oracle.com/en/java/javase/25/jpackage/) ships with
the JDK (25+ here) and wraps a jar + a bundled JRE into a native `.app`. No
extra Maven plugin needed — the script shells out to `jpackage` directly
after `mvnd -DskipTests package` has produced `target/triptale.jar`.

```bash
jpackage \
    --type app-image \
    --name TripTale \
    --input target/jpackage-input \
    --main-jar triptale.jar \
    --icon packaging/macos/triptale.icns \
    --dest target/dist \
    --app-version 1.0 \
    --vendor "TripTale" \
    --copyright "TripTale" \
    --mac-package-identifier net.timafe.triptale \
    --mac-app-category "healthcare-fitness" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --java-options "--sun-misc-unsafe-memory-access=allow"
```

## Gotchas (all baked into `build-app.sh` already)

1. **Stage the jar in an isolated directory before calling jpackage.**
   `--input` is copied *wholesale* into `Contents/app`. Pointing it straight
   at `target/` drags in `*.original` jars, `jacoco.exec`, surefire reports,
   etc. The script copies just `triptale.jar` into `target/jpackage-input/`
   first.

2. **`--main-jar` only, never `--main-class`.** The Spring Boot repackaged
   jar's manifest `Main-Class` is `org.springframework.boot.loader.launch.JarLauncher`
   (`Start-Class` is the real entry point, used internally by the loader).
   This is exactly how `make run-jar` already runs it (`java -jar
   target/triptale.jar`). Passing `--main-class` explicitly bypasses the
   Spring Boot loader and breaks the nested `BOOT-INF/lib/*.jar` classpath.

3. **`--app-version` must not start with `0`.** jpackage rejects
   `0.1.0-SNAPSHOT`-style versions (`"the first number ... cannot be zero"`).
   The script hardcodes a cosmetic `1.0` bundle version, independent of the
   real Maven project version (which still lives in the jar's
   `Implementation-Version` manifest entry via `build-info`).

4. **`--mac-app-category` value has no `public.app-category.` prefix.**
   jpackage adds that prefix itself; pass just the suffix (e.g.
   `healthcare-fitness`), or you get a doubled-up
   `public.app-category.public.app-category.foo` in `Info.plist`.

5. **JVM options need `--java-options`, not the values from `make
   run-jar`'s `$(JVMFLAGS)` directly** — same two flags
   (`--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow`)
   still apply, just passed one per `--java-options` flag to jpackage so
   they land in the generated `Contents/app/TripTale.cfg`.

## Icon

`packaging/macos/triptale.icns` — a backpack icon (matches the app's hiking/
cycling theme), generated once from an SVG and checked into the repo (small
binary, doesn't need regenerating unless the branding changes).

Regeneration recipe, if ever needed, using ImageMagick (has an SVG delegate
via `rsvg-convert`) + macOS's built-in `iconutil`:

```bash
SRC=path/to/backpack.svg
ICONSET=/tmp/triptale.iconset
mkdir -p "$ICONSET"

for size in 16 32 64 128 256 512 1024; do
  magick "$SRC" -background none -resize ${size}x${size} "/tmp/icon_${size}.png"
done

cp /tmp/icon_16.png   "$ICONSET/icon_16x16.png"
cp /tmp/icon_32.png   "$ICONSET/icon_16x16@2x.png"
cp /tmp/icon_32.png   "$ICONSET/icon_32x32.png"
cp /tmp/icon_64.png   "$ICONSET/icon_32x32@2x.png"
cp /tmp/icon_128.png  "$ICONSET/icon_128x128.png"
cp /tmp/icon_256.png  "$ICONSET/icon_128x128@2x.png"
cp /tmp/icon_256.png  "$ICONSET/icon_256x256.png"
cp /tmp/icon_512.png  "$ICONSET/icon_256x256@2x.png"
cp /tmp/icon_512.png  "$ICONSET/icon_512x512.png"
cp /tmp/icon_1024.png "$ICONSET/icon_512x512@2x.png"

iconutil -c icns "$ICONSET" -o packaging/macos/triptale.icns
```

(An emoji-based fallback also works if no SVG is at hand: render a colored
emoji glyph like `🎒` to PNGs via a tiny Swift/AppKit script using
`NSAttributedString` + `NSBitmapImageRep`, since ImageMagick/PIL don't
render color emoji reliably — CoreText/AppKit does.)

## Verified

- `make app` → `target/dist/TripTale.app` built successfully end to end.
- `open target/dist/TripTale.app` launches the full Spring Boot + JavaFX app.
- Shows up as `TripTale` (not `java`) in the menu bar, Cmd+Tab app switcher,
  and `System Events` process list — confirmed via
  `osascript -e 'tell application "System Events" to get name of every
  process whose background only is false'`.
- `Info.plist` correctly reports `CFBundleIdentifier: net.timafe.triptale`,
  `CFBundleName: TripTale`, custom icon file.

## Not done (out of scope for now)

- Code signing / notarization — the bundle is unsigned
  (`CFBundleSignature: ????`), fine for local/personal use but Gatekeeper
  will warn on other machines. Would need an Apple Developer ID cert.
- `.dmg` / `.pkg` installer (`--type dmg` / `--type pkg`) — `app-image` is
  enough for "drag into /Applications" personal use; revisit if
  distributing to others.
- CI integration — `release.yml` currently only builds a Linux jar; macOS
  app bundling would need a macOS runner (previously dropped from
  `release.yml` per its own comment about hanging runners).

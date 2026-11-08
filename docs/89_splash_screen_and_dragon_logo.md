# 89 Splash screen and dragon logo

# Splash screen and dragon logo

## Problem

The empty state ("Here be dragons") used the 🐉 emoji, which every platform renders
differently, and the app had no splash while Spring boots. A predictable logo was wanted.

## Decisions

- **Artwork:** generated with ChatGPT (prompts in `toolbox/prompts/splashscreen.md`): a
  serpentine dragon coiled into a "C", tail turning into a road, teal pearl. Two files:
  - `images/triptale-dragon.png`: dragon only, transparent, 512x512. Used for the empty state
    (160 px, loaded at 320 px for Retina) and the About dialog (110 px).
  - `images/triptale-splash.png`: dragon plus "TripTale" wordmark in a serif font, cropped, 640 px wide.
    Its background is slightly noisy (about `#192B2B`), not flat.
- **Splash = JavaFX `Preloader`:** `SplashPreloader` shows an undecorated stage with the image
  at 420 px; JavaFX hides it on `BEFORE_START`. `Launcher.main()` sets the `javafx.preloader`
  system property before `Application.launch`. This works with the plain `Launcher` main class
  (see boot sequence in AGENTS.md). No JVM-level native splash: static only, flaky on macOS.
- **Seamless edge:** `SplashPreloader.BACKGROUND` is the corner colour of the splash image, so
  window fill and image blend. Re-sample it (command in `splashscreen.md`) when the image changes.
- **Minimum display time:** Spring boots in under 1 s, so the splash would only flash.
  `TripTaleApplication.init()` sleeps up to a minimum (default 2000 ms) after Spring is up,
  on the launcher thread, never the FX thread. Override with env `TRIPTALE_SPLASH_MS` or
  system property `triptale.splash-ms`; the property wins. Negative = 0, invalid = default
  with a warning. Skipped when `triptale.aot-training` is set.
- **Dev vs release:** the Makefile sets `TRIPTALE_SPLASH_MS=0` for `run`, `run-fast` and `frun`
  (target-specific `export ... ?=`, override on the command line). `run-jar` and the
  released jar keep the 2 s. An env var, not `-D`, because `javafx:run` forks a JVM that does
  not reliably receive Maven `-D` properties.
- **About dialog:** `Dialogs.showInfo(title, header, content, graphic)` overload replaces the
  default (i) icon with the dragon; the three-argument version is unchanged.

## Options not taken

- Skipping the splash entirely in dev: would leave the splash code unexercised.
- A fade-out of the splash over the already visible main window (no hard wait): nicer, more
  code; revisit if the fixed 2 s annoys in normal use.

## Files

`SplashPreloader`, `Launcher`, `TripTaleApplication`, `ui/Dialogs`, `ui/dialog/AboutDialog`,
`fxml/main.fxml` (empty-state `ImageView`), `Makefile`, `resources/images/`.

## Not verified

Splash with `make run-fast` (AOT cache) and the fat jar was not tried.

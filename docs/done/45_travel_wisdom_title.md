# 45 Random travel wisdom and greeting in the window title bar

Make the app a bit more fun: the (mostly empty) window title shows a random greeting and a
random travel quote, e.g. `🐉 TripTale'e Hoş Geldiniz · To travel is to live. — Hans Christian Andersen`.

## Implemented

- **Stage 1:** `util/TravelWisdoms` reads the bundled `src/main/resources/wisdoms.txt`
  (one `quote — author` per line, `#` comments). Quotes stay ≤ 70 chars (enforced by
  `TravelWisdomsTest`).
- **Stage 2:** `MainController` owns the title (`windowTitleProperty()`, which
  `TripTaleApplication.start()` binds to `stage.titleProperty()`). `rollWindowTitle()` picks a
  new greeting and quote at launch and on every trip switch, including the one caused by a year
  switch. Day navigation leaves it unchanged. Never the same greeting or quote twice in a row
  (`util/RandomPick`).
- **Stage 3:**
  - 🐉 dragon is the new brand emoji. 🏔️🚴 are kept for activities in another todo.
  - `util/Greetings` reads `src/main/resources/greetings.txt`: `Language | template`, where
    `{app}` marks where the app name goes, so each language can place it naturally
    (Turkish `{app}'e Hoş Geldiniz`, Japanese `{app}へようこそ`). About 20 languages, English
    included. Non-Latin scripts yes, right-to-left (Arabic/Hebrew) no, because mixed with
    Latin text the native title reorders them oddly (bidi).
  - Title layout: `🐉 <greeting> · <quote>`. The greeting carries the app name, so it isn't
    repeated.
  - Language hint: the native macOS title bar has no tooltips, and the status line is kept
    for important messages. Instead the About dialog repeats the current greeting as its
    header with a line below: `Turkish for "Welcome to TripTale"` (no line for English).
  - The version moved out of the title into a dimmed label (`versionLabel`, CSS
    `.version-label`) right-aligned in the menu bar row, above the connectivity button
    (MenuBar wrapped in a `StackPane`).

## Decisions / ruled out

- Data-dir override list for quotes: ruled out for now.
- Title width: we let macOS truncate with "…". There is no width budgeting.
- About header: the language line can't be styled smaller or non-bold without replacing the
  dialog header node (and moving the "i" icon by hand). Left as plain two-line header text.

## Possible follow-up (not done)

- Pick a quote that fits the width: choose the greeting first, then pick only among quotes
  that fit the remaining space (~70 minus the greeting length), and show the greeting alone
  if none fit. Revisit if truncation turns out to be annoying in practice.

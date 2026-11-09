# 91 Derive name for weblinks

# 91 Derive name for weblinks

## Goal

Entering a URL in a weblink should not require typing the name by hand. A 🔍 button next to the URL field (like the Coordinator's 🔍, see `docs/51_reverse_geocoding.md`) checks that the URL is reachable and derives a name from the page.

## Decisions (grilled 2026-11-09)

- **Trigger:** button only; no automatic fetch on focus loss (no surprise network calls).
- **Name sources:** `og:title`, then `<title>`. `og:site_name` is not used. Cleanup is trim only: collapse whitespace, decode HTML entities, cap length (~60). No suffix stripping (`Hotel Foo | Booking.com` stays as is).
- **Name empty + success:** fill it in directly.
- **Name already set and different:** small popup "Replace 'X' with 'Y'?" with Replace / Keep. Same name: nothing to ask.
- **Problems** (unreachable, HTTP error, timeout, no title): shown on the button like the Coordinator 🔍 — ⏳ while running, ⚠️ with the error as tooltip, back to 🔍 on success. No popup.
- **Network:** `java.net.http.HttpClient` like `map.PlaceLookup` / `weather.OpenMeteoClient`, no new dependency. Follow redirects, 5–10 s timeout, browser-like User-Agent, regex over the first ~64 KB of the HTML. Runs off the FX thread.
- Only http(s) URLs (`UiText.isValidHttpUrl`); the button is disabled otherwise.

## Scope / open points

- Dialogs: `ui.dialog.LinksDialog` (new row and existing rows) and `ui.dialog.QuickWeblinkDialog` (follow-up to an Octobot inbox Add with "Create Weblink" ticked, todo 77). Share one helper rather than duplicating the logic.
- New class (e.g. `weblink.PageTitleLookup` or in `domain`/`map`-like package without JavaFX) returning a result type (title or error); unit-test the HTML parsing with `@TempDir`-free pure tests. The JavaFX part stays in `ui`.
- Update `docs/09_entry_links.md` / AGENTS.md package boundary list if a new package is added.

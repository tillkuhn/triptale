# Todo 13 — Settings Handling

## Problem

`prefs.yml` currently lives inside the git data dir itself and mixes two unrelated concerns:
machine-local app configuration (impressions patterns, grid columns) and repo-local dynamic
state (`lastTripSlug`). Meanwhile the actual git repo location (`dataDir`) and git author
identity are Spring `@ConfigurationProperties`, only settable at build/launch time via
`application.yml`/env vars/`-D` flags — not editable at runtime, and defaulting silently to
`~/Pictures/triptale-data` with no user awareness or consent.

## Key decisions (from grill-me session)

1. **Two-file split**, by lifecycle/scope, not by current file name:
   - **`settings.yml`** — app-wide, lives in an OS-standard config directory, versionable
     across machines only by the user manually copying it (not part of any git data repo).
     Fields: `dataDir`, `git.authorName`, `git.authorEmail`, `impressionsFilePattern`,
     `impressionsGridColumns`, `impressionsFaveFilePattern`.
   - **`.state.yml`** — repo-local, unversioned (gitignored, same as `prefs.yml` today),
     internal-only bookkeeping. Holds `lastTripSlug` today. Designed as an extensible
     map/POJO since more repo-local fields (e.g. last-opened-entry) are expected later, but
     **no UI ever edits this file** — it's written programmatically only.
2. **Settings directory location:** `%APPDATA%/triptale` on Windows, `$HOME/.config/triptale`
   on macOS *and* Linux (two-way branch, not three — `.config` on macOS is an accepted
   dev-tool convention here, no need for `~/Library/Application Support`).
3. **The one remaining Spring property:** `triptale.settings-dir` stays a genuine
   `@ConfigurationProperties` field (bootstrapping concern — the app needs to know where to
   look for `settings.yml` before it can load anything else). Overridable via Spring's
   standard relaxed binding: `TRIPTALE_SETTINGS_DIR` env var or `-Dtriptale.settings-dir=...`.
   Every other property (`dataDir`, git author, impressions fields) is removed from
   `application.yml`/`TripTaleProperties` entirely and lives only in `settings.yml`, editable
   at runtime.
4. **`settings.yml` is a typed POJO**, not the loose `Map<String,Object>` pattern `prefs.yml`
   uses today — new `AppSettings` class (Jackson YAML, mirrors `TripTaleProperties`' shape),
   loaded/saved by a new `SettingsService`/`SettingsStore` (in `config` or `storage`, no
   JavaFX imports — package boundary rule applies). `.state.yml` can keep the existing
   loose-map read-modify-write style since it's internal/small.
5. **No default `dataDir`.** Placeholder text in the settings dialog field:
   `e.g. ${HOME}/git/triptale-data` — using the `${HOME}` curly-brace syntax already
   established by `ImpressionsResolver` (see todo 10), not a bare `$HOME` or a resolved
   literal path, for consistency. Expansion of `${HOME}` specifically (not general
   templating) is in scope for todo 13; see todo 14 for the rest.
6. **Missing/unset `dataDir` behavior:**
   - Non-blocking warning (Alert or banner) shown on **every** launch until configured —
     not one-time, not a blocking modal. App remains otherwise usable.
   - Storage/git layer stays **fully inert** while unconfigured: no directory auto-creation,
     no `git init`, no type-definition file creation. `MarkdownStore.dataDir()` keeps its
     existing always-resolves contract untouched (no `Optional`/exception changes, to avoid
     rippling through all existing call sites); instead a new guard method (e.g.
     `GitService.isConfigured()`) is checked by `MainController` before startup init and
     before any trip/entry action, gating those actions and surfacing the warning.
7. **`git init` prompt fully replaces today's silent auto-init.** Currently
   `GitService.initOnStartup()` (a `@PostConstruct`) unconditionally runs `git init` if
   `.git` is missing. New behavior: if the configured `dataDir` doesn't exist or is an empty
   directory, prompt the user yes/no before initializing — for every case, not just
   first-time setup. Answering "no" leaves the setting as-is (does not clear it back to
   unset); the app re-prompts on the next launch since the directory is still empty.
8. **Timing/architecture fix required:** `@PostConstruct` runs during Spring context boot,
   *before* the JavaFX `Stage`/`Scene` exists (per the app's boot sequence — `init()` boots
   Spring, `start()` loads FXML after). A yes/no `Alert` cannot be shown at that point. The
   git-init check/prompt and the missing-`dataDir` warning move out of
   `GitService.initOnStartup()` and into `MainController`, triggered once the primary stage
   is visible. `GitService` exposes plain check/action methods (e.g. `isConfigured()`,
   `needsInit()`, `initRepo()`) for the controller to call and drive dialogs around, rather
   than doing unprompted work at construction time.
9. **Settings dialog:** the existing `"⚙ Edit Preferences…"` dialog/menu item is
   **repurposed** (not duplicated) into `"⚙ Edit Settings…"`, scoped entirely to
   `settings.yml` fields — `dataDir`, git author name/email, and the three impressions/faves
   fields together in one dialog. `.state.yml` is never exposed in any dialog.
10. **Changing `dataDir` requires an app restart** to take effect — no in-session hot-reload
    of `GitService`/`MarkdownStore` against a new path. Save the new value, inform the user a
    restart is needed. Keeps scope contained; live repo-switching (open trip, pending saves,
    connectivity state) is a separate concern.
11. **No migration of existing `prefs.yml`.** Existing single-user installs reconfigure
    manually after upgrading (re-enter impressions fields once; set `dataDir` explicitly,
    since it wasn't user-editable before this change anyway). No migration code.
12. **No other `application.yml` properties get exposed as settings.** Audited
    `logging.level.*` — left as a dev-only Spring property, not worth a UI toggle.
    `spring.main.*` is boot mechanics, not user-facing. Nothing else remains as a candidate.

## Non-goals / explicitly out of scope (→ todo 14)

- General template-variable expansion (`${TRIP_SLUG}`, `${TALE_DATE}`, etc.), resolved at
  runtime (entry write time / image lookup time), generalized from the existing
  `ImpressionsResolver` (`${HOME}`, `${DATE}` today) and applied to `dataDir` and beyond.
  Todo 13 only needs literal `${HOME}` expansion for `dataDir`, reusing the existing
  resolver's placeholder convention — not a new engine.
- macOS-native `~/Library/Application Support` path — intentionally using `.config` on both
  macOS and Linux.
- Logging-level / debug-mode toggle as a user setting.

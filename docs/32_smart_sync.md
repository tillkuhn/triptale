# Todo 32 — Smart Sync

Status: **agreed 2026-10-04 (grill-me session); implemented on `feature/32-smart-sync` and checked in the UI (git rows, attachments push). Attachment pull (33b) added the same day, not yet tested against the real bucket.**

Today there are three ways to persist data: Save (to disk), Commit (git commit of saved
changes) and Sync (commit + fetch/rebase + push, in one go), plus Push Attachments (todo 33a).
Too many buttons for "is my data safe?", and the single status line can't show partial
results (e.g. commit OK, rebase conflicted).

Goal: Sync becomes **Smart Sync**, an interactive dialog that shows what will happen, lets the
user opt out of steps, and shows the result of each step. Low-level operations stay in the
menu for power users.

## Decisions

**D1 — One Git Remote row, ahead/behind instead of a pull timer.** Pull and push are not
independent: pushing without pulling is rejected when the remote moved. So the dialog runs
`git fetch` in the background when it opens (if online) and shows real counts:
"↓ 3 incoming · ↑ 2 outgoing" or "Up to date". One **Git Remote** row runs fetch + rebase +
push; checked by default when online and ahead/behind ≠ 0/0. *Dropped from the draft:*
`lastRemoteGitPull` / `lastRemoteAttachmentsPull` in `.state.yml` and the
`suggestedGitPullIntervalMins` setting. Standalone Pull Git / Push Git stay in the menu.

**D2 — `git status` decides whether to commit.** Not the in-memory `pending` map. JGit status
is milliseconds on a Markdown repo and also catches external edits (Tolaria, editor, manual
checkout). Needs something like `GitService.dirtyFiles()`.
- Commit message: built from `pending` labels when present (nicer names), plus
  "+ N external changes" for dirty files not in `pending`; generated from the file list when
  `pending` is empty. Editable, 2-line text area (expandable).
- A collapsible "N files" list under the message (collapsed by default).
- `pending` non-empty but git clean (saved, then reverted by hand): row shows
  "Nothing to commit", `pending` is cleared silently.

**D3 — Implicit save happens before the dialog opens.** If the form is dirty, Smart Sync runs
the normal save path first (same validation and alerts). If the save fails, the dialog doesn't
open and the user stays on the bad field. The dialog only ever sees disk state — no "Save"
row. The menu-only Commit follows the same rule (save first, then commit) instead of today's
"disabled while form is dirty" (`canCommit()`).

**D4 — Rebase conflicts.** `fetchAndRebase()` already aborts a failed rebase, so the local
commit is safe and the tree is clean.
- Git Remote row turns red: "Conflict: remote changed the same files"; push is **skipped**.
- A **Details** toggle shows the git output and conflicting paths (parsed from
  `CONFLICT (content): Merge conflict in …`).
- Attachment rows still run — independent of git.
- No merge UI. Resolve manually (terminal / Tolaria), then sync again. A **Reveal in Finder**
  button for the data dir is in scope.
- Never auto-merge, never `-X ours` / `-X theirs` (both silently lose diary text).
- After closing, the status line keeps a sticky "⚠ Sync conflict — unresolved" until the
  next successful sync.

**D5 — Attachments: push only in #32.** `ObjectStore` has no `get`, and todo 33's delete /
conflict questions are still open, so attachment pull is split out as **todo 33b**.
- One **Attachments** row, visible only when `attachments.sync` is `cloud` (`git` mode is
  covered by the git row, `off` has nothing to sync).
- On open (online): one `list(prefix)`, compared like `AttachmentPusher` does →
  "↑ 4 to upload (12 MB) · 7 only in cloud" or "Up to date". The "only in cloud" count is free
  from the same LIST and prepares 33b.
- Checked by default when there's something to upload; untick it on a slow connection.
- Runs `AttachmentPusher.push(...)`, progress callback feeds the row
  ("Uploading 3/4: IMG_1234.jpg").
- Scope: the whole `attachments/` tree (one LIST). Revisit at thousands of objects.
- Incomplete S3 config (`validate()` error): row shown **disabled** with the message, not
  hidden.
- When 33b lands, the same row does both directions; layout unchanged.
- **Update (33b, 2026-10-04):** the row now pulls, then pushes. The plan shows
  "↓ 6 to download (4.2 MB) · ↑ 2 to upload (1.1 MB)"; checked by default when either is > 0.
  `AttachmentPusher` became `AttachmentSyncer`.

**D6 — Exit stays cheap.** No network on exit. "Commit & Exit" stays as is (local commit). The
only change: the warning also mentions committed-but-unpushed work, from a local ahead count
(no fetch): "…and 2 commits not yet pushed". Information only, no extra button.

**D7 — Shortcuts.** `Cmd+K` → Smart Sync (keeps the "make it safe" muscle memory); **Sync** is
the default button, so `Cmd+K`, `Enter` syncs. After the sync finishes, the default button
switches to Close (`Enter` again closes). Commit becomes menu-only with no accelerator
(offline Smart Sync *is* a commit; online, untick Git Remote). `Cmd+Alt+K` is free if a commit
shortcut is missed later. No "don't ask again" / auto-start for now; revisit after real use.

**D8 — Connectivity.** The toolbar button stays as a passive indicator (click re-checks), but
no longer gates the Sync button — Smart Sync is always enabled, since it commits offline. The
dialog's Connectivity row runs its own fresh check on open (and triggers the D1 fetch). Menu
items Pull Git / Push Git / Push Attachments stay gated by connectivity.

## Main window changes

- Sync button → **Smart Sync** (always enabled); Sync menu item likewise.
- Save button stays (cheap, local; still mandatory when leaving a day entry).
- Commit button removed; Commit stays in the Repository menu.

## Dialog layout

Popup with a grid of rows. Order follows execution; git rows above attachment rows. The dialog
does **not** start on open — it waits for the user.

| Row | Visible | Default | Disabled when |
|---|---|---|---|
| Connectivity | always | — (info only) | — |
| Commit (message + file list) | always | checked if `git status` dirty | repo clean |
| Git Remote (fetch + rebase + push) | always | checked if online and ahead/behind ≠ 0/0 | offline ("Offline") |
| Attachments (push) | sync mode `cloud` | checked if something to upload | offline or S3 config invalid |

Each row, left to right:
1. Checkbox — include the step; default and enabled state from context.
2. Operation name ("Commit", "Git Remote", …).
3. Status text — before sync: state or reason it's disabled ("↓ 3 · ↑ 2", "Offline"); during:
   current action ("Rebasing onto origin/main"); after: result ("Committed a1b2c3d",
   "Conflict — see details").
4. Status icon — disabled / spinner / success / error.

Buttons: **Close** (no action) and **Sync** (default). Sync runs the checked rows in order,
updating each row in place; the dialog stays open afterwards so results can be read.

Replaces `SyncProgressDialog`'s auto-start behavior; the worker-thread + `onSuccess` callback
pattern stays (see AGENTS.md, UI structure).

## Out of scope

- ~~Attachment pull → todo 33b.~~ Done, see D5 update.
- Merge / conflict resolution UI.
- Auto-sync / "don't ask again".

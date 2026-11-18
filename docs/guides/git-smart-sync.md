# Git & Smart Sync 🔄

Saving a tale (`⌘S`) writes the file to disk immediately but does **not** commit. Commits happen when you ask for them, so a day of small edits becomes one commit instead of twenty.

**🔄 Smart Sync** (`⌘K`, then `Enter`) is the "make my data safe" button. It saves the open tale first, then shows a dialog with one row per step, each of which you can untick:

- **Commit** — everything git sees as changed, with a generated (editable) message.
- **Git Remote** — fetch, rebase onto the remote, push. The row shows "↓ 3 incoming · ↑ 2 outgoing" before you start. On a rebase conflict nothing is lost: the rebase is aborted, push is skipped, and you resolve it by hand (terminal, Tolaria, …) and sync again.
- **Backpack** — only in `cloud` mode, see below.

You can keep writing offline for weeks and sync the whole batch the moment you find Wi-Fi. The toolbar shows whether the git host is reachable; online-only actions are disabled while it isn't. The low-level steps (Commit, Pull Git, Push Git, Repo Advisor) are still in the **Repository** menu.

To enable push/pull, add a remote to the data dir: `git -C <data-dir> remote add origin <url>`. Push and pull use your OS `git` binary, so your usual SSH keys or credential helper apply.

---

Back to the [guides](README.md) · [project README](../../README.md)

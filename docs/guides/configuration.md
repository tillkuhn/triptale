# Configuration ⚙️

Almost everything is set at runtime in **⚙ Edit Settings…** and stored in `settings.yml`:

| Setting | Notes |
|---|---|
| `dataDir` | The data directory / git repo, e.g. `${HOME}/git/triptale-data` (`${HOME}` and `${USER}` work). Changing it needs a restart. |
| `git.authorName`, `git.authorEmail` | Commit author; blank falls back to your git config. |
| `impressionsFilePattern`, `impressionsBaseFilter`, `impressionsFaveFilter`, `impressionsGridColumns` | See [Impressions & Faves](impressions-and-faves.md). |
| `gpxFilePattern` | Where recorded tours are backed up, same placeholders as the photo pattern, e.g. `${HOME}/tours/${ENTRY_YEAR}-${ENTRY_MONTH}-${ENTRY_DAY}_*.gpx`. Blank = no suggestions. |
| `attachments.*` | Backpack sync mode and S3 settings, see [Backpack](backpack.md). `publicBaseUrl` (optional) is the public HTTPS address of `attachments/`, for WordPress export images. The same bucket also syncs Tailwind FM's tracks. |
| `defaultTripLanguage` | `en` or `de`, the language of the words exports add. |
| `wordPress.*` | WordPress.com site and OAuth credentials for Post Office, see [Export](export-and-publish.md). |
| `telegramBotToken`, `telegramChatId` | Octobot's Inbox, see [Octobot's Inbox](octobots-inbox.md). |
| `thunderforestApiKey` | Optional free key for the Outdoors / OpenCycleMap map styles in Sgt. Mapper. |
| `radioEnabled` | Shows the Tailwind FM menu and 🔊 button, see [Tailwind FM](tailwind-fm.md). Needs a restart. |

`settings.yml` lives in `$HOME/.config/triptale/` on macOS and Linux, and in `%APPDATA%\triptale\` on Windows. It belongs to the machine, not to the data repo. To use a different settings directory (e.g. a second profile, or one on a USB stick for travel), set `TRIPTALE_SETTINGS_DIR` or pass `-Dtriptale.settings-dir=/path`.

---

Back to the [guides](README.md) · [project README](../../README.md)

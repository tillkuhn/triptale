# Export & publish 📤

**⇧ Export Diary…** renders the trip as a single Markdown document — headings per day, cumulative distance and altitude totals — which you can copy to the clipboard, or open as an HTML page in your browser, optionally with each day's impressions (from the photo library or the backpack, all or faves only) as a photo gallery. Switch **Format** to **WordPress** to get block markup for a blog post instead: day headings, distance and climb, the tales as editable blocks and a gallery per day. Paste it into the WordPress block editor's Code editor (Cmd + Opt + Shift + M). Gallery images are hotlinked from the backpack, so they need a **Backpack public URL** in Settings (e.g. a CloudFront distribution, see [`terraform/README.md`](../../terraform/README.md)) and a Smart Sync before you publish. A **From / To** range narrows it to some days, with totals for just those; **⇧ Export Tale** next to Save exports only the current day. Words the export adds itself (Day, Distance, link labels, weekdays, number formats) follow **Default trip language** (`defaultTripLanguage`: `en` or `de`); the app itself stays English. Each day gets a small fact line (date, activity, distance/climb, places), and exports containing places end with an OpenStreetMap credit.

**📮 Post Office…** (My Trips menu) turns a range of days into a *Post* with its own intro, and **Send to WordPress** publishes or updates it as a draft on WordPress.com via the API. It needs a WordPress.com OAuth app (`wordPress.site`, `clientId`, `clientSecret` in `settings.yml`); see [docs/60_wordpress_api_draft.md](../60_wordpress_api_draft.md).

Export uses a handful of small Mustache-style templates in `src/main/resources/export/` if you want to tweak the output.

---

Back to the [guides](README.md) · [project README](../../README.md)

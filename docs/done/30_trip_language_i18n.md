# 30 Localization and i18n for Trip Language

The single language for the application (e.g. menu entries, labels etc.) will remain english.
But since the contributed trip tales are potentially in a different language (currently mostly german, and the exporter adds some words such as "Distance, Day , Link Verbs etc." to the generated content, we should add support for i18n as far as derived trip entry content is affected. 
Add a new application setting defaultTripLanguage, possible values for locales are right now  German (de) and English (en), default is English. Trip Details may be able to overwrite it but not needed for for first iteration. no trip language set always means "fall back to app default"
Add support for properties to translate certain values, but should always default to english if not special language is available. should be easy to edit (e.g. property files, not embedded in java classes)

Current content for german translation:

Day = Tag
Distance = Distanz
Altitude = Höhenmeter
and the translations for URL Links like Sleep (Übernachten), Eat etc.

in future there may be more, e.g. enum for activities like "Bikepacking"

In Export Diary, use these values instead of the current hardcoded english versions when template content is written.
Also, if exporter transform dates like "Saturday, 2nd of November" there should be support to localize it according to trip language

## Decisions (grilled 2026-10-11)

- **Boundary:** whatever lands in the exported file follows the trip language (Markdown,
  HTML, WordPress, plus `<html lang="…">` in `html-shell.html` / WordPress preview).
  Everything shown only inside the app stays English: the "Day 3 of 12" status, the
  export dialog's entry picker. Entry filenames (`2026-06-04-Thursday.md`) stay English forever.
- **Package:** new `net.timafe.triptale.i18n`, JavaFX-free (add it to the package rule in
  AGENTS.md). Activity enums later and todo 53 will use it too.
- **Translations:** bundled only, `src/main/resources/i18n/export.properties` (English base)
  plus `export_de.properties`. No user/vault overrides. Namespaced keys: `stats.*`, `date.*`,
  `link.*`, later `activity.*`.
- **Fallback trap:** `ResourceBundle` falls back to the JVM default locale before the base
  bundle, so on a German Mac an "en" trip would get German. Use
  `Control.getNoFallbackControl(FORMAT_PROPERTIES)` and test with `Locale.setDefault(GERMAN)`.
  Read the properties as UTF-8 (`ResourceBundle` does since Java 9; `Properties.load(InputStream)` doesn't).
- **Granularity:** single-word keys (`{{t.distance}}: {{distance}} km`), not phrases. The
  English output changes too ("Distance covered:" → "Distance:").
- **Plurals:** `one`/`other` key pairs (`days.one=Tag`, `days.other=Tage`), picked by count.
  This also fixes the English "1 days".
- **Numbers:** frontmatter keeps `Locale.ROOT`, exported text uses the trip locale
  (`12,5 km` in de). Write this exception into the `Locale.ROOT` convention in AGENTS.md.
- **Dates:** the pattern is a properties key per language (`date.long=d. MMMM yyyy` for de).
  English gets an `{ordinal}` token for st/nd/rd/th (`July 23rd 2026`), because
  `DateTimeFormatter` can't produce ordinals.
- **Link labels:** look up `link.<verb>` for every verb, including custom ones like
  `url_hike`. Fallback: the `LinkKind` label, then the capitalized key. English labels stay in
  `LinkKind` only (no `link.*` in the base bundle). Emoji stay per verb.
- **Setting:** `defaultTripLanguage: de|en` in `settings.yml`, default `en`. Edit Settings
  shows a combo (English / Deutsch). A hand-edited unknown value → English plus a log warning.
  It's read at export time, so it takes effect without a restart.
- **Future trip override:** frontmatter key `lang` in the trip `README.md` (sorts between
  `end_date` and `start_date`). Not built in this iteration; unset = app default.
- **Refactor for todo 53:** the exporter first turns each entry into already-localized values
  (weekday, long date, day label, distance, altitude, coordinates) that the templates only
  place. For now they're rendered as simple text; todo 53 changes the layout.

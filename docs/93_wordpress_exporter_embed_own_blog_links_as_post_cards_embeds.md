# 93 WordPress Exporter: Embed own-blog links as post cards, embeds after gallery, add checkout verb (S)

# WordPress Exporter: own-blog post cards, embeds after the gallery, `checkout` verb

## Context

Links to other posts of the own blog were plain list items in the WordPress export. In the
block editor, pasting such a permalink produces a `wp:embed` "post card" (title, excerpt,
image, comment count). The editor's markup carries a `#?secret=...` suffix.

## Decisions

- **Own-blog detection:** host of a link equals the host of `wordPress.site` in settings
  (bare host or full URL accepted), and the path is more than `/` (a front-page link stays a
  list item). Any verb qualifies.
- **No secret, no `providerNameSlug`:** the secret is a random token WordPress adds itself
  when rendering the embed; the plain permalink with `"type":"wp-embed"` renders the card
  (verified on the live blog). The slug (site title) would need another setting and has no
  visible effect.
- **Context for the card:** a card on its own looks "thrown in". Only when the link has a
  name (`[Name](url)` / Name field in the Weblinks dialog) it is emitted as a normal
  paragraph above the card. Without a name there is no intro. The verb label is deliberately
  not used. Rejected: always printing a verb-label lead-in, wrapping the card in a
  collapsed `wp:details`.
- **Placement:** all WordPress-only embeds (own-blog cards and the existing YouTube `watch`
  embed) are rendered *after* the day's gallery, since they are large and distract less
  there. The remaining links stay in the list before the gallery. Other exports are
  untouched: they render these links as ordinary list items.
- **`checkout` verb:** new multi-purpose `LinkKind.CHECKOUT` (`url_checkout`, 👀, "Check out"),
  first in alphabetical order. German uses the same wording, so no `link.checkout` entry in
  `export_de.properties` (falls back to the `LinkKind` label).

## Files

- `export/WordPressExporter` (`linkBlocks`, `embedBlocks`, `siteHost`, `isOwnPostUrl`, `postEmbed`)
- `domain/LinkKind`
- Tests: `WordPressExporterTest`, `LinkTest`

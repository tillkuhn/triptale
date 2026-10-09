# ADR-0003: Reverse geocoding via the public Nominatim API

Status: Accepted. Not implemented yet, see todo 51.

## Context

Entries can record start and end coordinates (`start_lat`/`start_lon`, `stop_lat`/
`stop_lon`), but a place name and a country are what you actually want to read in a diary,
e.g. to suggest a title like "Vechta → Bremen". That needs reverse geocoding: coordinates in,
place name and country out, from an external service.

Constraints:

- **Free**, and no account or billing setup just to get a town name.
- **Results get stored** in the entry (title or frontmatter), so the terms must allow
  storing results permanently.
- **Low volume.** Lookups are interactive: one or two per entry, when the user sets
  coordinates. TripTale is a single-user desktop app, so there is no server-side batch load.
- **Worldwide coverage**, including small villages and remote areas.

Candidates considered (as of 2026-10):

| Service | Key | Free limit | Why not (or why) |
|---|---|---|---|
| **Nominatim** (public OSM instance) | no | 1 req/s, no bulk use | Chosen, see below. |
| Photon (komoot) | no | fair use, no published limit | Same OSM data, cleaner GeoJSON, but only `en`/`de`/`fr` and no formal usage policy. Kept as the fallback. |
| GeoNames `findNearbyPlaceNameJSON` | free username | 10k/day, 1k/h | Gives the nearest populated place, which suits a diary well, but needs a registered username in settings. |
| Mapbox Geocoding | token (already in settings for static maps) | 100k/month *temporary* | The free tier is temporary geocoding only: its results may not be stored. Storing them needs *permanent* geocoding, which is paid. |
| Geoapify / LocationIQ / OpenCage | yes | 2.5k–5k/day | Fine, but each needs another API key in settings for nothing Nominatim doesn't already give us. |
| BigDataCloud client API | no | unlimited | Its terms only allow looking up the device's current, real-time location. Looking up stored coordinates breaks them and can get the IP banned. |
| Offline GeoNames dump (`cities15000`/`cities1000`) + nearest neighbour | none | none | Works offline with no limits, but ships 1–10 MB of data that has to be refreshed now and then. Only finds the nearest city, not the actual place. Deferred, not rejected. |

The previous project (angkor, `GeoService.kt`) already used Nominatim behind a Bucket4j
bucket of 1 req/s without problems.

## Decision

Use the **public Nominatim reverse endpoint** at city level:

```
GET https://nominatim.openstreetmap.org/reverse?lat=..&lon=..&format=jsonv2&zoom=10&accept-language=en
```

Take the place name from `address.city`, then `address.town`, `address.village`, then
`name`, and the country from `address.country` / `address.country_code`. Call it with
`java.net.http` (as `S3Client` does), no new dependency.

Follow the [usage policy](https://operations.osmfoundation.org/policies/nominatim/):

- A User-Agent that identifies the app, e.g. `TripTale/<version> (+https://github.com/tillkuhn/triptale)`.
  Generic or library-default agents get blocked.
- At most 1 request per second. A "last request time, wait until 1 s has passed" check is
  enough at this volume; there's no need for Bucket4j.
- Never look up while the user is typing (no autocomplete-style use), and don't repeat a
  lookup for coordinates whose result is already stored.
- Credit "© OpenStreetMap contributors" (ODbL) wherever looked-up names are shown, e.g. in
  the About dialog.

The base URL is configurable, so switching to Photon or a self-hosted Nominatim doesn't need
a code change beyond the response mapping.

## Consequences

- Easier: no API key, no account, no new dependency. Results may be stored under ODbL.
- Harder: depends on a free community service with no availability promise. A lookup can
  fail or be slow, so it must run off the FX thread, time out quickly, and fail quietly (no
  name suggested, never a blocking error).
- Offline there's no lookup at all. If that turns out to matter on the road, the offline
  GeoNames dump is the planned addition, not a replacement for Nominatim.
- `accept-language=en` gives English names ("Munich", not "München"). Native names would
  need another setting; not done for now.

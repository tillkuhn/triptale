# 97 GPX map points

Goal: derive a map for a tour from the GPX with a reasonable number of points, so the line is not a straight start-to-end.

## Facts (sample: Butterfly Valley, Rhodos)

- GPX: one `trk`/`trkseg`, 314 `trkpt` (lat, lon, ele, time) roughly 10 s apart. No `wpt`, `rte` or `extensions`.
- Sidecar JSON `cover_images[]`: 9 entries for that tour (one duplicated), each with `location.lat`, `location.lng`, `alt` (always 0), and `index` (position along the route). `created_at` is upload time, not usable for ordering.
- Across all exported sidecars every photo has a location (7840 of 7840).
- `timeline` was empty in the sample.

## Photo count across all exports (588 sidecars)

- 76 tours with no photos, median 12, 139 tours with more than 20, max 50. The "not more than 20" assumption does not hold.
- Older tours (2020) have GPS tracks too (zorro-etappe-2: 6781 trackpoints, 39 photos, all with location). `cover_images[].index` is the trackpoint position (max 5655).
- That tour has two batches: photos with real `alt`/original `created_at`, and photos with `alt` 0 and a shared later upload timestamp. Some of the latter sit right next to an original photo (indexes 2359/2360, 3653/3656), probably near-duplicates.

## Approach

1. Photo points: sorted by `index`, deduped, bracketed by first and last GPX point. If more than ~20, thin to ~20 (even spread along `index` and/or drop points very close to a neighbour).
2. Fallback: every Nth GPX point (~20 total), always keeping first and last.

## Open

- Markers on the full track vs. simplified line through the points.
- Verify photo coordinates lie on/near the track.

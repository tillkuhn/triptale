package net.timafe.triptale.map;

import java.util.Locale;

/**
 * A raster tile server for {@link OsmMapRenderer} (todo 61): URL template, attribution and the
 * zoom it tops out at. v1 ships {@link #OPEN_TOPO_MAP} only — the doc's preferred nature look
 * (contours, hillshading, forests, paths) — CyclOSM/OSM standard/others are separate future
 * todos, each needing its own usage-policy check before being added here.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public enum TileSource {

    /** © OpenStreetMap contributors, SRTM · style © OpenTopoMap (CC-BY-SA). Max zoom 17. */
    OPEN_TOPO_MAP("OpenTopoMap", "tile.opentopomap.org", new String[] {"a", "b", "c"}, "/%d/%d/%d.png", 17,
            "\u00a9 OpenStreetMap contributors, SRTM \u00b7 style \u00a9 OpenTopoMap (CC-BY-SA)");

    private final String label;
    private final String host;
    private final String[] subdomains;
    private final String pathTemplate;
    private final int maxZoom;
    private final String attribution;

    TileSource(String label, String host, String[] subdomains, String pathTemplate, int maxZoom, String attribution) {
        this.label = label;
        this.host = host;
        this.subdomains = subdomains;
        this.pathTemplate = pathTemplate;
        this.maxZoom = maxZoom;
        this.attribution = attribution;
    }

    /** Short lowercase id used as the tile disk cache's subfolder name. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int maxZoom() {
        return maxZoom;
    }

    public String attribution() {
        return attribution;
    }

    /** A host of this source, for the pre-flight {@code ConnectivityService} check. */
    public String connectivityUrl() {
        return "https://" + subdomains[0] + "." + host;
    }

    /** The tile URL for {@code z}/{@code x}/{@code y}, round-robining over the source's subdomains. */
    public String tileUrl(int z, int x, int y, int requestIndex) {
        String subdomain = subdomains[Math.floorMod(requestIndex, subdomains.length)];
        return "https://" + subdomain + "." + host + pathTemplate.formatted(z, x, y);
    }

    @Override
    public String toString() {
        return label;
    }
}

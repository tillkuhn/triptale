package net.timafe.triptale.map;

import java.util.Locale;

/**
 * A raster tile server for {@link OsmMapRenderer} (todo 61): URL template, attribution and the
 * zoom it tops out at. Each source needs its own usage-policy check before being added here;
 * key-requiring sources (Thunderforest, OpenCycleMap) and overlay-only sources (Waymarked
 * Trails) are out of scope for this simple enum and are separate future todos.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public enum TileSource {

    /** © OpenStreetMap contributors, SRTM · style © OpenTopoMap (CC-BY-SA). Max zoom 17. */
    OPEN_TOPO_MAP("OpenTopoMap", "tile.opentopomap.org", new String[] {"a", "b", "c"}, "/%d/%d/%d.png", 17,
            "\u00a9 OpenStreetMap contributors, SRTM \u00b7 style \u00a9 OpenTopoMap (CC-BY-SA)"),

    /** © OpenStreetMap contributors. Max zoom 19. Single host, no subdomains (2024 tile usage policy). */
    OSM_STANDARD("OSM Standard", "tile.openstreetmap.org", new String[] {""}, "/%d/%d/%d.png", 19,
            "\u00a9 OpenStreetMap contributors"),

    /** © OpenStreetMap contributors \u00b7 CyclOSM. Max zoom 20. */
    CYCLOSM("CyclOSM", "tile-cyclosm.openstreetmap.fr", new String[] {"a", "b", "c"}, "/cyclosm/%d/%d/%d.png", 20,
            "\u00a9 OpenStreetMap contributors \u00b7 CyclOSM");

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
        return "https://" + hostWithSubdomain(subdomains[0]);
    }

    /** The tile URL for {@code z}/{@code x}/{@code y}, round-robining over the source's subdomains. */
    public String tileUrl(int z, int x, int y, int requestIndex) {
        String subdomain = subdomains[Math.floorMod(requestIndex, subdomains.length)];
        return "https://" + hostWithSubdomain(subdomain) + pathTemplate.formatted(z, x, y);
    }

    /** Prefixes {@code host} with {@code subdomain + "."}, or returns {@code host} alone if the subdomain is blank
     *  (single-host sources, e.g. {@link #OSM_STANDARD}'s 2024 tile usage policy). */
    private String hostWithSubdomain(String subdomain) {
        return subdomain.isEmpty() ? host : subdomain + "." + host;
    }

    @Override
    public String toString() {
        return label;
    }
}

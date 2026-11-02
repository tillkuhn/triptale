package net.timafe.triptale.map;

import java.util.Locale;

/**
 * A raster tile server for {@link OsmMapRenderer} (todo 61): URL template, attribution and the
 * zoom it tops out at. Each source needs its own usage-policy check before being added here;
 * overlay-only sources (Waymarked Trails) are out of scope for this simple enum and a separate
 * future todo.
 *
 * <p>Thunderforest's styles ({@link #THUNDERFOREST_OUTDOORS}, {@link #THUNDERFOREST_OPENCYCLEMAP},
 * {@link #THUNDERFOREST_LANDSCAPE}, {@link #THUNDERFOREST_ATLAS_CANVAS})
 * need an API key (free hobby plan) appended as a query parameter — see {@link #requiresApiKey()}
 * and the {@code apiKey} parameter of {@link #tileUrl(int, int, int, int, String)}. Their terms
 * (checked 2026-11) license tiles under CC-BY-SA 4.0, explicitly allowing storage/publishing,
 * unlike Mapbox. {@code ui.dialog.MapAssistantDialog} hides them from the Style dropdown
 * entirely when no key is configured, rather than showing them disabled.
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
            "\u00a9 OpenStreetMap contributors \u00b7 CyclOSM"),

    /** Maps © Thunderforest, Data © OpenStreetMap contributors (CC-BY-SA 4.0). Max zoom 22. Needs an API key. */
    THUNDERFOREST_OUTDOORS("Thunderforest Outdoors", "tile.thunderforest.com", new String[] {"a", "b", "c"},
            "/outdoors/%d/%d/%d.png", 22,
            "Maps \u00a9 Thunderforest, Data \u00a9 OpenStreetMap contributors", "apikey"),

    /** Maps © Thunderforest, Data © OpenStreetMap contributors (CC-BY-SA 4.0). Max zoom 22. Needs an API key. */
    THUNDERFOREST_OPENCYCLEMAP("Thunderforest OpenCycleMap", "tile.thunderforest.com", new String[] {"a", "b", "c"},
            "/cycle/%d/%d/%d.png", 22,
            "Maps \u00a9 Thunderforest, Data \u00a9 OpenStreetMap contributors", "apikey"),

    /** Maps \u00a9 Thunderforest, Data \u00a9 OpenStreetMap contributors (CC-BY-SA 4.0). Max zoom 22. Needs an API key. */
    THUNDERFOREST_LANDSCAPE("Thunderforest Landscape", "tile.thunderforest.com", new String[] {"a", "b", "c"},
            "/landscape/%d/%d/%d.png", 22,
            "Maps \u00a9 Thunderforest, Data \u00a9 OpenStreetMap contributors", "apikey"),

    /** Maps \u00a9 Thunderforest, Data \u00a9 OpenStreetMap contributors (CC-BY-SA 4.0). Max zoom 22. Needs an API key.
     *  A low-saturation variant of Thunderforest's Atlas style. */
    THUNDERFOREST_ATLAS_CANVAS("Thunderforest Atlas Canvas", "tile.thunderforest.com", new String[] {"a", "b", "c"},
            "/atlas-canvas/%d/%d/%d.png", 22,
            "Maps \u00a9 Thunderforest, Data \u00a9 OpenStreetMap contributors", "apikey");

    private final String label;
    private final String host;
    private final String[] subdomains;
    private final String pathTemplate;
    private final int maxZoom;
    private final String attribution;
    /** Query parameter name the API key is appended as ({@code null} = no key needed). */
    private final String apiKeyParam;

    TileSource(String label, String host, String[] subdomains, String pathTemplate, int maxZoom, String attribution) {
        this(label, host, subdomains, pathTemplate, maxZoom, attribution, null);
    }

    TileSource(String label, String host, String[] subdomains, String pathTemplate, int maxZoom, String attribution,
               String apiKeyParam) {
        this.label = label;
        this.host = host;
        this.subdomains = subdomains;
        this.pathTemplate = pathTemplate;
        this.maxZoom = maxZoom;
        this.attribution = attribution;
        this.apiKeyParam = apiKeyParam;
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

    /** Whether this source needs an API key appended to its tile URL (Thunderforest's styles). */
    public boolean requiresApiKey() {
        return apiKeyParam != null;
    }

    /** The tile URL for {@code z}/{@code x}/{@code y}, round-robining over the source's subdomains;
     *  no API key is appended. Only valid for a source where {@link #requiresApiKey()} is false. */
    public String tileUrl(int z, int x, int y, int requestIndex) {
        return tileUrl(z, x, y, requestIndex, null);
    }

    /** Same as {@link #tileUrl(int, int, int, int)}, appending {@code ?<param>=<apiKey>} for a
     *  source that {@link #requiresApiKey()} — {@code apiKey} is otherwise ignored. */
    public String tileUrl(int z, int x, int y, int requestIndex, String apiKey) {
        String subdomain = subdomains[Math.floorMod(requestIndex, subdomains.length)];
        String url = "https://" + hostWithSubdomain(subdomain) + pathTemplate.formatted(z, x, y);
        if (apiKeyParam != null) {
            url += "?" + apiKeyParam + "=" + (apiKey == null ? "" : apiKey);
        }
        return url;
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

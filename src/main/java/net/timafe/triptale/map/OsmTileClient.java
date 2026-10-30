package net.timafe.triptale.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.imageio.ImageIO;

/**
 * Fetches OSM-family raster tiles over {@code java.net.http}, cached on disk under
 * {@code <data-dir>/.tile-cache/<source>/<z>/<x>/<y>.png} (todo 61, {@link OsmMapRenderer}).
 * Tile usage policies (e.g. OpenTopoMap's) ask for a local cache and an identifying
 * {@code User-Agent} rather than hammering the server; one map render per user action, with a
 * cache that's never evicted here, stays well within "no bulk download".
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public class OsmTileClient {

    private static final Logger log = LoggerFactory.getLogger(OsmTileClient.class);
    static final Duration TIMEOUT = Duration.ofSeconds(8);

    /** A failed tile fetch or an unreadable cached/downloaded tile image. */
    public static class TileException extends RuntimeException {
        public TileException(String message) { super(message); }
        public TileException(String message, Throwable cause) { super(message, cause); }
    }

    private final Path cacheRoot;
    private final String userAgent;
    private final HttpClient http;
    /** Null in production; tests point this at a local server, keeping the generated path/query. */
    private final URI baseOverride;
    private int requestCount;

    public OsmTileClient(Path cacheRoot, String appVersion) {
        this(cacheRoot, appVersion, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), null);
    }

    /** Package-private: lets tests inject an {@link HttpClient} pointed at a local server. */
    OsmTileClient(Path cacheRoot, String appVersion, HttpClient http) {
        this(cacheRoot, appVersion, http, null);
    }

    /** Package-private: {@code baseOverride} (if not null) replaces the scheme/host of every tile URL. */
    OsmTileClient(Path cacheRoot, String appVersion, HttpClient http, URI baseOverride) {
        this.cacheRoot = cacheRoot;
        this.userAgent = "TripTale/" + (appVersion == null || appVersion.isBlank() ? "dev" : appVersion)
                + " (+https://github.com/tillkuhn/triptale)";
        this.http = http;
        this.baseOverride = baseOverride;
    }

    /**
     * The tile, from the disk cache if present, else fetched and cached. Blocks (network on a
     * cache miss): call off the FX thread. Throws {@link TileException} on any failure, so one
     * bad tile doesn't have to take the renderer with it — a caller may choose to skip it.
     */
    public BufferedImage tile(TileSource source, int z, int x, int y) {
        Path file = cacheFile(source, z, x, y);
        if (Files.exists(file)) {
            try {
                BufferedImage cached = ImageIO.read(file.toFile());
                if (cached != null) return cached;
                log.debug("Cached tile unreadable, refetching: {}", file);
            } catch (IOException e) {
                log.debug("Cached tile unreadable, refetching: {}", file, e);
            }
        }
        byte[] bytes = fetch(source, z, x, y);
        cache(file, bytes);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new TileException("Unreadable tile image from " + source);
            return image;
        } catch (IOException e) {
            throw new TileException("Unreadable tile image from " + source, e);
        }
    }

    private Path cacheFile(TileSource source, int z, int x, int y) {
        return cacheRoot.resolve(source.id())
                .resolve(Integer.toString(z))
                .resolve(Integer.toString(x))
                .resolve(y + ".png");
    }

    private void cache(Path file, byte[] bytes) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            log.debug("Could not cache tile {}", file, e);
        }
    }

    private byte[] fetch(TileSource source, int z, int x, int y) {
        URI uri = URI.create(source.tileUrl(z, x, y, requestCount++));
        if (baseOverride != null) {
            String pathAndQuery = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
            uri = baseOverride.resolve(pathAndQuery);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", userAgent)
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> response;
        try {
            log.debug("Tile GET {}", uri);
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new TileException("Timed out after " + TIMEOUT.toSeconds() + " s fetching tile " + uri);
        } catch (IOException e) {
            throw new TileException("Network error fetching tile "
                    + uri + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TileException("Interrupted");
        }
        if (response.statusCode() != 200) {
            throw new TileException(source + " answered HTTP " + response.statusCode() + " for " + uri);
        }
        return response.body();
    }
}

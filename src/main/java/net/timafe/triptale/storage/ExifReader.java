package net.timafe.triptale.storage;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Properties;

/**
 * Reads camera model, aperture, exposure time, ISO, GPS coordinates and capture time from an image file's EXIF
 * metadata, for display in the impressions viewer.
 *
 * <p>Never throws — any parsing failure (corrupt file, unsupported format, missing tags) yields
 * {@link ExifInfo#empty()} or an {@code ExifInfo} with the unavailable fields left {@code null}.
 * No JavaFX dependency — kept in the {@code storage} package per the project's package boundary
 * rule.
 *
 * <p>Reads are cached on disk under {@code <data-dir>/.exif-cache/}, mirroring the image's path
 * relative to the data dir (so the cache survives the data dir moving to a different absolute
 * path), one small {@link Properties} file per image, keyed by file size (todo 80): as long as
 * the source file's size hasn't changed, the cached fields are returned without touching
 * metadata-extractor again. Entries for deleted/moved images are never swept — see todo 81. The
 * cache is skipped entirely (always a live read) when no data dir is configured yet.
 */
@Component
public class ExifReader {

    private static final Logger log = LoggerFactory.getLogger(ExifReader.class);

    /** EXIF's own timestamp format, e.g. {@code 2026:08:19 10:37:10}. */
    private static final DateTimeFormatter EXIF_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    private static final String CACHE_DIR = ".exif-cache";
    private static final String KEY_SIZE = "size";
    private static final String KEY_CAMERA_MODEL = "cameraModel";
    private static final String KEY_APERTURE = "aperture";
    private static final String KEY_EXPOSURE_TIME = "exposureTime";
    private static final String KEY_ISO = "iso";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_DIMENSIONS = "dimensions";
    private static final String KEY_CAPTURE_TIME = "captureTime";

    private final MarkdownStore store;

    public ExifReader(MarkdownStore store) {
        this.store = store;
    }

    public ExifInfo read(Path path) {
        if (path == null) return ExifInfo.empty();
        Path cacheFile = cacheFile(path);
        if (cacheFile != null) {
            ExifInfo cached = readCache(cacheFile, path);
            if (cached != null) return cached;
        }
        ExifInfo info = readLive(path);
        if (cacheFile != null) writeCache(cacheFile, path, info);
        return info;
    }

    private ExifInfo readLive(Path path) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new File(path.toUri()));
            return new ExifInfo(cameraModel(metadata), aperture(metadata), exposureTime(metadata), iso(metadata),
                    latitude(metadata), longitude(metadata), dimensions(metadata), captureTime(metadata));
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            log.debug("Could not read EXIF metadata from {}: {}", path, e.getMessage());
            return ExifInfo.empty();
        }
    }

    /** The mirrored {@code .exif-cache} entry file for {@code path}, or {@code null} if no data dir is configured. */
    private Path cacheFile(Path path) {
        try {
            Path dataDir = store.dataDir();
            Path absolute = path.toAbsolutePath().normalize();
            if (!absolute.startsWith(dataDir)) return null;
            Path relative = dataDir.relativize(absolute);
            return dataDir.resolve(CACHE_DIR).resolve(relative.toString() + ".properties");
        } catch (StorageException e) {
            return null;
        }
    }

    private ExifInfo readCache(Path cacheFile, Path imagePath) {
        if (!Files.isRegularFile(cacheFile)) return null;
        try {
            long currentSize = Files.size(imagePath);
            Properties props = new Properties();
            try (var in = Files.newInputStream(cacheFile)) {
                props.load(in);
            }
            long cachedSize = Long.parseLong(props.getProperty(KEY_SIZE, "-1"));
            if (cachedSize != currentSize) return null;
            return new ExifInfo(
                    props.getProperty(KEY_CAMERA_MODEL),
                    props.getProperty(KEY_APERTURE),
                    props.getProperty(KEY_EXPOSURE_TIME),
                    props.getProperty(KEY_ISO),
                    parseDouble(props.getProperty(KEY_LATITUDE)),
                    parseDouble(props.getProperty(KEY_LONGITUDE)),
                    props.getProperty(KEY_DIMENSIONS),
                    parseCaptureTime(props.getProperty(KEY_CAPTURE_TIME)));
        } catch (IOException | NumberFormatException e) {
            log.debug("Could not read EXIF cache entry {}: {}", cacheFile, e.getMessage());
            return null;
        }
    }

    private void writeCache(Path cacheFile, Path imagePath, ExifInfo info) {
        try {
            long size = Files.size(imagePath);
            Properties props = new Properties();
            props.setProperty(KEY_SIZE, Long.toString(size));
            putIfPresent(props, KEY_CAMERA_MODEL, info.cameraModel());
            putIfPresent(props, KEY_APERTURE, info.aperture());
            putIfPresent(props, KEY_EXPOSURE_TIME, info.exposureTime());
            putIfPresent(props, KEY_ISO, info.iso());
            if (info.latitude() != null) props.setProperty(KEY_LATITUDE, Double.toString(info.latitude()));
            if (info.longitude() != null) props.setProperty(KEY_LONGITUDE, Double.toString(info.longitude()));
            putIfPresent(props, KEY_DIMENSIONS, info.dimensions());
            if (info.captureTime() != null) props.setProperty(KEY_CAPTURE_TIME, info.captureTime().toString());
            Files.createDirectories(cacheFile.getParent());
            try (var out = Files.newOutputStream(cacheFile)) {
                props.store(out, null);
            }
        } catch (IOException e) {
            log.debug("Could not write EXIF cache entry {}: {}", cacheFile, e.getMessage());
        }
    }

    private static void putIfPresent(Properties props, String key, String value) {
        if (value != null) props.setProperty(key, value);
    }

    private static Double parseDouble(String s) {
        return s == null ? null : Double.valueOf(s);
    }

    private static LocalDateTime parseCaptureTime(String s) {
        return s == null ? null : LocalDateTime.parse(s);
    }

    private static String cameraModel(Metadata metadata) {
        ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (dir == null) return null;
        String make = trimToNull(dir.getString(ExifIFD0Directory.TAG_MAKE));
        String model = trimToNull(dir.getString(ExifIFD0Directory.TAG_MODEL));
        if (model == null) return make;
        if (make != null && !model.toLowerCase().contains(make.toLowerCase())) {
            return make + " " + model;
        }
        return model;
    }

    private static String aperture(Metadata metadata) {
        ExifSubIFDDirectory dir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (dir == null) return null;
        try {
            Double fNumber = dir.getDoubleObject(ExifSubIFDDirectory.TAG_FNUMBER);
            if (fNumber != null) {
                double rounded = Math.round(fNumber * 10) / 10.0;
                String formatted = (rounded == Math.rint(rounded))
                        ? String.format(java.util.Locale.ROOT, "%.0f", rounded)
                        : String.format(java.util.Locale.ROOT, "%.1f", rounded);
                return "f/" + formatted;
            }
        } catch (Exception e) {
            // fall through to description-based fallback
        }
        return trimToNull(dir.getDescription(ExifSubIFDDirectory.TAG_FNUMBER));
    }

    private static String exposureTime(Metadata metadata) {
        ExifSubIFDDirectory dir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (dir == null) return null;
        com.drew.lang.Rational rational = dir.getRational(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
        if (rational == null) return trimToNull(dir.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME));
        return formatExposureTime(rational.doubleValue());
    }

    /**
     * Formats an exposure time in seconds as a human-readable fraction (e.g. "1/33 sec") for
     * exposures shorter than one second, or as a decimal number of seconds (e.g. "2.5 sec") for
     * longer exposures. Some camera JPEGs (notably certain Samsung models) report the raw
     * numerator/denominator un-simplified (e.g. "3030303/100000000 sec"); rounding to the nearest
     * whole fraction avoids surfacing that noise.
     */
    static String formatExposureTime(double seconds) {
        if (seconds <= 0) return null;
        if (seconds >= 1) {
            double rounded = Math.round(seconds * 10) / 10.0;
            return (rounded == Math.rint(rounded) ? String.format(java.util.Locale.ROOT, "%.0f", rounded)
                    : String.format(java.util.Locale.ROOT, "%.1f", rounded)) + " sec";
        }
        long denominator = Math.round(1.0 / seconds);
        return "1/" + denominator + " sec";
    }

    private static String iso(Metadata metadata) {
        ExifSubIFDDirectory dir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (dir == null) return null;
        String iso = trimToNull(dir.getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
        return iso == null ? null : "ISO " + iso;
    }

    /**
     * Pixel dimensions read from the JPEG's own SOF marker (not an EXIF tag), so it's available
     * even for images with no EXIF metadata at all.
     */
    private static String dimensions(Metadata metadata) {
        JpegDirectory dir = metadata.getFirstDirectoryOfType(JpegDirectory.class);
        if (dir == null) return null;
        try {
            return dir.getImageWidth() + "×" + dir.getImageHeight();
        } catch (MetadataException e) {
            return null;
        }
    }

    /**
     * {@code DateTimeOriginal} as camera-local time, or null when it's missing or unparseable (some
     * cameras write blanks like {@code "    :  :     "} when the clock was never set).
     */
    static LocalDateTime captureTime(Metadata metadata) {
        ExifSubIFDDirectory dir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (dir == null) return null;
        String raw = trimToNull(dir.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL));
        if (raw == null) return null;
        try {
            return LocalDateTime.parse(raw, EXIF_TIMESTAMP);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Double latitude(Metadata metadata) {
        GeoLocation loc = geoLocation(metadata);
        return loc == null ? null : loc.getLatitude();
    }

    private static Double longitude(Metadata metadata) {
        GeoLocation loc = geoLocation(metadata);
        return loc == null ? null : loc.getLongitude();
    }

    private static GeoLocation geoLocation(Metadata metadata) {
        GpsDirectory dir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (dir == null) return null;
        GeoLocation loc = dir.getGeoLocation();
        return (loc == null || loc.isZero()) ? null : loc;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

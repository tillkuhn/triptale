package net.timafe.triptale.storage;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Reads camera model, aperture, exposure time, ISO and GPS coordinates from an image file's EXIF
 * metadata, for display in the impressions viewer.
 *
 * <p>Never throws — any parsing failure (corrupt file, unsupported format, missing tags) yields
 * {@link ExifInfo#empty()} or an {@code ExifInfo} with the unavailable fields left {@code null}.
 * No JavaFX dependency — kept in the {@code storage} package per the project's package boundary
 * rule.
 */
@Component
public class ExifReader {

    private static final Logger log = LoggerFactory.getLogger(ExifReader.class);

    public ExifInfo read(Path path) {
        if (path == null) return ExifInfo.empty();
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new File(path.toUri()));
            return new ExifInfo(cameraModel(metadata), aperture(metadata), exposureTime(metadata), iso(metadata),
                    latitude(metadata), longitude(metadata));
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            log.debug("Could not read EXIF metadata from {}: {}", path, e.getMessage());
            return ExifInfo.empty();
        }
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

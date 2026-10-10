package net.timafe.triptale.impressions;

import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resolves an {@link ImpressionSource} to a day's images (todo 47). The base filter
 * ({@code impressionsBaseFilter}) always applies, so non-images (GPX, PDF, …) never show up;
 * the fave filter is an optional subset on top, see {@link #faveFilter()}.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
@Component
public class ImpressionsService {

    private static final Logger log = LoggerFactory.getLogger(ImpressionsService.class);

    private final ImpressionsResolver resolver;
    private final AttachmentsDir attachmentsDir;
    private final SettingsStore settingsStore;

    public ImpressionsService(ImpressionsResolver resolver, AttachmentsDir attachmentsDir,
                              SettingsStore settingsStore) {
        this.resolver = resolver;
        this.attachmentsDir = attachmentsDir;
        this.settingsStore = settingsStore;
    }

    /** True if the photo library source has a pattern to work with. */
    public boolean photoLibraryConfigured() {
        return !settingsStore.load().getImpressionsFilePattern().isBlank();
    }

    /** The folder the source reads from for that day, if it exists. */
    public Optional<Path> directory(ImpressionSource source, Trip trip, LocalDate date) {
        return switch (source) {
            case ImpressionSource.PhotoLibrary p ->
                    resolver.resolveDirectory(settingsStore.load().getImpressionsFilePattern(), trip, date);
            case ImpressionSource.TripAttachments a -> trip == null || date == null
                    ? Optional.empty()
                    : Optional.of(attachmentsDir.dayDir(trip.ref(), date)).filter(Files::isDirectory);
            case ImpressionSource.Folder f -> Optional.of(f.dir()).filter(Files::isDirectory);
        };
    }

    /** The day's images from the source, base filter applied, sorted by filename. */
    public List<Path> images(ImpressionSource source, Trip trip, LocalDate date) {
        AppSettings settings = settingsStore.load();
        List<Path> files = source instanceof ImpressionSource.PhotoLibrary
                ? resolver.resolve(settings.getImpressionsFilePattern(), trip, date)
                : directory(source, trip, date).map(ImpressionsService::listFiles).orElse(List.of());
        return ImageFilter.parse(settings.getImpressionsBaseFilter()).apply(files);
    }

    /** The configured {@code impressionsFaveFilter}; {@link ImageFilter#isEmpty() empty} if none. */
    public ImageFilter faveFilter() {
        return ImageFilter.parse(settingsStore.load().getImpressionsFaveFilter());
    }

    /**
     * Copies the images into the day's attachment folder; same-name files there are overwritten,
     * like Add Attachments. Never commits (see the pending-commit rule).
     */
    public List<Path> importToAttachments(Trip trip, LocalDate date, List<Path> images) {
        return attachmentsDir.addFiles(trip.ref(), date, images);
    }

    private static List<Path> listFiles(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to scan impressions directory {}: {}", dir, e.getMessage());
            return List.of();
        }
    }
}

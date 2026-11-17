package net.timafe.triptale.impressions;

import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.SettingsStore;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds recorded tours for a day (todo 90): GPX files in an external track app's backup folder,
 * located via the {@code gpxFilePattern} setting (same variables and resolver as the
 * impressions pattern). Filename-only matching, no GPX parsing. No JavaFX imports.
 */
@Component
public class GpxCandidates {

    private final ImpressionsResolver resolver;
    private final SettingsStore settingsStore;
    private final AttachmentsDir attachmentsDir;

    public GpxCandidates(ImpressionsResolver resolver, SettingsStore settingsStore, AttachmentsDir attachmentsDir) {
        this.resolver = resolver;
        this.settingsStore = settingsStore;
        this.attachmentsDir = attachmentsDir;
    }

    /**
     * One {@code .gpx} per recorded tour matching the day, sorted by name; empty if the setting is
     * blank or nothing matches. A pattern that also matches the {@code .json} sidecars (e.g. ending
     * in {@code _*}) does not produce extra entries: a sidecar stands for its GPX, a sidecar without
     * a GPX is ignored. Use {@link #sidecarOf} to find the sidecar of a candidate.
     */
    public List<Path> find(Trip trip, LocalDate date) {
        String pattern = settingsStore.load().getGpxFilePattern();
        if (pattern == null || pattern.isBlank() || date == null) return List.of();
        return resolver.resolve(pattern, trip, date).stream()
                .map(p -> isExt(p, ".json") ? withExtension(p, ".gpx") : p)
                .filter(p -> isExt(p, ".gpx") && Files.isRegularFile(p))
                .distinct()
                .sorted()
                .toList();
    }

    /** The optional same-basename {@code .json} sidecar next to a GPX file (todo 94). */
    public static Optional<Path> sidecarOf(Path gpx) {
        Path json = withExtension(gpx, ".json");
        return Files.isRegularFile(json) ? Optional.of(json) : Optional.empty();
    }

    private static boolean isExt(Path p, String ext) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext);
    }

    private static Path withExtension(Path p, String ext) {
        String name = p.getFileName().toString();
        return p.resolveSibling(name.substring(0, name.lastIndexOf('.')) + ext);
    }

    /** Lower-cased file names in the day's backpack; a candidate counts as imported if its {@code .gpx} name is in here. */
    public java.util.Set<String> importedNames(Trip trip, LocalDate date) {
        if (trip == null || date == null) return java.util.Set.of();
        return attachmentsDir.dayFiles(trip.ref(), date).stream()
                .map(f -> f.name().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
    }

    /** True if the day's backpack already holds a {@code .gpx} file. */
    public boolean hasImported(Trip trip, LocalDate date) {
        if (trip == null || date == null) return false;
        return attachmentsDir.dayFiles(trip.ref(), date).stream()
                .anyMatch(f -> f.name().toLowerCase(java.util.Locale.ROOT).endsWith(".gpx"));
    }
}

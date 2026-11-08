package net.timafe.triptale.impressions;

import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.SettingsStore;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

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

    /** Backup files matching the day, sorted by name; empty if the setting is blank or nothing matches. */
    public List<Path> find(Trip trip, LocalDate date) {
        String pattern = settingsStore.load().getGpxFilePattern();
        if (pattern == null || pattern.isBlank() || date == null) return List.of();
        return resolver.resolve(pattern, trip, date);
    }

    /** True if the day's backpack already holds a {@code .gpx} file. */
    public boolean hasImported(Trip trip, LocalDate date) {
        if (trip == null || date == null) return false;
        return attachmentsDir.dayFiles(trip.ref(), date).stream()
                .anyMatch(f -> f.name().toLowerCase(java.util.Locale.ROOT).endsWith(".gpx"));
    }
}

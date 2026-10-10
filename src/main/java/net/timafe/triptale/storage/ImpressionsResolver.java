package net.timafe.triptale.storage;

import net.timafe.triptale.domain.Trip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Resolves "impressions" (day-entry images) on disk from a configurable pattern such as
 * {@code ${HOME}/Pictures/${TRIP_YEAR}_${TRIP_MONTH}_*}{@code /${ENTRY_YEAR}_${ENTRY_MONTH}_${ENTRY_DAY}/*.jpg}.
 *
 * <p>Supported variables:
 * <ul>
 *     <li>{@code ${HOME}} — the current user's home directory</li>
 *     <li>{@code ${TRIP_SLUG}} — the active trip's slug</li>
 *     <li>{@code ${TRIP_YEAR}} — the active trip's start-date year, e.g. {@code 2026}</li>
 *     <li>{@code ${TRIP_MONTH}} — the active trip's start-date month, zero-padded, e.g. {@code 06}</li>
 *     <li>{@code ${TRIP_DAY}} — the active trip's start-date day, zero-padded, e.g. {@code 04}</li>
 *     <li>{@code ${ENTRY_YEAR}} — the entry's year, e.g. {@code 2026}</li>
 *     <li>{@code ${ENTRY_MONTH}} — the entry's month, zero-padded, e.g. {@code 08}</li>
 *     <li>{@code ${ENTRY_DAY}} — the entry's day of month, zero-padded, e.g. {@code 07}</li>
 * </ul>
 *
 * <p>Variable substitution and directory-segment glob matching are delegated to
 * {@link PathPatternResolver}. The pattern's final {@code /}-separated segment is always treated
 * as a filename glob matched against files in the resolved directory; everything before it is
 * resolved as a directory (each segment may itself be a glob). The resolved directory is cached
 * per substituted directory pattern for the lifetime of the application — when it only uses
 * trip variables that is one lookup per trip, so only the filename glob is re-evaluated on every
 * date navigation; {@code ENTRY_*} variables in a directory segment make it one lookup per day.
 * Misses are cached too. Restart the app if the underlying folder is created or reorganized
 * mid-session.
 *
 * <p>No JavaFX dependency — kept in the {@code storage} package per the project's package
 * boundary rule so it stays independently unit-testable.
 */
@Component
public class ImpressionsResolver {

    private static final Logger log = LoggerFactory.getLogger(ImpressionsResolver.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");

    private final PathPatternResolver pathPatternResolver;
    private final Map<String, Optional<Path>> resolvedDirectoryCache = new ConcurrentHashMap<>();

    public ImpressionsResolver(PathPatternResolver pathPatternResolver) {
        this.pathPatternResolver = pathPatternResolver;
    }

    /**
     * Resolves the given pattern for the given trip and date, returning matching image files
     * sorted by filename. Returns an empty list if the pattern is blank, the date is missing,
     * the directory can't be resolved (missing, or references a trip variable with no trip in
     * scope), or nothing matches.
     */
    public List<Path> resolve(String pattern, Trip trip, LocalDate date) {
        if (date == null) return List.of();
        Optional<Path> dir = resolveDirectory(pattern, trip, date);
        if (dir.isEmpty()) return List.of();

        String filenamePattern = pattern.substring(pattern.lastIndexOf('/') + 1);
        String glob = PathPatternResolver.substitute(filenamePattern, variables(trip, date));
        return matchFiles(dir.get(), glob);
    }

    /**
     * Resolves the directory half of the pattern (everything before the last {@code /}) for the
     * given trip and date; empty if the pattern is blank, has no directory segment, or doesn't
     * resolve. A null date leaves {@code ENTRY_*} variables unsubstituted, so a directory that
     * uses them won't resolve.
     */
    public Optional<Path> resolveDirectory(String pattern, Trip trip, LocalDate date) {
        if (pattern == null || pattern.isBlank()) return Optional.empty();
        int sep = pattern.lastIndexOf('/');
        if (sep < 0) {
            log.warn("Impressions pattern has no directory segment: {}", pattern);
            return Optional.empty();
        }
        String directoryPattern = PathPatternResolver.substitute(pattern.substring(0, sep), variables(trip, date));
        return resolvedDirectoryCache.computeIfAbsent(directoryPattern,
                k -> pathPatternResolver.resolveDirectory(directoryPattern, Map.of()));
    }

    private static Map<String, String> variables(Trip trip, LocalDate date) {
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("HOME", System.getProperty("user.home", ""));
        if (trip != null && trip.startDate() != null) {
            vars.put("TRIP_SLUG", trip.slug());
            vars.put("TRIP_YEAR", Integer.toString(trip.startDate().getYear()));
            vars.put("TRIP_MONTH", trip.startDate().format(MONTH_FORMAT));
            vars.put("TRIP_DAY", trip.startDate().format(DAY_FORMAT));
        }
        if (date != null) {
            vars.put("ENTRY_YEAR", Integer.toString(date.getYear()));
            vars.put("ENTRY_MONTH", date.format(MONTH_FORMAT));
            vars.put("ENTRY_DAY", date.format(DAY_FORMAT));
        }
        return vars;
    }

    private static List<Path> matchFiles(Path dir, String globPattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> matches = new ArrayList<>();
            s.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(p.getFileName()))
                    .forEach(matches::add);
            matches.sort(Comparator.comparing(p -> p.getFileName().toString()));
            return matches;
        } catch (IOException e) {
            log.warn("Failed to scan impressions directory {}: {}", dir, e.getMessage());
            return List.of();
        }
    }
}

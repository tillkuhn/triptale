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
 * {@code ${HOME}/Pictures/${TRIP_YEAR}/${TRIP_MONTH}_??_${TRIP_SLUG}/00_Faves/output/${DATE}*.jpg}.
 *
 * <p>Supported variables:
 * <ul>
 *     <li>{@code ${HOME}} — the current user's home directory</li>
 *     <li>{@code ${DATE}} — the entry's date formatted as {@code yyyyMMdd}</li>
 *     <li>{@code ${TRIP_SLUG}} — the active trip's slug</li>
 *     <li>{@code ${TRIP_YEAR}} — the active trip's start-date year, e.g. {@code 2026}</li>
 *     <li>{@code ${TRIP_MONTH}} — the active trip's start-date month, zero-padded, e.g. {@code 06}</li>
 * </ul>
 *
 * <p>Variable substitution and directory-segment glob matching are delegated to
 * {@link PathPatternResolver}. The pattern's final {@code /}-separated segment is always treated
 * as a filename glob matched against files in the resolved directory; everything before it is
 * resolved as a directory (each segment may itself be a glob). The resolved directory is cached
 * per {@code (pattern, trip)} for the lifetime of the application — that half of the pattern is
 * fixed for a trip's whole lifetime, so only the filename glob is re-evaluated on every call
 * (e.g. on every date navigation). Restart the app if the underlying folder is reorganized
 * mid-session.
 *
 * <p>No JavaFX dependency — kept in the {@code storage} package per the project's package
 * boundary rule so it stays independently unit-testable.
 */
@Component
public class ImpressionsResolver {

    private static final Logger log = LoggerFactory.getLogger(ImpressionsResolver.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TRIP_MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");

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
        if (pattern == null || pattern.isBlank() || date == null) return List.of();

        int sep = pattern.lastIndexOf('/');
        if (sep < 0) {
            log.warn("Impressions pattern has no directory segment: {}", pattern);
            return List.of();
        }
        String directoryPattern = pattern.substring(0, sep);
        String filenamePattern = pattern.substring(sep + 1);

        Map<String, String> vars = tripVariables(trip);
        String cacheKey = directoryPattern + "||" + tripCacheKey(trip);
        Optional<Path> dir = resolvedDirectoryCache.computeIfAbsent(cacheKey,
                k -> pathPatternResolver.resolveDirectory(directoryPattern, vars));
        if (dir.isEmpty()) return List.of();

        String glob = PathPatternResolver.substitute(filenamePattern, Map.of("DATE", date.format(DATE_FORMAT)));
        return matchFiles(dir.get(), glob);
    }

    private static Map<String, String> tripVariables(Trip trip) {
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("HOME", System.getProperty("user.home", ""));
        if (trip != null && trip.startDate() != null) {
            vars.put("TRIP_SLUG", trip.slug());
            vars.put("TRIP_YEAR", Integer.toString(trip.startDate().getYear()));
            vars.put("TRIP_MONTH", trip.startDate().format(TRIP_MONTH_FORMAT));
        }
        return vars;
    }

    private static String tripCacheKey(Trip trip) {
        return trip == null ? "" : trip.year() + "/" + trip.slug();
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

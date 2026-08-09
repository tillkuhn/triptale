package net.timafe.triptale.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves "impressions" (day-entry images) on disk from a configurable pattern such as
 * {@code ${HOME}/Pictures/00_Faves/output/${DATE}*.jpg}.
 *
 * <p>Supported variables:
 * <ul>
 *     <li>{@code ${HOME}} — the current user's home directory</li>
 *     <li>{@code ${DATE}} — the entry's date formatted as {@code yyyyMMdd}</li>
 * </ul>
 *
 * <p>After variable substitution, the pattern is split at the last {@code /}: everything before
 * it must resolve to an existing directory (scanned non-recursively), everything after it is
 * treated as a glob (see {@link PathMatcher}, {@code glob:} syntax) matched against filenames in
 * that directory.
 *
 * <p>No JavaFX dependency — kept in the {@code storage} package per the project's package
 * boundary rule so it stays independently unit-testable.
 */
@Component
public class ImpressionsResolver {

    private static final Logger log = LoggerFactory.getLogger(ImpressionsResolver.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Resolves the given pattern for the given date, returning matching image files sorted by
     * filename. Returns an empty list if the pattern is blank, the directory doesn't exist, or
     * nothing matches.
     */
    public List<Path> resolve(String pattern, LocalDate date) {
        if (pattern == null || pattern.isBlank() || date == null) return List.of();

        String substituted = substitute(pattern, date);
        int sep = substituted.lastIndexOf('/');
        if (sep < 0) {
            log.warn("Impressions pattern has no directory segment: {}", pattern);
            return List.of();
        }
        Path dir = Paths.get(substituted.substring(0, sep));
        String globPattern = substituted.substring(sep + 1);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

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

    private static String substitute(String pattern, LocalDate date) {
        String home = System.getProperty("user.home", "");
        return pattern
                .replace("${HOME}", home)
                .replace("${DATE}", date.format(DATE_FORMAT));
    }
}

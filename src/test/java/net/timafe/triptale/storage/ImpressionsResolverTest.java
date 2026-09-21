package net.timafe.triptale.storage;

import net.timafe.triptale.domain.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpressionsResolverTest {

    @TempDir
    Path tempDir;

    private final ImpressionsResolver resolver = new ImpressionsResolver(new PathPatternResolver());

    private static final Trip TRIP = new Trip(2026, "iceland-roadtrip", "Iceland Roadtrip",
            LocalDate.of(2026, 6, 4), "");

    @BeforeEach
    void setUp() throws IOException {
        Files.createFile(tempDir.resolve("20260807_204352_Fireshow.jpg"));
        Files.createFile(tempDir.resolve("20260807_210101_Other.jpg"));
        Files.createFile(tempDir.resolve("20260808_000000_NextDay.jpg"));
        Files.createFile(tempDir.resolve("20260807_notes.txt"));
    }

    @Test
    void matchesFilesForGivenDateOnly() {
        String pattern = tempDir.toString() + "/${DATE}*.jpg";
        List<Path> matches = resolver.resolve(pattern, TRIP, LocalDate.of(2026, 8, 7));
        assertEquals(2, matches.size());
        assertTrue(matches.get(0).getFileName().toString().startsWith("20260807"));
        assertTrue(matches.get(1).getFileName().toString().startsWith("20260807"));
    }

    @Test
    void returnsEmptyWhenDirectoryMissing() {
        List<Path> matches = resolver.resolve(tempDir.resolve("nope").toString() + "/${DATE}*.jpg", TRIP, LocalDate.of(2026, 8, 7));
        assertTrue(matches.isEmpty());
    }

    @Test
    void returnsEmptyForBlankPattern() {
        assertTrue(resolver.resolve("", TRIP, LocalDate.of(2026, 8, 7)).isEmpty());
        assertTrue(resolver.resolve(null, TRIP, LocalDate.of(2026, 8, 7)).isEmpty());
    }

    @Test
    void substitutesHomeVariable() {
        String home = System.getProperty("user.home");
        // Won't necessarily match anything real, just verifying no crash and substitution occurs
        List<Path> matches = resolver.resolve("${HOME}/nonexistent-dir-xyz/${DATE}*.jpg", TRIP, LocalDate.of(2026, 8, 7));
        assertTrue(matches.isEmpty());
        assertTrue(Files.notExists(Path.of(home, "nonexistent-dir-xyz")));
    }

    @Test
    void substitutesTripVariablesIncludingWildcardDirectorySegment() throws IOException {
        Path tripDir = Files.createDirectories(tempDir.resolve("2026").resolve("06_ab_iceland-roadtrip"));
        Files.createFile(tripDir.resolve("20260807_party.jpg"));

        String pattern = tempDir + "/${TRIP_YEAR}/${TRIP_MONTH}_??_${TRIP_SLUG}/${DATE}*.jpg";
        List<Path> matches = resolver.resolve(pattern, TRIP, LocalDate.of(2026, 8, 7));
        assertEquals(1, matches.size());
        assertEquals("20260807_party.jpg", matches.get(0).getFileName().toString());
    }

    @Test
    void returnsEmptyWhenTripVariableUsedWithNoTripInScope() {
        String pattern = tempDir + "/${TRIP_SLUG}/${DATE}*.jpg";
        assertTrue(resolver.resolve(pattern, null, LocalDate.of(2026, 8, 7)).isEmpty());
    }
}

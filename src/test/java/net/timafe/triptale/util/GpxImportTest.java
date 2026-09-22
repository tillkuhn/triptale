package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpxImportTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesNameDateAndFirstTrackPoint() throws IOException {
        Path file = writeGpx("""
                <?xml version='1.0' encoding='UTF-8'?>
                <gpx>
                  <trk>
                    <name>🍷🚵 RheinRaufTour #1</name>
                    <trkseg>
                      <trkpt lat="50.352114" lon="7.589085">
                        <ele>116.469940</ele>
                        <time>2025-04-18T09:57:50.374Z</time>
                      </trkpt>
                      <trkpt lat="50.352063" lon="7.588966">
                        <ele>116.469940</ele>
                        <time>2025-04-18T09:58:06.788Z</time>
                      </trkpt>
                    </trkseg>
                  </trk>
                </gpx>
                """);

        Optional<GpxImport.Parsed> result = GpxImport.parse(file.toFile());

        assertTrue(result.isPresent());
        assertEquals("🍷🚵 RheinRaufTour #1", result.get().name());
        assertEquals(LocalDate.of(2025, 4, 18), result.get().date());
        assertEquals(50.352114, result.get().lat());
        assertEquals(7.589085, result.get().lon());
    }

    @Test
    void emptyWhenTrackNameMissing() throws IOException {
        Path file = writeGpx("""
                <gpx>
                  <trk>
                    <trkseg>
                      <trkpt lat="50.0" lon="7.0"><time>2025-04-18T09:57:50Z</time></trkpt>
                    </trkseg>
                  </trk>
                </gpx>
                """);

        assertTrue(GpxImport.parse(file.toFile()).isEmpty());
    }

    @Test
    void emptyWhenNoTrackPoints() throws IOException {
        Path file = writeGpx("""
                <gpx>
                  <trk>
                    <name>Empty Track</name>
                    <trkseg></trkseg>
                  </trk>
                </gpx>
                """);

        assertTrue(GpxImport.parse(file.toFile()).isEmpty());
    }

    @Test
    void emptyWhenFileIsNotXml() throws IOException {
        Path file = writeGpx("this is not gpx at all");

        assertTrue(GpxImport.parse(file.toFile()).isEmpty());
    }

    private Path writeGpx(String content) throws IOException {
        Path file = tempDir.resolve("track.gpx");
        Files.writeString(file, content);
        return file;
    }
}

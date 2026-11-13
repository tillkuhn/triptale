package net.timafe.triptale.sync;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3ClientTest {

    @Test
    void contentTypeOf_mapsGpx() throws Exception {
        assertEquals("application/gpx+xml", S3Client.contentTypeOf(Path.of("2026-06-04-Track.GPX")));
    }

    @Test
    void contentTypeOf_fallsBackToOctetStream() throws Exception {
        assertEquals("application/octet-stream", S3Client.contentTypeOf(Path.of("data.unknownext123")));
    }
}

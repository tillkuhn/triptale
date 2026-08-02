package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelativeTimeTest {

    @Test
    void justNowUnderTenSeconds() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("just now", RelativeTime.ago(now.minusSeconds(9), now));
        assertEquals("just now", RelativeTime.ago(now, now));
    }

    @Test
    void secondsBucket() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("10 seconds ago", RelativeTime.ago(now.minusSeconds(10), now));
        assertEquals("59 seconds ago", RelativeTime.ago(now.minusSeconds(59), now));
    }

    @Test
    void minutesBucketWithSingular() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("1 minute ago", RelativeTime.ago(now.minusSeconds(60), now));
        assertEquals("5 minutes ago", RelativeTime.ago(now.minusSeconds(300), now));
        assertEquals("59 minutes ago", RelativeTime.ago(now.minusSeconds(59 * 60), now));
    }

    @Test
    void hoursBucketWithSingular() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("1 hour ago", RelativeTime.ago(now.minusSeconds(3600), now));
        assertEquals("23 hours ago", RelativeTime.ago(now.minusSeconds(23 * 3600), now));
    }

    @Test
    void daysBucketWithNoUpperCap() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("1 day ago", RelativeTime.ago(now.minusSeconds(24 * 3600), now));
        assertEquals("2 days ago", RelativeTime.ago(now.minusSeconds(2 * 24 * 3600), now));
        assertEquals("340 days ago", RelativeTime.ago(now.minusSeconds(340L * 24 * 3600), now));
    }

    @Test
    void futureInstantClampsToJustNow() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("just now", RelativeTime.ago(now.plusSeconds(60), now));
    }
}

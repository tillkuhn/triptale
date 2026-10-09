package net.timafe.triptale.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BucketUrlTest {

    @Test
    void parse_bucketOnly() {
        assertEquals(new BucketUrl("triptale-backpack", ""), BucketUrl.parse("s3://triptale-backpack"));
    }

    @Test
    void parse_bucketWithTrailingSlash() {
        assertEquals(new BucketUrl("triptale-backpack", ""), BucketUrl.parse("s3://triptale-backpack/"));
    }

    @Test
    void parse_prefixStripsSurroundingSlashes() {
        assertEquals(new BucketUrl("my.bucket", "trips/2026"), BucketUrl.parse("  S3://my.bucket//trips/2026//  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "triptale-backpack", "https://triptale-backpack", "s3://",
            "s3://ab", "s3://Upper-Case", "s3://-leading-hyphen", "s3://under_score"})
    void parse_rejectsMalformed(String url) {
        assertThrows(IllegalArgumentException.class, () -> BucketUrl.parse(url));
    }

    @Test
    void parse_nullIsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> BucketUrl.parse(null));
    }
}

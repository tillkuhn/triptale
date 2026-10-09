package net.timafe.triptale.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AppSettingsTest {

    @Test
    void attachments_defaultsAreBlankWithDefaultRegion() {
        AppSettings.Attachments attachments = new AppSettings().getAttachments();
        assertEquals("", attachments.getBucketUrl());
        assertEquals("eu-central-1", attachments.getRegion());
        assertEquals("", attachments.getAccessKeyId());
        assertEquals("", attachments.getSecretAccessKey());
        assertTrue(attachments.validationError().isEmpty());
    }

    @Test
    void attachments_blankValuesAreValid() {
        AppSettings.Attachments attachments = attachments("", "");
        assertTrue(attachments.validationError().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eu-central-1", "us-east-1", "us-gov-west-1", "ap-southeast-2"})
    void attachments_validRegions(String region) {
        assertTrue(attachments("s3://triptale-backpack/x", region).validationError().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eu-central", "EU-CENTRAL-1", "frankfurt", "auto"})
    void attachments_invalidRegion(String region) {
        assertTrue(attachments("", region).validationError().orElseThrow().contains(region));
    }

    @Test
    void attachments_invalidBucketUrl() {
        assertTrue(attachments("triptale-backpack", "eu-central-1").validationError().orElseThrow()
                .contains("s3://"));
    }

    @Test
    void attachments_cloudRequiresCompleteConfig() {
        AppSettings.Attachments attachments = attachments("s3://triptale-backpack", "eu-central-1");
        attachments.setSync(AppSettings.AttachmentSync.CLOUD);
        attachments.setAccessKeyId("AKIAEXAMPLE");
        assertTrue(attachments.validationError().orElseThrow().startsWith("Cloud sync needs"));

        attachments.setSecretAccessKey("secret");
        assertTrue(attachments.validationError().isEmpty());
    }

    @Test
    void attachments_syncDefaultsToOffAndNullFallsBack() {
        AppSettings.Attachments attachments = new AppSettings.Attachments();
        assertEquals(AppSettings.AttachmentSync.OFF, attachments.getSync());
        attachments.setSync(null);
        assertEquals(AppSettings.AttachmentSync.OFF, attachments.getSync());
    }

    private static AppSettings.Attachments attachments(String bucketUrl, String region) {
        AppSettings.Attachments attachments = new AppSettings.Attachments();
        attachments.setBucketUrl(bucketUrl);
        attachments.setRegion(region);
        return attachments;
    }
}

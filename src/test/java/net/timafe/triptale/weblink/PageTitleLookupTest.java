package net.timafe.triptale.weblink;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PageTitleLookupTest {

    @Test
    void ogTitleWinsOverTitle() {
        String html = "<head><title>Plain</title><meta property=\"og:title\" content=\"Social\"></head>";
        assertThat(PageTitleLookup.extractTitle(html)).contains("Social");
    }

    @Test
    void ogTitleWithContentFirstAndSingleQuotes() {
        assertThat(PageTitleLookup.extractTitle("<meta content='Hotel Foo | Booking.com' property='og:title'/>"))
                .contains("Hotel Foo | Booking.com");
    }

    @Test
    void fallsBackToTitle() {
        assertThat(PageTitleLookup.extractTitle("<html><TITLE>Hello</TITLE>")).contains("Hello");
    }

    @Test
    void multiLineTitleIsCollapsed() {
        assertThat(PageTitleLookup.extractTitle("<title>\n  Foo\n   Bar \n</title>")).contains("Foo Bar");
    }

    @Test
    void entitiesAreDecoded() {
        assertThat(PageTitleLookup.extractTitle("<title>Tom &amp; Jerry&#39;s &#x2013; M&uuml;nchen</title>"))
                .contains("Tom & Jerry's – München");
    }

    @Test
    void unknownEntityStaysAsIs() {
        assertThat(PageTitleLookup.extractTitle("<title>A &bogus; B</title>")).contains("A &bogus; B");
    }

    @Test
    void longTitleIsCapped() {
        String title = PageTitleLookup.extractTitle("<title>" + "a".repeat(100) + "</title>").orElseThrow();
        assertThat(title).hasSize(PageTitleLookup.MAX_LENGTH).endsWith("…");
    }

    @Test
    void noTitleOrBlankTitleIsEmpty() {
        assertThat(PageTitleLookup.extractTitle("<html><body>hi</body>")).isEmpty();
        assertThat(PageTitleLookup.extractTitle("<title>  </title>")).isEmpty();
    }

    @Test
    void charsetFromHeader() {
        byte[] body = "<title>Käse</title>".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(PageTitleLookup.extractTitle(body, "text/html; charset=ISO-8859-1")).contains("Käse");
    }

    @Test
    void charsetFromMetaTag() {
        byte[] body = "<meta charset=\"iso-8859-1\"><title>Grüße</title>".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(PageTitleLookup.extractTitle(body, "text/html")).contains("Grüße");
    }

    @Test
    void defaultsToUtf8() {
        byte[] body = "<title>Grüße</title>".getBytes(StandardCharsets.UTF_8);
        assertThat(PageTitleLookup.extractTitle(body, "")).contains("Grüße");
    }
}

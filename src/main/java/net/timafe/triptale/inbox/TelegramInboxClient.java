package net.timafe.triptale.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.storage.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Polls the Telegram Bot API's {@code getUpdates} once per call (todo 68, MVP) — no persistent
 * background thread or webhook, matching "TripTale is not meant to run as an app" (the inbox is
 * simply stale until the user checks). Text messages from the single configured chat are written
 * as files into {@code <dataDir>/inbox/} via {@link InboxDir}; every other update (photos, other
 * chats, non-text messages, {@code timeout=0} so the call never blocks waiting for new traffic)
 * is just skipped, but still advances the offset so it's never re-fetched. No JavaFX imports
 * (package boundary rule).
 *
 * <p>Bot token and chat ID are re-read from settings on every call (not cached at construction),
 * same pattern as {@code ui.MapboxService}/the impressions settings — an Edit Settings change
 * takes effect without a restart.
 */
public class TelegramInboxClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramInboxClient.class);
    private static final URI API = URI.create("https://api.telegram.org");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** A failed poll; the message is short and human, meant for a status line, not a stack trace dump. */
    public static class TelegramException extends RuntimeException {
        public TelegramException(String message) { super(message); }
    }

    /**
     * {@code newOffset} is the next {@code getUpdates} offset to persist as-is (unchanged from
     * the one passed in if nothing new came back); {@code newItems} is how many text messages
     * from the configured chat were written to {@code inbox/}.
     */
    public record PollResult(long newOffset, int newItems) {}

    private final SettingsStore settingsStore;
    private final InboxDir inboxDir;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();

    public TelegramInboxClient(SettingsStore settingsStore, InboxDir inboxDir) {
        this.settingsStore = settingsStore;
        this.inboxDir = inboxDir;
    }

    public boolean configured() {
        AppSettings settings = settingsStore.load();
        return !settings.getTelegramBotToken().isBlank() && !settings.getTelegramChatId().isBlank();
    }

    /** Blocks (network): call off the FX thread. Throws {@link TelegramException} if unconfigured or on failure. */
    public PollResult poll(long offset) {
        AppSettings settings = settingsStore.load();
        String token = settings.getTelegramBotToken();
        String chatId = settings.getTelegramChatId();
        if (token.isBlank() || chatId.isBlank()) {
            throw new TelegramException("No Telegram bot token / chat ID configured (Edit Settings…)");
        }
        String path = "/bot" + token + "/getUpdates?timeout=0" + (offset > 0 ? "&offset=" + offset : "");
        HttpRequest request = HttpRequest.newBuilder(API.resolve(path))
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            log.debug("Telegram GET getUpdates (offset={})", offset);
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new TelegramException("Timed out after " + TIMEOUT.toSeconds() + " s");
        } catch (IOException e) {
            throw new TelegramException("Network error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelegramException("Interrupted");
        }
        if (response.statusCode() != 200) {
            throw new TelegramException("Telegram answered HTTP " + response.statusCode() + " — check the bot token");
        }
        JsonNode root;
        try {
            root = json.readTree(response.body());
        } catch (IOException e) {
            throw new TelegramException("Unreadable answer from Telegram");
        }
        if (!root.path("ok").asBoolean(false)) {
            throw new TelegramException("Telegram: " + root.path("description").asText("unknown error"));
        }

        long newOffset = offset;
        int newItems = 0;
        for (JsonNode update : root.path("result")) {
            long updateId = update.path("update_id").asLong();
            if (updateId >= newOffset) newOffset = updateId + 1;

            JsonNode message = update.path("message");
            if (message.isMissingNode()) continue; // edited_message, channel_post, ... — ignored in v1
            String text = message.path("text").asText(null);
            if (text == null || text.isBlank()) continue; // photos/locations — fast-follow, not v1
            String msgChatId = message.path("chat").path("id").asText(null);
            if (msgChatId == null || !msgChatId.equals(chatId.trim())) {
                log.debug("Ignoring inbox message from unconfigured chat {}", msgChatId);
                continue;
            }
            LocalDateTime timestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(message.path("date").asLong()), ZoneId.systemDefault());
            inboxDir.writeItem(timestamp, text);
            newItems++;
        }
        log.info("Telegram inbox poll: {} new item(s), offset {} -> {}", newItems, offset, newOffset);
        return new PollResult(newOffset, newItems);
    }
}

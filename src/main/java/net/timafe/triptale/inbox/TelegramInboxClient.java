package net.timafe.triptale.inbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.storage.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Polls the Telegram Bot API's {@code getUpdates} once per call (todo 68, MVP) — no persistent
 * background thread or webhook, matching "TripTale is not meant to run as an app" (the inbox is
 * simply stale until the user checks). Text messages from the single configured chat are written
 * as files into {@code <dataDir>/inbox/} via {@link InboxDir}, each with a same-stem {@code .json}
 * sidecar holding the raw Telegram {@code update} (todo 73 — debugging and designing future data
 * extraction, plus {@code message_id}/{@code from} read back by {@link InboxItem}, todo 75).
 * Photo messages (todo 74) go through a second
 * pair of Bot API calls — {@code getFile} to resolve a {@code file_path}, then a plain download —
 * to fetch the largest available {@code PhotoSize} (Telegram already compresses photos server-side,
 * so there's no quality lost by always taking the biggest one and no realistic risk of the 20 MB
 * {@code getFile} limit); the bytes land in a same-stem {@code .jpg} sidecar next to the caption's
 * {@code .txt} (empty if the photo had no caption). Any other non-text update (locations, a photo
 * whose download failed, …) doesn't become a pending item, but its raw payload is still captured
 * into {@code inbox/raw/} for the same debugging/design reason as todo 73. Every other update
 * (edited_message, channel_post, other chats, {@code timeout=0} so the call never blocks waiting
 * for new traffic) is just skipped, but still advances the offset so it's never re-fetched. No
 * JavaFX imports (package boundary rule).
 *
 * <p>Bot token and chat ID are re-read from settings on every call (not cached at construction),
 * same pattern as the impressions settings — an Edit Settings change takes effect without a
 * restart.
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
     * the one passed in if nothing new came back); {@code newItems} is how many text/photo
     * messages from the configured chat were written to {@code inbox/}.
     */
    public record PollResult(long newOffset, int newItems) {}

    private final URI api;
    private final SettingsStore settingsStore;
    private final InboxDir inboxDir;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();

    public TelegramInboxClient(SettingsStore settingsStore, InboxDir inboxDir) {
        this(API, settingsStore, inboxDir);
    }

    /** Package-private: lets tests point the Bot API at a local server. */
    TelegramInboxClient(URI api, SettingsStore settingsStore, InboxDir inboxDir) {
        this.api = api;
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
        HttpRequest request = HttpRequest.newBuilder(api.resolve(path))
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET()
                .build();
        log.debug("Telegram GET getUpdates (offset={})", offset);
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
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
            String msgChatId = message.path("chat").path("id").asText(null);
            if (msgChatId == null || !msgChatId.equals(chatId.trim())) {
                log.debug("Ignoring inbox message from unconfigured chat {}", msgChatId);
                continue;
            }
            LocalDateTime timestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(message.path("date").asLong()), ZoneId.systemDefault());
            String text = message.path("text").asText(null);
            if (text != null && !text.isBlank()) {
                inboxDir.writeItem(timestamp, text, prettyJson(update));
                newItems++;
                continue;
            }
            JsonNode photoSizes = message.path("photo");
            if (photoSizes.isArray() && !photoSizes.isEmpty()) {
                byte[] photo = downloadLargestPhoto(token, photoSizes, updateId);
                if (photo != null) {
                    String caption = message.path("caption").asText("");
                    inboxDir.writeItem(timestamp, caption, photo, prettyJson(update));
                    newItems++;
                    continue;
                }
                // download failed — fall through to raw capture, same as any other non-text update
            }
            // locations/other non-text content, or a photo that couldn't be downloaded — not a
            // pending item (fast-follow for anything but photos), but the raw payload is still
            // captured (todo 73) to design future fast-follows from real samples.
            inboxDir.writeRawCapture(timestamp, updateId, prettyJson(update));
        }
        log.info("Telegram inbox poll: {} new item(s), offset {} -> {}", newItems, offset, newOffset);
        return new PollResult(newOffset, newItems);
    }

    /**
     * Downloads the largest {@code PhotoSize} in a {@code message.photo} array (todo 74) — two
     * chained Bot API calls, {@code getFile} to resolve a {@code file_path} then a plain GET to
     * fetch the bytes. Telegram already compresses photos server-side, so the largest entry is
     * strictly the best quality available and comfortably under the 20 MB {@code getFile} limit.
     * Never throws — a failure here must not break the rest of the poll; returns {@code null} and
     * logs a warning instead, so the caller can fall back to a raw capture.
     */
    private byte[] downloadLargestPhoto(String token, JsonNode photoSizes, long updateId) {
        JsonNode largest = null;
        long bestArea = -1;
        for (JsonNode size : photoSizes) {
            long area = (long) size.path("width").asInt(0) * size.path("height").asInt(0);
            if (area >= bestArea) {
                bestArea = area;
                largest = size;
            }
        }
        String fileId = largest == null ? null : largest.path("file_id").asText(null);
        if (fileId == null) return null;
        try {
            String filePath = resolveFilePath(token, fileId);
            if (filePath == null) return null;
            return downloadFile(token, filePath);
        } catch (RuntimeException e) {
            log.warn("Could not download Telegram photo for update {}: {}", updateId, e.getMessage());
            return null;
        }
    }

    /** {@code getFile}: resolves a {@code file_id} into the {@code file_path} the binary download needs. */
    private String resolveFilePath(String token, String fileId) {
        String path = "/bot" + token + "/getFile?file_id="
                + URLEncoder.encode(fileId, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(api.resolve(path))
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new TelegramException("getFile answered HTTP " + response.statusCode());
        }
        JsonNode root;
        try {
            root = json.readTree(response.body());
        } catch (IOException e) {
            throw new TelegramException("Unreadable getFile answer from Telegram");
        }
        if (!root.path("ok").asBoolean(false)) {
            throw new TelegramException("Telegram getFile: " + root.path("description").asText("unknown error"));
        }
        return root.path("result").path("file_path").asText(null);
    }

    /** Downloads the raw bytes of a resolved {@code file_path} from Telegram's file API. */
    private byte[] downloadFile(String token, String filePath) {
        HttpRequest request = HttpRequest.newBuilder(api.resolve("/file/bot" + token + "/" + filePath))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new TelegramException("File download answered HTTP " + response.statusCode());
        }
        return response.body();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return http.send(request, handler);
        } catch (HttpTimeoutException e) {
            throw new TelegramException("Timed out after " + TIMEOUT.toSeconds() + " s");
        } catch (IOException e) {
            throw new TelegramException("Network error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelegramException("Interrupted");
        }
    }

    /** Pretty-prints a raw update for the {@code .json} sidecar/raw capture (todo 73); best-effort — a failure here must not break the poll. */
    private String prettyJson(JsonNode node) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            log.debug("Could not pretty-print raw inbox payload", e);
            return null;
        }
    }
}

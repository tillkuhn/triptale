package net.timafe.triptale.inbox;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.inbox.TelegramInboxClient.PollResult;
import net.timafe.triptale.inbox.TelegramInboxClient.TelegramException;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class TelegramInboxClientTest {

    private static final String TOKEN = "TESTTOKEN";
    private static final String CHAT_ID = "555";

    @TempDir
    Path tempDir;

    private SettingsStore settingsStore;
    private InboxDir inboxDir;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        settingsStore = new SettingsStore(props);
        AppSettings settings = new AppSettings();
        settings.setDataDir(tempDir.resolve("data").toString());
        settings.setTelegramBotToken(TOKEN);
        settings.setTelegramChatId(CHAT_ID);
        settingsStore.save(settings);
        inboxDir = new InboxDir(new MarkdownStore(settingsStore));
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void poll_textMessage_writesPendingItemAndAdvancesOffset() throws IOException {
        List<String> paths = new CopyOnWriteArrayList<>();
        URI base = serve(path -> {
            paths.add(path);
            return """
                    {"ok":true,"result":[{"update_id":100,"message":{
                        "date":1700000000,"chat":{"id":555},"text":"Saw a great waterfall"
                    }}]}""";
        });

        TelegramInboxClient client = new TelegramInboxClient(base, settingsStore, inboxDir);
        PollResult result = client.poll(0);

        assertEquals(101, result.newOffset());
        assertEquals(1, result.newItems());
        List<InboxItem> items = inboxDir.listPending();
        assertEquals(1, items.size());
        assertEquals("Saw a great waterfall", items.get(0).text());
        assertTrue(items.get(0).photo().isEmpty());
        assertTrue(paths.get(0).contains("/getUpdates"));
    }

    @Test
    void poll_photoMessage_downloadsLargestSizeAndKeepsCaption() throws IOException {
        byte[] photoBytes = {1, 2, 3, 4, 5};
        String updateJson = """
                {"ok":true,"result":[{"update_id":200,"message":{
                    "date":1700000000,"chat":{"id":555},"caption":"On the summit",
                    "photo":[
                        {"file_id":"small","width":90,"height":90},
                        {"file_id":"big","width":800,"height":800},
                        {"file_id":"medium","width":320,"height":320}
                    ]
                }}]}""";
        List<String> paths = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bot" + TOKEN + "/getUpdates", exchange -> respond(exchange, 200, updateJson));
        server.createContext("/bot" + TOKEN + "/getFile", exchange -> {
            paths.add(exchange.getRequestURI().toString());
            assertTrue(exchange.getRequestURI().toString().contains("file_id=big"), "should request the largest PhotoSize");
            respond(exchange, 200, """
                    {"ok":true,"result":{"file_id":"big","file_path":"photos/file_1.jpg"}}""");
        });
        server.createContext("/file/bot" + TOKEN + "/photos/file_1.jpg", exchange -> {
            paths.add(exchange.getRequestURI().toString());
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, photoBytes.length);
            exchange.getResponseBody().write(photoBytes);
            exchange.close();
        });
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        TelegramInboxClient client = new TelegramInboxClient(base, settingsStore, inboxDir);
        PollResult result = client.poll(0);

        assertEquals(1, result.newItems());
        assertEquals(201, result.newOffset());
        List<InboxItem> items = inboxDir.listPending();
        assertEquals(1, items.size());
        assertEquals("On the summit", items.get(0).text());
        assertTrue(items.get(0).photo().isPresent());
        assertEquals(2, paths.size(), "both getFile and the binary download should have been called");
    }

    @Test
    void poll_photoWithNoCaption_isPendingWithEmptyText() throws IOException {
        String updateJson = """
                {"ok":true,"result":[{"update_id":300,"message":{
                    "date":1700000000,"chat":{"id":555},
                    "photo":[{"file_id":"only","width":400,"height":400}]
                }}]}""";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bot" + TOKEN + "/getUpdates", exchange -> respond(exchange, 200, updateJson));
        server.createContext("/bot" + TOKEN + "/getFile", exchange -> respond(exchange, 200, """
                {"ok":true,"result":{"file_id":"only","file_path":"photos/only.jpg"}}"""));
        byte[] photoBytes = {9, 9};
        server.createContext("/file/bot" + TOKEN + "/photos/only.jpg", exchange -> {
            exchange.sendResponseHeaders(200, photoBytes.length);
            exchange.getResponseBody().write(photoBytes);
            exchange.close();
        });
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        TelegramInboxClient client = new TelegramInboxClient(base, settingsStore, inboxDir);
        client.poll(0);

        List<InboxItem> items = inboxDir.listPending();
        assertEquals(1, items.size());
        assertEquals("", items.get(0).text());
        assertTrue(items.get(0).photo().isPresent());
    }

    @Test
    void poll_photoDownloadFails_fallsBackToRawCaptureNotAPendingItem() throws IOException {
        String updateJson = """
                {"ok":true,"result":[{"update_id":400,"message":{
                    "date":1700000000,"chat":{"id":555},
                    "photo":[{"file_id":"broken","width":400,"height":400}]
                }}]}""";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bot" + TOKEN + "/getUpdates", exchange -> respond(exchange, 200, updateJson));
        server.createContext("/bot" + TOKEN + "/getFile", exchange -> respond(exchange, 500, "boom"));
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        TelegramInboxClient client = new TelegramInboxClient(base, settingsStore, inboxDir);
        PollResult result = client.poll(0);

        assertEquals(0, result.newItems());
        assertEquals(401, result.newOffset(), "offset still advances even though nothing became pending");
        assertTrue(inboxDir.listPending().isEmpty());
        assertTrue(Files.isDirectory(tempDir.resolve("data/inbox/raw")), "raw capture should exist");
    }

    @Test
    void poll_messageFromOtherChat_isIgnored() throws IOException {
        URI base = serve(path -> """
                {"ok":true,"result":[{"update_id":500,"message":{
                    "date":1700000000,"chat":{"id":999},"text":"not for us"
                }}]}""");

        TelegramInboxClient client = new TelegramInboxClient(base, settingsStore, inboxDir);
        PollResult result = client.poll(0);

        assertEquals(0, result.newItems());
        assertEquals(501, result.newOffset());
        assertTrue(inboxDir.listPending().isEmpty());
    }

    @Test
    void poll_unconfigured_throws() {
        AppSettings settings = settingsStore.load();
        settings.setTelegramBotToken("");
        settingsStore.save(settings);
        TelegramInboxClient client = new TelegramInboxClient(settingsStore, inboxDir);

        assertThrows(TelegramException.class, () -> client.poll(0));
    }

    private interface Responder { String respond(String path); }

    private URI serve(Responder responder) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = responder.respond(exchange.getRequestURI().toString());
            respond(exchange, 200, body);
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

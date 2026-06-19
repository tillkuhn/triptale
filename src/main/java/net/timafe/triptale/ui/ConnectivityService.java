package net.timafe.triptale.ui;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Checks whether the host of the configured git remote (or github.com as fallback)
 * is reachable via a TCP connection on port 443, with a 3-second timeout.
 * The check runs on a background thread via a JavaFX {@link Task} so the UI
 * is never blocked.
 */
@Component
public class ConnectivityService {

    private static final Logger log = LoggerFactory.getLogger(ConnectivityService.class);
    private static final String FALLBACK_HOST = "github.com";
    private static final int PORT = 443;
    private static final int TIMEOUT_MS = 3_000;

    /**
     * Creates a new {@link Task} that performs a single TCP connectivity check.
     * Callers must start a {@link Thread} around it and set success/failure
     * callbacks via {@code setOnSucceeded} / {@code setOnFailed} before starting.
     *
     * @param remoteUrl the configured git remote URL (may be blank)
     * @return a Task resolving to {@code true} if the host is reachable, {@code false} otherwise
     */
    public Task<Boolean> checkTask(String remoteUrl) {
        String host = resolveHost(remoteUrl);
        return new Task<>() {
            @Override
            protected Boolean call() {
                log.debug("Checking connectivity to {}:{}", host, PORT);
                try {
                    InetAddress address = InetAddress.getByName(host);
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(address, PORT), TIMEOUT_MS);
                    }
                    log.debug("Connectivity OK: {}:{}", host, PORT);
                    return true;
                } catch (Exception e) {
                    log.debug("Connectivity check failed for {}:{} — {}", host, PORT, e.getMessage());
                    return false;
                }
            }
        };
    }

    /**
     * Parses the hostname from {@code remoteUrl}. Falls back to {@value #FALLBACK_HOST}
     * if the URL is blank or cannot be parsed.
     */
    static String resolveHost(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return FALLBACK_HOST;
        }
        try {
            // Handle SCP-style git URLs like git@github.com:user/repo.git
            if (!remoteUrl.contains("://") && remoteUrl.contains("@") && remoteUrl.contains(":")) {
                String afterAt = remoteUrl.substring(remoteUrl.indexOf('@') + 1);
                String host = afterAt.substring(0, afterAt.indexOf(':'));
                return host.isBlank() ? FALLBACK_HOST : host;
            }
            URI uri = new URI(remoteUrl);
            String host = uri.getHost();
            return (host == null || host.isBlank()) ? FALLBACK_HOST : host;
        } catch (Exception e) {
            log.debug("Could not parse remote URL '{}', falling back to {}", remoteUrl, FALLBACK_HOST);
            return FALLBACK_HOST;
        }
    }
}

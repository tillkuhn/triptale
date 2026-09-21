package net.timafe.triptale.ui;

import javafx.application.HostServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens URLs in the system browser via JavaFX {@link HostServices}.
 * <p>
 * {@code HostServices} is only obtainable from the {@code Application} instance, so it is
 * registered as a Spring singleton by hand during {@code TripTaleApplication.init()} and may be
 * absent in contexts that never went through the JavaFX launcher (e.g. tests). A null instance
 * degrades to a warning rather than an exception.
 */
public final class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final HostServices hostServices;

    public BrowserLauncher(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public void open(String url) {
        try {
            if (hostServices != null) {
                hostServices.showDocument(url);
            } else {
                log.warn("HostServices not available; cannot open {}", url);
            }
        } catch (Exception ex) {
            log.warn("Could not open browser for {}: {}", url, ex.getMessage());
        }
    }
}

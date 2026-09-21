package net.timafe.triptale.ui.dialog;

import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Dialogs;
import org.springframework.boot.info.BuildProperties;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Version, runtime and license popup. */
public final class AboutDialog {

    private static final String REPO_URL = "https://github.com/tillkuhn/triptale";
    private static final DateTimeFormatter BUILT_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String appName;
    private final BuildProperties buildProperties;
    private final BrowserLauncher browser;

    /**
     * @param buildProperties may be null — it only exists once the {@code build-info} goal has
     *                        run, i.e. after {@code mvn package}, not after a bare {@code compile}.
     */
    public AboutDialog(String appName, BuildProperties buildProperties, BrowserLauncher browser) {
        this.appName = appName;
        this.buildProperties = buildProperties;
        this.browser = browser;
    }

    public void show() {
        GridPane grid = Dialogs.infoGrid();
        int row = 0;

        Label desc = new Label("Offline-first cycling and hiking trip diary with git sync");
        desc.setWrapText(true);
        desc.setMaxWidth(360);
        grid.add(desc, 0, row++, 2, 1);

        grid.add(new Label("Version:"), 0, row);
        grid.add(new Label(buildProperties != null ? buildProperties.getVersion() : "dev"), 1, row++);
        grid.add(new Label("Built:"), 0, row);
        grid.add(new Label(builtAt()), 1, row++);
        grid.add(new Label("Runtime:"), 0, row);
        grid.add(new Label("Java " + System.getProperty("java.version", "?")
                + "  ·  JavaFX " + javafxVersion()), 1, row++);
        grid.add(new Label("Memory:"), 0, row);
        grid.add(new Label(usedMemory()), 1, row++);
        grid.add(new Label("License:"), 0, row);
        grid.add(new Label("Apache 2.0"), 1, row++);
        grid.add(new Label("Source:"), 0, row);
        Hyperlink link = new Hyperlink("github.com/tillkuhn/triptale");
        link.setOnAction(e -> browser.open(REPO_URL));
        grid.add(link, 1, row);

        Dialogs.showInfo("About " + appName, appName, grid);
    }

    private String builtAt() {
        if (buildProperties == null || buildProperties.getTime() == null) return "—";
        return BUILT_AT.withZone(ZoneId.systemDefault()).format(buildProperties.getTime());
    }

    private static String javafxVersion() {
        return System.getProperty("javafx.runtime.version", System.getProperty("javafx.version", "?"));
    }

    private static String usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        return String.format(Locale.ROOT, "%.0f MB", usedBytes / (1024.0 * 1024.0));
    }
}

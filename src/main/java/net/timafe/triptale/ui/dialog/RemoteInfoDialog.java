package net.timafe.triptale.ui.dialog;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.BucketUrl;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.LocalDir;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.sync.BucketSyncer;
import net.timafe.triptale.sync.ObjectStore;
import net.timafe.triptale.sync.S3Client;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.ByteSizes;
import net.timafe.triptale.util.DirSizes;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Read-only summary of the git (and, with cloud sync, S3 attachment) configuration actually in
 * effect, plus git object count and the approximate size of the synced folders and the local
 * {@code .triptale/} cache (todo 26).
 */
public final class RemoteInfoDialog {

    private static final List<String> FOLDERS = List.of("attachments", "radio", LocalDir.NAME);

    private final GitService gitService;
    private final SettingsStore settingsStore;

    public RemoteInfoDialog(GitService gitService, SettingsStore settingsStore) {
        this.gitService = gitService;
        this.settingsStore = settingsStore;
    }

    public void show() {
        AppSettings settings = settingsStore.load();
        GridPane grid = Dialogs.infoGrid();
        int row = 0;
        grid.add(new Label("Data dir:"), 0, row);
        grid.add(new Label(settings.resolvedDataDir().map(Path::toString).orElse("(not configured)")), 1, row++);
        grid.add(new Label("Origin URL:"), 0, row);
        grid.add(new Label(remoteDisplay()), 1, row++);
        grid.add(new Label("Author:"), 0, row);
        grid.add(new Label(authorDisplay(settings)), 1, row++);

        AppSettings.Attachments attachments = settings.getAttachments();
        boolean cloud = attachments.getSync() == AppSettings.AttachmentSync.CLOUD;
        if (cloud) {
            // credentials are deliberately never shown
            grid.add(new Label("Backpack:"), 0, row);
            grid.add(new Label(orNone(attachments.getBucketUrl())), 1, row++);
            grid.add(new Label("Region:"), 0, row);
            grid.add(new Label(orNone(attachments.getRegion())), 1, row++);
        }

        Optional<Path> dataDir = settings.resolvedDataDir();
        boolean ready = dataDir.isPresent() && gitService.isConfigured();
        grid.add(new Label("Git objects:"), 0, row);
        grid.add(new Label(ready ? objectsDisplay() : "-"), 1, row++);
        for (String folder : FOLDERS) {
            grid.add(new Label(folder + "/:"), 0, row);
            grid.add(new Label(ready ? sizeDisplay(dataDir.get(), folder, attachments, cloud) : "-"), 1, row++);
        }

        Dialogs.showInfo("🗄 Repository Info",
                cloud ? "Git and backpack cloud configuration" : "Git configuration", grid);
    }

    private String objectsDisplay() {
        try {
            return gitService.objectSummary();
        } catch (RuntimeException e) {
            return "(error: " + UiText.describe(e) + ")";
        }
    }

    /** Local size, plus the bucket total (one LIST) for the synced folders when cloud sync is on. */
    private static String sizeDisplay(Path dataDir, String folder, AppSettings.Attachments attachments, boolean cloud) {
        String local = ByteSizes.humanize(DirSizes.totalBytes(dataDir.resolve(folder))) + " local";
        if (!cloud || folder.equals(LocalDir.NAME)) return local;
        try {
            String prefix = BucketSyncer.keyPrefix(BucketUrl.parse(attachments.getBucketUrl()), folder);
            long bytes = S3Client.from(attachments).list(prefix).values().stream()
                    .mapToLong(ObjectStore.ObjectInfo::size).sum();
            return local + ", " + ByteSizes.humanize(bytes) + " in bucket";
        } catch (RuntimeException e) {
            return local + ", bucket: " + UiText.describe(e);
        }
    }

    private String remoteDisplay() {
        try {
            String remote = gitService.remoteUrl();
            return remote.isBlank() ? "(none)" : remote;
        } catch (RuntimeException e) {
            return "(error: " + e.getMessage() + ")";
        }
    }

    private static String orNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    /** Blank author settings mean JGit falls back to whatever the system git config says. */
    private static String authorDisplay(AppSettings settings) {
        String name = settings.getGit().getAuthorName();
        String email = settings.getGit().getAuthorEmail();
        return (name.isBlank() && email.isBlank())
                ? "(system git config)"
                : name + " <" + email + ">";
    }
}

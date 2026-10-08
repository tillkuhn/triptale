package net.timafe.triptale.ui.dialog;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.Dialogs;

import java.nio.file.Path;

/** Read-only summary of the git (and, with cloud sync, S3 attachment) configuration actually in effect. */
public final class RemoteInfoDialog {

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
            // the secret access key is deliberately never shown
            grid.add(new Label("Attachments:"), 0, row);
            grid.add(new Label(orNone(attachments.getBucketUrl())), 1, row++);
            grid.add(new Label("Region:"), 0, row);
            grid.add(new Label(orNone(attachments.getRegion())), 1, row++);
            grid.add(new Label("Access key ID:"), 0, row);
            grid.add(new Label(orNone(attachments.getAccessKeyId())), 1, row);
        }

        Dialogs.showInfo("Remote Info",
                cloud ? "Git and attachment cloud configuration" : "Git configuration", grid);
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

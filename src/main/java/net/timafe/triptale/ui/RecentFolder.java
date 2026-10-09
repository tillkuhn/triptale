package net.timafe.triptale.ui;

import java.io.File;

/**
 * The folder files were last picked from this session — shared by Add Attachments and the
 * impressions viewer's Pick Folder source, so both choosers start where the user left off.
 */
public final class RecentFolder {

    private File folder;

    /** The remembered folder if it still exists, else {@code ~/Pictures}, else the home directory. */
    public File initial() {
        if (folder != null && folder.isDirectory()) return folder;
        File home = new File(System.getProperty("user.home", "."));
        File pictures = new File(home, "Pictures");
        return pictures.isDirectory() ? pictures : home;
    }

    public void remember(File folder) {
        if (folder != null) this.folder = folder;
    }
}

package net.timafe.triptale.ui;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** Thin wrapper over the system clipboard — the three-line put-string dance, once. */
public final class Clipboards {

    private Clipboards() {
    }

    public static void putString(String text) {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(cc);
    }
}

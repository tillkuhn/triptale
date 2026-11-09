package net.timafe.triptale.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import net.timafe.triptale.weblink.PageTitleLookup;

/**
 * The 🔍 button next to a weblink's URL field (todo 91): fetches the page off the FX thread and
 * fills the name field. Progress and failures live on the button, like the Coordinator's 🔍:
 * ⏳ while running, ⚠️ with the error as tooltip, back to 🔍 on success. An existing, different
 * name is only replaced after a Replace/Keep question.
 */
public final class TitleLookupButton extends Button {

    private static final String IDLE = "🔍";
    private static final String BUSY = "⏳";
    private static final String FAILED = "⚠️";
    private static final String IDLE_TIP = "Fetch the page and use its title as the name";

    private static final PageTitleLookup LOOKUP = new PageTitleLookup();

    private final TextField urlField;
    private final TextField nameField;
    private boolean busy;

    public TitleLookupButton(TextField urlField, TextField nameField) {
        super(IDLE);
        this.urlField = urlField;
        this.nameField = nameField;
        setTooltip(new Tooltip(IDLE_TIP));
        urlField.textProperty().addListener((o, a, b) -> refresh());
        setOnAction(e -> start());
        refresh();
    }

    private void refresh() {
        setDisable(busy || !UiText.isValidHttpUrl(urlField.getText().trim()));
    }

    private void outcome(String text, String tip) {
        setText(text);
        getTooltip().setText(tip == null ? IDLE_TIP : tip);
    }

    private void start() {
        String url = urlField.getText().trim();
        busy = true;
        outcome(BUSY, "Fetching…");
        refresh();
        Thread.ofVirtual().name("weblink-title").start(() -> {
            String title = null;
            String error = null;
            try {
                title = LOOKUP.lookup(url);
            } catch (RuntimeException e) {
                error = e instanceof PageTitleLookup.LookupException ? e.getMessage() : UiText.describe(e);
            }
            String found = title;
            String message = error;
            Platform.runLater(() -> {
                busy = false;
                if (!url.equals(urlField.getText().trim())) {
                    outcome(IDLE, null);
                } else if (found != null) {
                    outcome(IDLE, null);
                    apply(found);
                } else {
                    outcome(FAILED, message);
                }
                refresh();
            });
        });
    }

    private void apply(String title) {
        String current = nameField.getText().trim();
        if (current.equals(title)) return;
        if (current.isEmpty() || confirmReplace(current, title)) nameField.setText(title);
    }

    private boolean confirmReplace(String current, String title) {
        ButtonType replace = new ButtonType("Replace", ButtonBar.ButtonData.OK_DONE);
        ButtonType keep = new ButtonType("Keep", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Replace '" + current + "' with '" + title + "'?",
                replace, keep);
        alert.setHeaderText(null);
        alert.initOwner(getScene() == null ? null : getScene().getWindow());
        Dialogs.applyStylesheet(alert.getDialogPane());
        return alert.showAndWait().filter(b -> b == replace).isPresent();
    }
}

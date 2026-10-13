package net.timafe.triptale.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared dialog chrome. Every {@link Alert} and {@link javafx.scene.control.Dialog} in the app
 * must run through {@link #applyStylesheet} to inherit the dark theme — going through these
 * factories instead of {@code new Alert(...)} makes that impossible to forget.
 */
public final class Dialogs {

    private Dialogs() {
    }

    /** Attaches the app stylesheet so a dialog doesn't render in the light platform default. */
    public static void applyStylesheet(DialogPane pane) {
        pane.getStylesheets().add(
                Dialogs.class.getResource("/fxml/triptale.css").toExternalForm());
    }

    /** Label of the one save action for a day's entry — main window and its frontmatter dialogs alike. */
    public static final String SAVE_THE_DAY = "📅 Save the Day";

    private static final KeyCombination SAVE_SHORTCUT =
            new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);

    /**
     * Adds Cancel and the confirm button of a dialog that edits the day's frontmatter. When the
     * day is savable the button reads {@link #SAVE_THE_DAY} and the caller saves the whole entry
     * on confirm; otherwise it reads "Apply" and the edits only land in the (unsaved) main form.
     * Cmd/Ctrl+S fires it too, as on the main window. Returns the button's type.
     */
    public static ButtonType addSaveDayButtons(DialogPane pane, boolean canSaveDay) {
        ButtonType type = new ButtonType(canSaveDay ? SAVE_THE_DAY : "Apply", ButtonBar.ButtonData.OK_DONE);
        // ButtonType.CANCEL also re-enables the dialog window's native close (X) button —
        // JavaFX disables it unless a CANCEL_CLOSE-data button is present.
        pane.getButtonTypes().addAll(ButtonType.CANCEL, type);
        Button button = (Button) pane.lookupButton(type);
        button.setTooltip(new Tooltip(canSaveDay
                ? "Save the whole day, including unsaved edits in the main window (⌘S)"
                : "Apply to the day — it needs a title and tale text before it can be saved (⌘S)"));
        pane.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (!SAVE_SHORTCUT.match(ev)) return;
            ev.consume();
            if (!button.isDisabled()) button.fire();
        });
        return type;
    }

    /** The standard two-column label/field grid used by the editing dialogs. */
    public static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(14));
        grid.getStyleClass().add("card");
        return grid;
    }

    private static final DateTimeFormatter DATE_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);

    /** Renders a {@link javafx.scene.control.DatePicker} as {@code yyyy-MM-dd} instead of the locale default. */
    public static StringConverter<LocalDate> isoDateConverter() {
        return new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : DATE_ISO.format(d); }
            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try { return LocalDate.parse(s.trim(), DATE_ISO); } catch (Exception e) { return null; }
            }
        };
    }

    /** A denser grid for read-only key/value popups (Remote Info, About). */
    public static GridPane infoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));
        return grid;
    }

    /**
     * Shared layout for static info popups (Remote Info, About, ...): title bar text,
     * a bold heading with the platform "i" icon, and arbitrary content below.
     */
    public static void showInfo(String title, String headerText, Node content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.getDialogPane().setContent(content);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        applyStylesheet(alert.getDialogPane());
        alert.showAndWait();
    }
}

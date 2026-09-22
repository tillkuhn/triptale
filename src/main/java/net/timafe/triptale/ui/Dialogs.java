package net.timafe.triptale.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
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

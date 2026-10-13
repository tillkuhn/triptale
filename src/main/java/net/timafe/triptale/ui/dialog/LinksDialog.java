package net.timafe.triptale.ui.dialog;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import net.timafe.triptale.domain.Link;
import net.timafe.triptale.domain.LinkKind;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Dialogs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

/**
 * Edits an entry's links (todo 09): one row per existing link with the verb fixed and URL/name
 * editable, plus a row to add a link for a verb not used yet. Only http(s) URLs can be entered;
 * an invalid value that came from the file is kept as-is and marked ⚠ unless the user edits it.
 */
public final class LinksDialog {

    private static final int URL_COLUMNS = 28;
    private static final int NAME_COLUMNS = 14;

    /** One existing link: its verb, editable fields, and the value loaded from the entry. */
    private record Row(String verb, TextField url, TextField name, Link original) {

        static Row of(String verb, Link link) {
            Row row = new Row(verb, field(link.url(), URL_COLUMNS),
                    field(link.name() == null ? "" : link.name(), NAME_COLUMNS), link);
            row.url.setPromptText("https://…");
            row.name.setPromptText("Name (optional)");
            return row;
        }

        Link value() {
            return Link.of(url.getText(), name.getText());
        }

        /** Invalid but untouched values from the file are kept (lenient); edited ones must be valid. */
        String problem() {
            if (url.getText().isBlank()) return LinkKind.labelFor(verb) + " needs a URL (🗑 removes it).";
            Link v = value();
            if (!v.valid() && !v.equals(Link.of(original.url(), original.name()))) {
                return LinkKind.labelFor(verb) + " needs an http(s) URL.";
            }
            return null;
        }
    }

    private final BrowserLauncher browser;

    public LinksDialog(BrowserLauncher browser) {
        this.browser = browser;
    }

    /**
     * The edited links keyed by verb in {@link LinkKind#ORDER}, or empty when the user cancelled.
     * {@code canSaveDay} only picks the confirm button's label (see {@link Dialogs#addSaveDayButtons}).
     */
    public Optional<SequencedMap<String, Link>> showAndWait(SequencedMap<String, Link> links, boolean canSaveDay) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("🕸 Weblinks Manager");
        dlg.setHeaderText("Weblinks for this day");

        List<Row> rows = new ArrayList<>();
        links.forEach((verb, link) -> rows.add(Row.of(verb, link)));

        ComboBox<LinkKind> newKind = new ComboBox<>();
        newKind.setButtonCell(new KindCell());
        newKind.setCellFactory(lv -> new KindCell());
        TextField newUrl = field("", URL_COLUMNS);
        newUrl.setPromptText("https://…");
        TextField newName = field("", NAME_COLUMNS);
        newName.setPromptText("Name (optional)");
        Button add = new Button("＋");
        add.setTooltip(new Tooltip("Add link"));
        Label hintLabel = new Label();
        hintLabel.getStyleClass().add("hint-label");

        GridPane grid = Dialogs.formGrid();
        dlg.getDialogPane().setContent(grid);
        ButtonType okType = Dialogs.addSaveDayButtons(dlg.getDialogPane(), canSaveDay);
        Dialogs.applyStylesheet(dlg.getDialogPane());
        Node okButton = dlg.getDialogPane().lookupButton(okType);

        Runnable[] rebuild = new Runnable[1];
        Runnable refresh = () -> {
            String problem = rows.stream().map(Row::problem).filter(p -> p != null).findFirst().orElse(null);
            boolean newFilled = !newUrl.getText().isBlank() || !newName.getText().isBlank();
            boolean newValid = newKind.getValue() != null && Link.isHttpUrl(newUrl.getText());
            if (problem == null && newFilled && !newValid) {
                problem = newKind.getValue() == null ? "Pick a kind for the new link." : "The new link needs an http(s) URL.";
            }
            hintLabel.setText(problem == null ? "" : problem);
            okButton.setDisable(problem != null);
            add.setDisable(!newValid);
        };
        rebuild[0] = () -> {
            grid.getChildren().clear();
            int r = 0;
            for (Row row : rows) {
                Link current = row.value();
                Label label = new Label(LinkKind.emojiFor(row.verb()) + " " + LinkKind.labelFor(row.verb())
                        + (current.valid() ? "" : " ⚠"));
                if (!current.valid()) label.setTooltip(new Tooltip("Not an http(s) URL — kept as-is"));
                Button open = new Button("🔗");
                open.setTooltip(new Tooltip("Open in browser"));
                open.setDisable(!current.valid());
                open.setOnAction(e -> browser.open(row.value().url()));
                Button delete = new Button("🗑");
                delete.setTooltip(new Tooltip("Remove link"));
                delete.setOnAction(e -> {
                    rows.remove(row);
                    rebuild[0].run();
                });
                grid.addRow(r++, label, row.url(), row.name(), open, delete);
            }
            List<LinkKind> unused = Arrays.stream(LinkKind.values())
                    .filter(k -> rows.stream().noneMatch(row -> row.verb().equals(k.key())))
                    .toList();
            newKind.getItems().setAll(unused);
            if (!unused.isEmpty()) {
                if (!unused.contains(newKind.getValue())) newKind.setValue(unused.getFirst());
                grid.addRow(r++, newKind, newUrl, newName, add);
            } else {
                newKind.setValue(null);
                grid.add(new Label("All kinds of links are in use."), 0, r++, 5, 1);
            }
            grid.add(hintLabel, 0, r, 5, 1);
            refresh.run();
            if (dlg.getDialogPane().getScene() != null) dlg.getDialogPane().getScene().getWindow().sizeToScene();
        };
        Runnable addNew = () -> {
            Row row = Row.of(newKind.getValue().key(), Link.of(newUrl.getText(), newName.getText()));
            row.url().textProperty().addListener((o, a, b) -> refresh.run());
            rows.add(row);
            rows.sort((a, b) -> LinkKind.ORDER.compare(a.verb(), b.verb()));
            newUrl.clear();
            newName.clear();
            rebuild[0].run();
        };
        add.setOnAction(e -> addNew.run());
        newKind.valueProperty().addListener((o, a, b) -> refresh.run());
        newUrl.textProperty().addListener((o, a, b) -> refresh.run());
        newName.textProperty().addListener((o, a, b) -> refresh.run());
        for (Row row : rows) {
            row.url().textProperty().addListener((o, a, b) -> refresh.run());
        }
        rebuild[0].run();

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != okType) return Optional.empty();
        // A filled-in, valid new row counts even without pressing ＋ (invalid ones block OK)
        if (newKind.getValue() != null && Link.isHttpUrl(newUrl.getText())) addNew.run();
        SequencedMap<String, Link> out = new LinkedHashMap<>();
        for (Row row : rows) out.put(row.verb(), row.value());
        return Optional.of(out);
    }

    private static TextField field(String text, int columns) {
        TextField f = new TextField(text);
        f.setPrefColumnCount(columns);
        return f;
    }

    private static final class KindCell extends ListCell<LinkKind> {
        @Override
        protected void updateItem(LinkKind kind, boolean empty) {
            super.updateItem(kind, empty);
            setText(empty || kind == null ? null : kind.emoji() + " " + kind.label());
        }
    }
}

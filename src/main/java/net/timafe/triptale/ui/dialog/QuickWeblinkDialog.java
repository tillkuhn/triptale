package net.timafe.triptale.ui.dialog;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import net.timafe.triptale.domain.Link;
import net.timafe.triptale.domain.LinkKind;
import net.timafe.triptale.ui.Dialogs;

import java.util.List;
import java.util.Optional;

/**
 * Single-link follow-up to an Octobot inbox Add, opened when the user ticked "Create Weblink"
 * (todo 77). Deliberately simpler than {@link LinksDialog} — it only ever creates the one new
 * link, so it doesn't list the day's existing links, just lets the user pick a verb among the
 * ones not already used that day and adjust the pre-filled URL/name before confirming. Follows
 * the usual dialog contract: a plain object, no controller-state mutation, {@code Optional}
 * result on Cancel.
 */
public final class QuickWeblinkDialog {

    private static final int URL_COLUMNS = 28;
    private static final int NAME_COLUMNS = 14;

    private QuickWeblinkDialog() {
    }

    /** The chosen verb key (e.g. {@code "eat"}) and the link to store under {@code url_<verb>}. */
    public record Result(String verb, Link link) {
    }

    /**
     * {@code untaken} are the only pickable verbs (every {@link LinkKind} already used that day
     * is excluded by the caller); {@code guessedVerb} is preselected when it's one of them, else
     * the first of {@code untaken}. {@code url}/{@code name} pre-fill the editable fields.
     */
    public static Optional<Result> showAndWait(List<LinkKind> untaken, LinkKind guessedVerb,
                                                String url, String name) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("🕸 Create Weblink");
        dlg.setHeaderText("Add a weblink for this day");

        ComboBox<LinkKind> verb = new ComboBox<>();
        verb.setButtonCell(new KindCell());
        verb.setCellFactory(lv -> new KindCell());
        verb.getItems().setAll(untaken);
        verb.setValue(guessedVerb != null && untaken.contains(guessedVerb) ? guessedVerb : untaken.getFirst());

        TextField urlField = new TextField(url == null ? "" : url);
        urlField.setPromptText("https://…");
        urlField.setPrefColumnCount(URL_COLUMNS);
        TextField nameField = new TextField(name == null ? "" : name);
        nameField.setPromptText("Name (optional)");
        nameField.setPrefColumnCount(NAME_COLUMNS);

        Label hint = new Label();
        hint.getStyleClass().add("hint-label");

        GridPane grid = Dialogs.formGrid();
        grid.addRow(0, new Label("Kind"), verb);
        grid.addRow(1, new Label("URL"), urlField);
        grid.addRow(2, new Label("Name"), nameField);
        grid.add(hint, 0, 3, 2, 1);
        dlg.getDialogPane().setContent(grid);

        ButtonType okType = new ButtonType("Add Weblink", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, okType);
        Dialogs.applyStylesheet(dlg.getDialogPane());
        Node okButton = dlg.getDialogPane().lookupButton(okType);

        Runnable refresh = () -> {
            boolean valid = verb.getValue() != null && Link.isHttpUrl(urlField.getText());
            okButton.setDisable(!valid);
            hint.setText(valid ? "" : "Pick a kind and enter an http(s) URL.");
        };
        verb.valueProperty().addListener((o, a, b) -> refresh.run());
        urlField.textProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != okType) return Optional.empty();
        return Optional.of(new Result(verb.getValue().key(), Link.of(urlField.getText(), nameField.getText())));
    }

    private static final class KindCell extends ListCell<LinkKind> {
        @Override
        protected void updateItem(LinkKind kind, boolean empty) {
            super.updateItem(kind, empty);
            setText(empty || kind == null ? null : kind.emoji() + " " + kind.label());
        }
    }
}

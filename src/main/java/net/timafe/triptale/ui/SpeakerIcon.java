package net.timafe.triptale.ui;

import javafx.scene.Group;
import javafx.scene.shape.SVGPath;

/**
 * Loudspeaker for the idle radio button (todo 46). Drawn rather than the 🔊 emoji, which
 * renders as a grey colour glyph that ignores {@code -fx-text-fill} on the dark toolbar.
 */
class SpeakerIcon extends Group {

    SpeakerIcon() {
        SVGPath cone = new SVGPath();
        cone.setContent("M1 5.5h3l4-3.5v12l-4-3.5H1z");
        cone.getStyleClass().add("speaker-cone");
        SVGPath waves = new SVGPath();
        waves.setContent("M10.5 5.5a3.5 3.5 0 0 1 0 5M12.5 3a7 7 0 0 1 0 10");
        waves.getStyleClass().add("speaker-waves");
        getChildren().addAll(cone, waves);
    }
}

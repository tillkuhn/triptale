package net.timafe.triptale.map;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Reads and writes PNG {@code tEXt} chunks (keyword/value pairs) with plain {@code javax.imageio}:
 * Sgt. Mapper stores the settings a map was made with inside the PNG itself (todo 82, D1), so
 * there's no sidecar file to keep in step.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public final class PngText {

    private static final String PNG_FORMAT = "javax_imageio_png_1.0";

    private PngText() {
    }

    /** Writes {@code image} as a PNG to {@code target}, with one {@code tEXt} chunk per entry of {@code text}. */
    public static void write(BufferedImage image, Path target, Map<String, String> text) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) throw new IOException("No PNG writer available");
        ImageWriter writer = writers.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(Files.newOutputStream(target))) {
            writer.setOutput(out);
            IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
            if (!text.isEmpty()) {
                IIOMetadataNode textNode = new IIOMetadataNode("tEXt");
                text.forEach((keyword, value) -> {
                    IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
                    entry.setAttribute("keyword", keyword);
                    entry.setAttribute("value", value);
                    textNode.appendChild(entry);
                });
                IIOMetadataNode root = new IIOMetadataNode(PNG_FORMAT);
                root.appendChild(textNode);
                metadata.mergeTree(PNG_FORMAT, root);
            }
            writer.write(new IIOImage(image, null, metadata));
        } finally {
            writer.dispose();
        }
    }

    /** The {@code tEXt} chunks of the PNG at {@code file}, in file order; empty if it has none or isn't a PNG. */
    public static Map<String, String> read(Path file) throws IOException {
        Map<String, String> text = new LinkedHashMap<>();
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) return text;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return text;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, false);
                IIOMetadata metadata = reader.getImageMetadata(0);
                if (metadata == null || !PNG_FORMAT.equals(metadata.getNativeMetadataFormatName())) return text;
                collect(metadata.getAsTree(PNG_FORMAT), text);
            } finally {
                reader.dispose();
            }
        }
        return text;
    }

    private static void collect(Node node, Map<String, String> text) {
        if ("tEXtEntry".equals(node.getNodeName())) {
            NamedNodeMap attrs = node.getAttributes();
            Node keyword = attrs.getNamedItem("keyword");
            Node value = attrs.getNamedItem("value");
            if (keyword != null && value != null) text.put(keyword.getNodeValue(), value.getNodeValue());
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) collect(child, text);
    }
}

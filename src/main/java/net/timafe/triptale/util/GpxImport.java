package net.timafe.triptale.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Reads the first track's name and the first track point's coordinates/date out of a GPX file,
 * for prefilling {@code NewTripDialog}. Only {@code trk/trkseg/trkpt} is read (no {@code rte}).
 */
public final class GpxImport {

    public record Parsed(String name, LocalDate date, double lat, double lon) {}

    private GpxImport() {}

    /** Empty if the file isn't well-formed GPX, or lacks a track name or a first track point. */
    public static Optional<Parsed> parse(File file) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(file);
            Element trk = firstChildElement(doc.getDocumentElement(), "trk");
            if (trk == null) return Optional.empty();
            Element name = firstChildElement(trk, "name");
            if (name == null || name.getTextContent().isBlank()) return Optional.empty();

            Element trkseg = firstChildElement(trk, "trkseg");
            Element trkpt = trkseg == null ? null : firstChildElement(trkseg, "trkpt");
            if (trkpt == null) return Optional.empty();

            Element timeEl = firstChildElement(trkpt, "time");
            if (timeEl == null) return Optional.empty();
            double lat = Double.parseDouble(trkpt.getAttribute("lat"));
            double lon = Double.parseDouble(trkpt.getAttribute("lon"));
            if (!Coordinates.isValid(lat, lon)) return Optional.empty();
            LocalDate date = Instant.parse(timeEl.getTextContent().trim())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();

            return Optional.of(new Parsed(name.getTextContent().trim(), date, lat, lon));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(tagName)) {
                return (Element) child;
            }
        }
        return null;
    }
}

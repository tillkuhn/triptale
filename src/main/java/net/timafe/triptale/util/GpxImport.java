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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads a track's name, first/last track point coordinates, first point's date, and whole-track
 * distance/altitude-gain totals out of a GPX file, for prefilling {@code NewTripDialog} and
 * the Tale Entry import. Only {@code trk/trkseg/trkpt} is read (no {@code rte}).
 */
public final class GpxImport {

    /** Below this, an elevation change between trkpts is treated as GPS noise, not real climb. */
    private static final double ELEVATION_NOISE_THRESHOLD_M = 1.0;
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    public record Parsed(String name, LocalDate date, double lat, double lon,
                          double stopLat, double stopLon,
                          double distanceKm, Double altitudeGainM) {}

    private record TrkPt(double lat, double lon, Double ele) {}

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

            List<TrkPt> points = readTrackPoints(trk);
            if (points.isEmpty()) return Optional.empty();
            TrkPt first = points.get(0);
            TrkPt last = points.get(points.size() - 1);

            Element firstTrkseg = firstChildElement(trk, "trkseg");
            Element firstTrkpt = firstTrkseg == null ? null : firstChildElement(firstTrkseg, "trkpt");
            Element timeEl = firstTrkpt == null ? null : firstChildElement(firstTrkpt, "time");
            if (timeEl == null) return Optional.empty();
            if (!Coordinates.isValid(first.lat(), first.lon())) return Optional.empty();
            LocalDate date = Instant.parse(timeEl.getTextContent().trim())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();

            double distanceKm = round2(totalDistanceKm(points));
            Double altitudeGainM = totalAltitudeGainM(points);
            if (altitudeGainM != null) altitudeGainM = round2(altitudeGainM);

            return Optional.of(new Parsed(name.getTextContent().trim(), date,
                    first.lat(), first.lon(), last.lat(), last.lon(), distanceKm, altitudeGainM));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static List<TrkPt> readTrackPoints(Element trk) {
        List<TrkPt> points = new ArrayList<>();
        NodeList trksegs = trk.getChildNodes();
        for (int i = 0; i < trksegs.getLength(); i++) {
            Node trksegNode = trksegs.item(i);
            if (trksegNode.getNodeType() != Node.ELEMENT_NODE || !trksegNode.getNodeName().equals("trkseg")) {
                continue;
            }
            NodeList trkpts = ((Element) trksegNode).getChildNodes();
            for (int j = 0; j < trkpts.getLength(); j++) {
                Node trkptNode = trkpts.item(j);
                if (trkptNode.getNodeType() != Node.ELEMENT_NODE || !trkptNode.getNodeName().equals("trkpt")) {
                    continue;
                }
                Element trkpt = (Element) trkptNode;
                double lat = Double.parseDouble(trkpt.getAttribute("lat"));
                double lon = Double.parseDouble(trkpt.getAttribute("lon"));
                Element eleEl = firstChildElement(trkpt, "ele");
                Double ele = eleEl == null ? null : Double.parseDouble(eleEl.getTextContent().trim());
                points.add(new TrkPt(lat, lon, ele));
            }
        }
        return points;
    }

    private static double totalDistanceKm(List<TrkPt> points) {
        double meters = 0.0;
        for (int i = 1; i < points.size(); i++) {
            meters += haversineMeters(points.get(i - 1), points.get(i));
        }
        return meters / 1000.0;
    }

    private static double haversineMeters(TrkPt a, TrkPt b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    /** Total ascent ("Höhenmeter"), not net elevation change. Null if any trkpt lacks {@code <ele>}. */
    private static Double totalAltitudeGainM(List<TrkPt> points) {
        for (TrkPt p : points) {
            if (p.ele() == null) return null;
        }
        double gain = 0.0;
        double reference = points.get(0).ele();
        for (int i = 1; i < points.size(); i++) {
            double ele = points.get(i).ele();
            double delta = ele - reference;
            if (Math.abs(delta) >= ELEVATION_NOISE_THRESHOLD_M) {
                if (delta > 0) gain += delta;
                reference = ele;
            }
        }
        return gain;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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

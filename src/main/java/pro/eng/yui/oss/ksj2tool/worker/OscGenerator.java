package pro.eng.yui.oss.ksj2tool.worker;

import pro.eng.yui.oss.ksj2tool.osm.OsmNode;
import pro.eng.yui.oss.ksj2tool.osm.OsmTag;
import pro.eng.yui.oss.ksj2tool.osm.OsmWay;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class OscGenerator {

    public void generate(Path outputPath, List<ObjectUpdate> updates) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            writer.write("<osmChange version=\"0.6\" generator=\"KSJ2AddressSalvage\">\n");
            writer.write("<modify>\n");
            for (ObjectUpdate update : updates) {
                if (update instanceof NodeAddressUpdate nodeUpdate) {
                    writeNode(writer, nodeUpdate);
                } else if (update instanceof WayAddressUpdate wayUpdate) {
                    writeWay(writer, wayUpdate);
                }
            }
            writer.write("</modify>\n");
            writer.write("</osmChange>\n");
        }
    }

    private void writeNode(BufferedWriter writer, NodeAddressUpdate update) throws IOException {
        OsmNode node = update.node();
        writer.write(String.format("  <node id=\"%d\" lat=\"%f\" lon=\"%f\" version=\"%d\">\n",
            node.getId(), node.getLat(), node.getLon(), node.getVersion()));
        writeTags(writer, node.getTags(), update.additionalTags(), update.removeTags());
        writer.write("  </node>\n");
    }

    private void writeWay(BufferedWriter writer, WayAddressUpdate update) throws IOException {
        OsmWay way = update.way();
        writer.write(String.format("  <way id=\"%d\" version=\"%d\">\n",
            way.getId(), way.getVersion()));
        for (var nd : way.getNodes()) {
            writer.write(String.format("    <nd ref=\"%d\"/>\n", nd.getRef()));
        }
        writeTags(writer, way.getTags(), update.additionalTags(), update.removeTags());
        writer.write("  </way>\n");
    }

    private void writeTags(BufferedWriter writer, List<OsmTag> existingTags, Map<String, String> additionalTags, List<String> removeTags) throws IOException {
        // 既存タグの書き出し (削除対象以外)
        for (OsmTag tag : existingTags) {
            if (removeTags != null && removeTags.contains(tag.getK())) {
                continue;
            }
            writer.write(String.format("    <tag k=\"%s\" v=\"%s\"/>\n", escape(tag.getK()), escape(tag.getV())));
        }
        // 追加タグ
        for (var entry : additionalTags.entrySet()) {
            writer.write(String.format("    <tag k=\"%s\" v=\"%s\"/>\n", escape(entry.getKey()), escape(entry.getValue())));
        }
    }

    private String escape(String input) {
        if (input == null){ return ""; }
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }

    public interface ObjectUpdate {}
    public record NodeAddressUpdate(OsmNode node, Map<String, String> additionalTags, List<String> removeTags) implements ObjectUpdate {}
    public record WayAddressUpdate(OsmWay way, Map<String, String> additionalTags, List<String> removeTags) implements ObjectUpdate {}
}

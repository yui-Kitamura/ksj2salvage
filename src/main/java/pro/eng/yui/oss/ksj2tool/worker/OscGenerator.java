package pro.eng.yui.oss.ksj2tool.worker;

import pro.eng.yui.oss.ksj2tool.osm.OsmNode;
import pro.eng.yui.oss.ksj2tool.osm.OsmTag;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class OscGenerator {

    public void generate(Path outputPath, List<NodeAddressUpdate> updates) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            writer.write("<osmChange version=\"0.6\" generator=\"KSJ2AddressSalvage\">\n");
            writer.write("<modify>\n");

            for (NodeAddressUpdate update : updates) {
                OsmNode node = update.node();
                writer.write(String.format("  <node id=\"%d\" lat=\"%f\" lon=\"%f\" version=\"%d\">\n",
                    node.getId(), node.getLat(), node.getLon(), node.getVersion()));
                
                // 既存タグの書き出し
                for (OsmTag tag : node.getTags()) {
                    writer.write(String.format("    <tag k=\"%s\" v=\"%s\"/>\n", escape(tag.getK()), escape(tag.getV())));
                }
                
                // 追加タグ
                for (var entry : update.additionalTags().entrySet()) {
                    writer.write(String.format("    <tag k=\"%s\" v=\"%s\"/>\n", escape(entry.getKey()), escape(entry.getValue())));
                }
                
                writer.write("  </node>\n");
            }

            writer.write("</modify>\n");
            writer.write("</osmChange>\n");
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

    public record NodeAddressUpdate(OsmNode node, Map<String, String> additionalTags) {}
}

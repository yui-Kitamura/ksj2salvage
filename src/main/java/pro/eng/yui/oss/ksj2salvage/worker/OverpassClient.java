package pro.eng.yui.oss.ksj2salvage.worker;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.eng.yui.oss.ksj2salvage.osm.Osm;
import pro.eng.yui.oss.ksj2salvage.osm.OsmNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OverpassClient {
    private static final Logger log = LogManager.getLogger(OverpassClient.class);
    private final OkHttpClient client;
    private final XmlMapper xmlMapper;

    public OverpassClient(OkHttpClient client, XmlMapper xmlMapper) {
        this.client = client;
        this.xmlMapper = xmlMapper;
    }

    public List<OsmNode> fetchTargetNodes(String prefecture) throws IOException {
        String query = String.format(
            "[out:xml][timeout:180];\n" +
            "area[\"admin_level\"=\"4\"][\"name\"=\"%s\"]->.searchArea;\n" +
            "node\n" +
            "  [source=KSJ2]\n" +
            "  [amenity]\n" +
            "  [!\"addr:full\"]\n" +
            "  [!\"addr:neighbourhood\"]\n" +
            "  [!\"KSJ2:ADS\"]\n" +
            "  (area.searchArea);\n" +
            "out body;", prefecture);

        log.info("Overpass API に問い合わせ中 (都府県: {})...", prefecture);
        
        Request request = new Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(okhttp3.RequestBody.create(query, okhttp3.MediaType.parse("text/plain")))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Overpass API 失敗: " + response);
            }
            String body = response.body().string();
            Osm osm = xmlMapper.readValue(body, Osm.class);
            List<OsmNode> nodes = osm.getNodes();

            // ID一覧を保存
            List<String> ids = nodes.stream().map(n -> String.valueOf(n.getId())).collect(Collectors.toList());
            Files.write(Paths.get(prefecture + ".overpass.tmp"), ids, StandardCharsets.UTF_8);

            log.info("取得件数: {}", nodes.size());
            return nodes;
        }
    }
}

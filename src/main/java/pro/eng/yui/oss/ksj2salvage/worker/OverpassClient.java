package pro.eng.yui.oss.ksj2salvage.worker;

import tools.jackson.dataformat.xml.XmlMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.FormBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.eng.yui.oss.ksj2salvage.osm.Osm;
import pro.eng.yui.oss.ksj2salvage.osm.OsmNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
            .header("User-Agent", "KSJ2AddressSalvage/1.0 (https://github.com/yui-oss/ksj2salvage)")
            .header("Accept", "application/osm3s+xml")
            .post(new FormBody.Builder().add("data", query).build())
            .build();

        int maxRetries = 3;
        int attempt = 0;
        while (true) {
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    Osm osm = xmlMapper.readValue(body, Osm.class);
                    List<OsmNode> nodes = osm.getNodes();

                    // ID一覧を保存
                    List<String> ids = nodes.stream().map(n -> String.valueOf(n.getId())).collect(Collectors.toList());
                    Files.write(Paths.get(prefecture + ".overpass.tmp"), ids, StandardCharsets.UTF_8);

                    log.info("取得件数: {}", nodes.size());
                    return nodes;
                }
                log.warn("Overpass API 失敗 (試行 {}/{}): {}", attempt + 1, maxRetries + 1, response);
                if (attempt >= maxRetries) {
                    throw new IOException("Overpass API 最終失敗: " + response);
                }
            } catch (IOException e) {
                log.warn("Overpass API 例外発生 (試行 {}/{}): {}", attempt + 1, maxRetries + 1, e.getMessage());
                if (attempt >= maxRetries) {
                    throw e;
                }
            }

            attempt++;
            try {
                log.info("5秒待機してリトライします...");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("待機中に割り込まれました", e);
            }
        }
    }
}

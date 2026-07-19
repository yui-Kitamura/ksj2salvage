package pro.eng.yui.oss.ksj2tool.worker;

import tools.jackson.dataformat.xml.XmlMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.FormBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.eng.yui.oss.ksj2tool.osm.Osm;
import pro.eng.yui.oss.ksj2tool.osm.OsmNode;
import pro.eng.yui.oss.ksj2tool.osm.OsmWay;
import pro.eng.yui.oss.ksj2tool.util.RetryUtils;

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

    public Osm fetchTargetNodes(String customLabel) throws IOException {
        String query = 
                "[out:xml][timeout:3600];\n" +
                "node\n" +
                "  [source=KSJ2]\n" +
                "  [amenity]\n" +
                "  [!\"addr:full\"]\n" +
                "  [!\"addr:block_number\"]\n" +
                "  [!\"KSJ2:ADS\"];\n" +
                "out meta center;";
        return executeFetch(query, customLabel);
    }

    public Osm fetchTargetNodes(String prefecture, String suffix) throws IOException {
        String query = String.format(
                "[out:xml][timeout:180];\n" +
                    "area[\"admin_level\"=\"4\"][\"name\"=\"%s\"]->.searchArea;\n" +
                    "node\n" +
                    "  [source=KSJ2]\n" +
                    "  [amenity]\n" +
                    "  [!\"addr:full\"]\n" +
                    "  [!\"addr:block_number\"]\n" +
                    "  [!\"KSJ2:ADS\"]\n" +
                    "  (area.searchArea);\n" +
                    "out meta center;", prefecture);

        return executeFetch(query, prefecture + "_" + suffix);
    }

    public Osm fetchNormalizeTargets() throws IOException {
        String query =
                "[out:xml][timeout:3600];\n" +
                "(\n" +
                "  node[source=KSJ2][\"KSJ2:ADS\"][!\"addr:full\"][!\"addr:block_number\"];\n" +
                "  way[source=KSJ2][\"KSJ2:ADS\"][!\"addr:full\"][!\"addr:block_number\"];\n" +
                ");\n" +
                "out meta center;";
        return executeFetch(query, "全国_normalize");
    }

    public Osm fetchNormalizeTargets(String prefecture) throws IOException {
        String query = String.format(
                "[out:xml][timeout:300];\n" +
                "area[\"admin_level\"=\"4\"][\"name\"=\"%s\"]->.searchArea;\n" +
                "(\n" +
                "  node[source=KSJ2][\"KSJ2:ADS\"][!\"addr:full\"][!\"addr:block_number\"](area.searchArea);\n" +
                "  way[source=KSJ2][\"KSJ2:ADS\"][!\"addr:full\"][!\"addr:block_number\"](area.searchArea);\n" +
                ");\n" +
                "out meta center;", prefecture);
        return executeFetch(query, prefecture + "_normalize");
    }

    private Osm executeFetch(String query, String saveLabel) throws IOException {
        log.info("Overpass API に問い合わせ中 (対象: {})...", saveLabel);
        
        Request request = new Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .header("User-Agent", "KSJ2AddressSalvage/1.0 (https://github.com/yui-oss/ksj2tool)")
            .header("Accept", "application/osm3s+xml")
            .post(new FormBody.Builder().add("data", query).build())
            .build();

        int maxRetries = 3;
        int attempt = 0;
        Response lastResponse = null;
        while (true) {
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    Osm osm = xmlMapper.readValue(body, Osm.class);

                    // ID一覧を保存 (node, way)
                    List<String> ids = osm.getNodes().stream().map(n -> "n" + n.getId()).collect(Collectors.toList());
                    ids.addAll(osm.getWays().stream().map(w -> "w" + w.getId()).collect(Collectors.toList()));
                    Files.write(Paths.get(saveLabel + ".overpass.tmp"), ids, StandardCharsets.UTF_8);

                    log.info("取得件数: node={}, way={}", osm.getNodes().size(), osm.getWays().size());
                    return osm;
                }
                log.warn("Overpass API 失敗 (試行 {}/{}): {}", attempt + 1, maxRetries + 1, response);
                if (attempt >= maxRetries) {
                    throw new IOException("Overpass API 最終失敗: " + response);
                }
                lastResponse = response;
            } catch (IOException e) {
                log.warn("Overpass API 例外発生 (試行 {}/{}): {}", attempt + 1, maxRetries + 1, e.getMessage());
                if (attempt >= maxRetries) {
                    throw e;
                }
                lastResponse = null;
            }

            attempt++;
            try {
                if (lastResponse != null) {
                    RetryUtils.waitForRetry(lastResponse, 5);
                } else {
                    log.info("OverpassAPI 5秒待機してリトライします...");
                    Thread.sleep(5000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("待機中に割り込まれました", e);
            }
        }
    }
}

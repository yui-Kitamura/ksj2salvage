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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminAreaClient {
    private static final Logger log = LogManager.getLogger(AdminAreaClient.class);
    private final OkHttpClient client;
    private final XmlMapper xmlMapper;

    public AdminAreaClient(OkHttpClient client, XmlMapper xmlMapper) {
        this.client = client;
        this.xmlMapper = xmlMapper;
    }

    public String fetchAdminAreaName(double lat, double lon) throws IOException {
        String query = String.format(
            "[out:xml][timeout:30];\n" +
            "is_in(%f,%f)->.a;\n" +
            "rel(picker.a)[\"boundary\"=\"administrative\"][\"admin_level\"~\"^[4678]$\"];\n" +
            "out tags;", lat, lon);

        Request request = new Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(okhttp3.RequestBody.create(query, okhttp3.MediaType.parse("text/plain")))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("AdminArea API 失敗: " + response);
            }
            String body = response.body().string();
            Osm osm = xmlMapper.readValue(body, Osm.class);
            return buildAdminAreaName(osm.getRelations());
        }
    }

    private String buildAdminAreaName(List<OsmRelation> relations) {
        Map<String, String> levelToName = relations.stream()
            .map(OsmRelation::getTagMap)
            .filter(tags -> tags.containsKey("admin_level") && tags.containsKey("name"))
            .collect(Collectors.toMap(
                tags -> tags.get("admin_level"),
                tags -> tags.get("name"),
                (v1, v2) -> v1 // 重複時は最初を採用
            ));

        // admin_level 7 または 8 (東京23区や政令市の区)
        // 郡 (admin_level 6) は含めない場合が多いが、要件では 4, 6, 7, 8 を取得
        // 一般的には 都道府県(4) + 市区町村(7) で構成
        // 東京23区の場合は 7 が特別区名になる
        String pref = levelToName.get("4");
        String gun = levelToName.get("6");
        String city = levelToName.get("7");
        String ku = levelToName.get("8");

        StringBuilder sb = new StringBuilder();
        // pref は addr:full に含めるか？ 要件例では「山梨県中央市...」
        if (pref != null) sb.append(pref);
        if (gun != null) sb.append(gun);
        if (city != null) sb.append(city);
        if (ku != null) sb.append(ku);

        return sb.toString();
    }
}

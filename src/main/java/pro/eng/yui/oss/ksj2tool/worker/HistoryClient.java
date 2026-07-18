package pro.eng.yui.oss.ksj2tool.worker;

import tools.jackson.dataformat.xml.XmlMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.eng.yui.oss.ksj2tool.osm.Osm;
import pro.eng.yui.oss.ksj2tool.osm.OsmNode;
import pro.eng.yui.oss.ksj2tool.osm.OsmWay;
import pro.eng.yui.oss.ksj2tool.util.RetryUtils;

import java.io.IOException;
import java.util.Optional;

public class HistoryClient {
    private static final Logger log = LogManager.getLogger(HistoryClient.class);
    private final OkHttpClient client;
    private final XmlMapper xmlMapper;

    public HistoryClient(OkHttpClient client, XmlMapper xmlMapper) {
        this.client = client;
        this.xmlMapper = xmlMapper;
    }

    public Optional<OsmNode> fetchNodeVersion1(long nodeId) throws IOException {
        return fetchVersion1("node", nodeId, OsmNode.class);
    }

    public Optional<OsmWay> fetchWayVersion1(long wayId) throws IOException {
        return fetchVersion1("way", wayId, OsmWay.class);
    }

    private <T> Optional<T> fetchVersion1(String type, long id, Class<T> clazz) throws IOException {
        String url = String.format("https://api.openstreetmap.org/api/0.6/%s/%d/history", type, id);
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "KSJ2AddressSalvage/1.0 (https://github.com/yui-Kitamura/ksj2salvage)")
            .header("Accept", "application/xml")
            .build();

        int retries = 3;
        while (retries > 0) {
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    Osm osm = xmlMapper.readValue(body, Osm.class);
                    if (clazz == OsmNode.class) {
                        return (Optional<T>) osm.getNodes().stream()
                            .filter(n -> n.getVersion() == 1)
                            .findFirst();
                    } else if (clazz == OsmWay.class) {
                        return (Optional<T>) osm.getWays().stream()
                            .filter(w -> w.getVersion() == 1)
                            .findFirst();
                    }
                    return Optional.empty();
                } else if (response.code() == 404) {
                    return Optional.empty();
                } else if (response.code() == 429 || response.code() >= 500) {
                    retries--;
                    if (retries > 0) {
                        log.warn("History API リトライ中 ({}/{}): 残り {} 回", type, id, retries);
                        RetryUtils.waitForRetry(response, 2);
                    }
                } else {
                    throw new IOException("History API 失敗: " + response);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            } catch (IOException e) {
                retries--;
                if (retries <= 0) throw e;
                log.warn("History API 通信エラー リトライ中 ({}/{}): {}", type, id, e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }
        return Optional.empty();
    }
}

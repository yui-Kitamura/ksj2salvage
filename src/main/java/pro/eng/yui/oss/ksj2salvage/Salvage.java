package pro.eng.yui.oss.ksj2salvage;

import tools.jackson.dataformat.xml.XmlMapper;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.eng.yui.oss.ksj2salvage.osm.OsmNode;
import pro.eng.yui.oss.ksj2salvage.util.GeoUtils;
import pro.eng.yui.oss.ksj2salvage.worker.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class Salvage {

    private static final Logger log = LogManager.getLogger(Salvage.class);

    private final OkHttpClient client;
    private final XmlMapper xmlMapper;
    private final OverpassClient overpassClient;
    private final HistoryClient historyClient;
    private final AdminAreaClient adminAreaClient;
    private final OscGenerator oscGenerator;

    private int totalCount = 0;
    private int processedCount = 0;
    private int successCount = 0;
    private int skippedCount = 0;
    private int errorCount = 0;

    // 階級別位置変化ノード数: 0 | 0-10 | 10-30 | 30-50 | 50-100 | 100-
    private int dist0 = 0;
    private int dist0_10 = 0;
    private int dist10_30 = 0;
    private int dist30_50 = 0;
    private int dist50_100 = 0;
    private int distAbove100 = 0;

    public Salvage() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        this.xmlMapper = new XmlMapper();
        this.overpassClient = new OverpassClient(client, xmlMapper);
        this.historyClient = new HistoryClient(client, xmlMapper);
        this.adminAreaClient = new AdminAreaClient(client, xmlMapper);
        this.oscGenerator = new OscGenerator();
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("引数に都府県名(JIS表記)を指定してください。例: 東京都");
            System.exit(1);
        }

        String prefecture = args[0];
        try {
            new Salvage().run(prefecture);
        } catch (Exception e) {
            log.error("致命的なエラーが発生しました: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    public void run(String prefecture) throws IOException {
        log.info("--- 処理開始: {} ---", prefecture);

        List<OscGenerator.NodeAddressUpdate> updates = new ArrayList<>();
        List<OsmNode> targetNodes;
        if("全国".equals(prefecture)) {
            targetNodes = overpassClient.fetchTargetNodes();
        }else {
            targetNodes = overpassClient.fetchTargetNodes(prefecture);
        }
        totalCount = targetNodes.size();

        for (OsmNode node : targetNodes) {
            processedCount++;
            try {
                TimeUnit.SECONDS.sleep(1L);
            } catch (InterruptedException ignore) {
            }
            processNode(node).ifPresent(updates::add);
        }

        if (!updates.isEmpty()) {
            oscGenerator.generate(Paths.get("output", prefecture + ".osc"), updates);
        }

        printSummary();
    }

    private Optional<OscGenerator.NodeAddressUpdate> processNode(OsmNode node) {
        log.info("[{}/{}] ノードを処理中: ID={}, Name={}", processedCount, totalCount, node.getId(), node.getTagMap().getOrDefault("name", "N/A"));
        try {
            // 現在 addr:* が存在するか (Overpassクエリでもフィルタしているが念のため)
            Map<String, String> currentTags = node.getTagMap();
            if (currentTags.keySet().stream().anyMatch(k -> k.startsWith("addr:"))) {
                log.info(" -> スキップ (addr:* タグが既に存在します)");
                skippedCount++;
                return Optional.empty();
            }

            // History API から v1 取得
            Optional<OsmNode> v1NodeOpt = historyClient.fetchVersion1(node.getId());
            if (v1NodeOpt.isEmpty()) {
                log.info(" -> スキップ (v1 取得失敗)");
                skippedCount++;
                return Optional.empty();
            }

            OsmNode v1Node = v1NodeOpt.get();

            // 位置の変化を検証
            double distance = GeoUtils.calculateDistance(node.getLat(), node.getLon(), v1Node.getLat(), v1Node.getLon());
            updateDistanceStats(distance);

            if (distance >= 50.0) {
                log.info(" -> スキップ (位置が大きく変化しています: {:.1f}m)", distance);
                skippedCount++;
                return Optional.empty();
            }

            Map<String, String> v1Tags = v1Node.getTagMap();
            String ksj2ads = v1Tags.get("KSJ2:ADS");
            if (ksj2ads == null || ksj2ads.isEmpty()) {
                log.info(" -> スキップ (KSJ2:ADS タグなし)");
                skippedCount++;
                return Optional.empty();
            }

            // 行政界取得
            AdminAreaClient.AdminAreaResult adminArea = adminAreaClient.fetchAdminArea(node.getLat(), node.getLon());
            if (adminArea.fullName() == null || adminArea.fullName().isEmpty()) {
                log.info(" -> スキップ (行政界取得失敗)");
                skippedCount++;
                return Optional.empty();
            }

            Map<String, String> additionalTags = new HashMap<>();
            String fullAddress = adminArea.fullName() + ksj2ads;
            additionalTags.put("addr:full", fullAddress);

            // 10m以上の変化にfixmeタグ付与
            if (distance >= 10.0) {
                additionalTags.put("fixme", String.format("addr:fullを機械付与した時点でオリジナルと位置が%.1fm変化しています", distance));
            }

            // KSJ2:PubFacAdmin サルベージ
            String pubFacAdmin = v1Tags.get("KSJ2:PubFacAdmin");
            if (pubFacAdmin != null && !currentTags.containsKey("KSJ2:PubFacAdmin")) {
                switch (pubFacAdmin) {
                    case "民間" -> additionalTags.put("operator:type", "private");
                    case "市区町村" -> {
                        String op = "";
                        if (adminArea.prefecture() != null) op += adminArea.prefecture();
                        if (adminArea.city() != null) op += adminArea.city();
                        if (!op.isEmpty()) {
                            additionalTags.put("operator", op);
                        }
                    }
                    case "都道府県" -> {
                        if (adminArea.prefecture() != null) {
                            additionalTags.put("operator", adminArea.prefecture());
                        }
                    }
                    case "国", "その他" -> additionalTags.put("KSJ2:PubFacAdmin", pubFacAdmin);
                }
            }

            log.info(" -> OK: {}", fullAddress);
            successCount++;
            return Optional.of(new OscGenerator.NodeAddressUpdate(node, additionalTags));

        } catch (IOException e) {
            log.info(" -> FAIL (エラー: {})", e.getMessage());
            log.error("Node {} の処理中にエラーが発生しました: {}", node.getId(), e.getMessage());
            errorCount++;
            return Optional.empty();
        }
    }

    private void updateDistanceStats(double distance) {
        if (distance == 0) {
            dist0++;
        } else if (distance < 10.0) {
            dist0_10++;
        } else if (distance < 30.0) {
            dist10_30++;
        } else if (distance < 50.0) {
            dist30_50++;
        } else if (distance < 100.0) {
            dist50_100++;
        } else {
            distAbove100++;
        }
    }

    private void printSummary() {
        log.info("--- 処理完了 ---");
        log.info("取得件数: {}", totalCount);
        log.info("処理件数: {}", processedCount);
        log.info("成功件数: {}", successCount);
        log.info("対象外件数: {}", skippedCount);
        log.info("API失敗件数: {}", errorCount);
        log.info("階級別位置変化ノード数(m): 0:{} | 0-10:{} | 10-30:{} | 30-50:{} | 50-100:{} | 100-:{}",
            dist0, dist0_10, dist10_30, dist30_50, dist50_100, distAbove100);
    }
}

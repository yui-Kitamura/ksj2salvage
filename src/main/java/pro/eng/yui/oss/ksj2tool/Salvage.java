package pro.eng.yui.oss.ksj2tool;

import tools.jackson.dataformat.xml.XmlMapper;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.eng.yui.oss.ksj2tool.osm.Osm;
import pro.eng.yui.oss.ksj2tool.osm.OsmNode;
import pro.eng.yui.oss.ksj2tool.osm.OsmWay;
import pro.eng.yui.oss.ksj2tool.util.GeoUtils;
import pro.eng.yui.oss.ksj2tool.worker.*;

import java.io.IOException;
import java.nio.file.Path;
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
    private int normalizedCount = 0;

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
            System.err.println("オプション: [mode]");
            System.err.println("  salvage:   現行の救済処理を実行");
            System.err.println("  normalize: 正規化処理を実行 (未実装)");
            System.err.println("  both:      両方を実行 (デフォルト)");
            System.exit(1);
        }

        String prefecture = args[0];
        String mode = (args.length > 1) ? args[1] : "both";

        try {
            new Salvage().run(prefecture, mode);
        } catch (Exception e) {
            log.error("致命的なエラーが発生しました: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    public void run(String prefecture, String mode) throws IOException {
        log.info("--- 処理開始: {} (Mode: {}) ---", prefecture, mode);

        List<OscGenerator.ObjectUpdate> updates = new ArrayList<>();
        totalCount = 0;
        processedCount = 0;

        if ("salvage".equalsIgnoreCase(mode)) {
            Osm osm = fetchSalvageOsm(prefecture);
            totalCount = osm.getNodes().size();
            runSalvage(osm, updates);
        } else if ("normalize".equalsIgnoreCase(mode)) {
            Osm osm = fetchNormalizeOsm(prefecture);
            totalCount = osm.getNodes().size() + osm.getWays().size();
            runNormalize(osm, updates);
        } else if ("both".equalsIgnoreCase(mode)) {
            log.info("Running both salvage and normalize...");
            Osm salvageOsm = fetchSalvageOsm(prefecture);
            Osm normalizeOsm = fetchNormalizeOsm(prefecture);
            totalCount = salvageOsm.getNodes().size() + normalizeOsm.getNodes().size() + normalizeOsm.getWays().size();

            runSalvage(salvageOsm, updates);
            runNormalize(normalizeOsm, updates);
        } else {
            log.warn("Unknown mode: {}. Defaulting to salvage.", mode);
            Osm osm = fetchSalvageOsm(prefecture);
            totalCount = osm.getNodes().size();
            runSalvage(osm, updates);
        }

        if (!updates.isEmpty()) {
            String suffix = mode.toLowerCase();
            Path outputPath = Paths.get("output", prefecture + "_" + suffix + ".osc");
            oscGenerator.generate(outputPath, updates);
            log.info("Output generated: {}", outputPath.toAbsolutePath());
        } else {
            log.info("No updates found. Skip generating .osc file.");
        }

        printSummary();
    }

    private Osm fetchSalvageOsm(String prefecture) throws IOException {
        if ("全国".equals(prefecture)) {
            return overpassClient.fetchTargetNodes("全国_salvage");
        } else {
            return overpassClient.fetchTargetNodes(prefecture, "salvage");
        }
    }

    private Osm fetchNormalizeOsm(String prefecture) throws IOException {
        if ("全国".equals(prefecture)) {
            return overpassClient.fetchNormalizeTargets();
        } else {
            return overpassClient.fetchNormalizeTargets(prefecture);
        }
    }

    private void runSalvage(Osm osm, List<OscGenerator.ObjectUpdate> updates) throws IOException {
        List<OsmNode> targetNodes = osm.getNodes();
        for (OsmNode node : targetNodes) {
            processedCount++;
            try {
                TimeUnit.SECONDS.sleep(1L);
            } catch (InterruptedException ignore) {
            }
            processNode(node).ifPresent(updates::add);
        }
    }

    private void runNormalize(Osm osm, List<OscGenerator.ObjectUpdate> updates) throws IOException {
        log.info("--- Normalize 処理開始 ---");
        List<OsmNode> targetNodes = osm.getNodes();
        List<OsmWay> targetWays = osm.getWays();

        log.info("Normalize対象: {} nodes, {} ways", targetNodes.size(), targetWays.size());

        for (OsmNode node : targetNodes) {
            processedCount++;
            try { TimeUnit.SECONDS.sleep(1L); } catch (InterruptedException ignore) {}
            processNode(node).ifPresent(updates::add);
        }

        for (OsmWay way : targetWays) {
            processedCount++;
            try { TimeUnit.SECONDS.sleep(1L); } catch (InterruptedException ignore) {}
            processWay(way).ifPresent(updates::add);
        }
        log.info("--- Normalize 処理完了 ---");
    }

    private Optional<OscGenerator.ObjectUpdate> processNode(OsmNode node) {
        log.info("[{}/{}] ノードを処理中: ID={}, Name={}", processedCount, totalCount, node.getId(), node.getTagMap().getOrDefault("name", "N/A"));
        
        // すでに KSJ2:ADS がある場合は履歴取得不要 (normalize のケース)
        String currentAds = node.getTagMap().get("KSJ2:ADS");
        if (currentAds != null && !currentAds.isEmpty()) {
            normalizedCount++;
            return processWithAds(node.getId(), node.getLat(), node.getLon(), node.getTagMap(), currentAds, 0.0, node.getTagMap())
                .map(tags -> new OscGenerator.NodeAddressUpdate(node, tags));
        }

        return processWithHistory(node.getId(), node.getLat(), node.getLon(), node.getTagMap(),
            () -> historyClient.fetchNodeVersion1(node.getId()))
            .map(tags -> new OscGenerator.NodeAddressUpdate(node, tags));
    }

    private Optional<OscGenerator.ObjectUpdate> processWay(OsmWay way) {
        log.info("[{}/{}] ウェイを処理中: ID={}, Name={}", processedCount, totalCount, way.getId(), way.getTagMap().getOrDefault("name", "N/A"));
        if (way.getCenter() == null) {
            log.info(" -> スキップ (座標情報がありません)");
            skippedCount++;
            return Optional.empty();
        }

        // すでに KSJ2:ADS がある場合は履歴取得不要 (normalize のケース)
        String currentAds = way.getTagMap().get("KSJ2:ADS");
        if (currentAds != null && !currentAds.isEmpty()) {
            normalizedCount++;
            return processWithAds(way.getId(), way.getCenter().getLat(), way.getCenter().getLon(), way.getTagMap(), currentAds, 0.0, way.getTagMap())
                .map(tags -> new OscGenerator.WayAddressUpdate(way, tags));
        }

        return processWithHistory(way.getId(), way.getCenter().getLat(), way.getCenter().getLon(), way.getTagMap(),
            () -> historyClient.fetchWayVersion1(way.getId()))
            .map(tags -> new OscGenerator.WayAddressUpdate(way, tags));
    }

    private interface Version1Fetcher<T> {
        Optional<T> fetch() throws IOException;
    }

    private <T> Optional<Map<String, String>> processWithHistory(long id, double lat, double lon, Map<String, String> currentTags, Version1Fetcher<T> fetcher) {
        try {
            // 現在 addr:* が存在するか (Overpassクエリでもフィルタしているが念のため)
            if (currentTags.keySet().stream().anyMatch(k -> k.startsWith("addr:"))) {
                log.info(" -> スキップ (addr:* タグが既に存在します)");
                skippedCount++;
                return Optional.empty();
            }

            // History API から v1 取得
            Optional<T> v1ObjOpt = fetcher.fetch();
            if (v1ObjOpt.isEmpty()) {
                log.info(" -> スキップ (v1 取得失敗)");
                skippedCount++;
                return Optional.empty();
            }

            T v1Obj = v1ObjOpt.get();
            double v1Lat, v1Lon;
            Map<String, String> v1Tags;

            if (v1Obj instanceof OsmNode v1Node) {
                v1Lat = v1Node.getLat();
                v1Lon = v1Node.getLon();
                v1Tags = v1Node.getTagMap();
            } else if (v1Obj instanceof OsmWay v1Way) {
                if (v1Way.getCenter() == null) {
                    log.info(" -> スキップ (v1 座標情報なし)");
                    skippedCount++;
                    return Optional.empty();
                }
                v1Lat = v1Way.getCenter().getLat();
                v1Lon = v1Way.getCenter().getLon();
                v1Tags = v1Way.getTagMap();
            } else {
                return Optional.empty();
            }

            // 位置の変化を検証
            double distance = GeoUtils.calculateDistance(lat, lon, v1Lat, v1Lon);
            updateDistanceStats(distance);

            if (distance >= 50.0) {
                log.info(" -> スキップ (位置が大きく変化しています: {}m)", String.format("%.1f", distance));
                skippedCount++;
                return Optional.empty();
            }

            String ksj2ads = v1Tags.get("KSJ2:ADS");
            if (ksj2ads == null || ksj2ads.isEmpty()) {
                log.info(" -> スキップ (KSJ2:ADS タグなし)");
                skippedCount++;
                return Optional.empty();
            }

            return processWithAds(id, lat, lon, currentTags, ksj2ads, distance, v1Tags);

        } catch (IOException e) {
            log.info(" -> FAIL (エラー: {})", e.getMessage());
            log.error("Object {} の処理中にエラーが発生しました: {}", id, e.getMessage());
            errorCount++;
            return Optional.empty();
        }
    }

    private Optional<Map<String, String>> processWithAds(long id, double lat, double lon, Map<String, String> currentTags, String ksj2ads, double distance, Map<String, String> v1Tags) {
        try {
            // 行政界取得
            AdminAreaClient.AdminAreaResult adminArea = adminAreaClient.fetchAdminArea(lat, lon);
            if (adminArea.fullName() == null || adminArea.fullName().isEmpty()) {
                log.info(" -> スキップ (行政界取得失敗)");
                skippedCount++;
                return Optional.empty();
            }

            Map<String, String> additionalTags = new HashMap<>();
            String fullAddress = adminArea.fullName() + ksj2ads;
            additionalTags.put("addr:full", fullAddress);

            // 30m以上の変化にfixmeタグ付与
            if (distance >= 30.0) {
                additionalTags.put("fixme", String.format("addr:fullを機械付与した時点でオリジナルと位置が%.1fm変化しています", distance));
            }

            // KSJ2:PubFacAdmin サルベージ
            String pubFacAdmin = v1Tags.get("KSJ2:PubFacAdmin");
            if (pubFacAdmin != null) {
                switch (pubFacAdmin) {
                    case "民間" -> {
                        if (!currentTags.containsKey("operator:type")) {
                            additionalTags.put("operator:type", "private");
                        }
                    }
                    case "市区町村" -> {
                        if (!currentTags.containsKey("operator")) {
                            String op = "";
                            if (adminArea.prefecture() != null) op += adminArea.prefecture();
                            if (adminArea.city() != null) op += adminArea.city();
                            if (!op.isEmpty()) {
                                additionalTags.put("operator", op);
                            }
                        }
                    }
                    case "都道府県" -> {
                        if (!currentTags.containsKey("operator")) {
                            if (adminArea.prefecture() != null) {
                                additionalTags.put("operator", adminArea.prefecture());
                            }
                        }
                    }
                    case "国", "その他" -> {
                        if (!currentTags.containsKey("KSJ2:PubFacAdmin")) {
                            additionalTags.put("KSJ2:PubFacAdmin", pubFacAdmin);
                        }
                    }
                }
            }

            log.info(" -> OK: {}", fullAddress);
            successCount++;
            return Optional.of(additionalTags);

        } catch (IOException e) {
            log.info(" -> FAIL (エラー: {})", e.getMessage());
            log.error("Object {} の処理中にエラーが発生しました: {}", id, e.getMessage());
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
        log.info("距離による除外件数: {}", skippedCount);
        log.info("API失敗件数: {}", errorCount);
        log.info("階級別位置変化ノード数(m): 0:{} | 0-10:{} | 10-30:{} | 30-50:{} | 50-100:{} | 100-:{}",
            dist0, dist0_10, dist10_30, dist30_50, dist50_100, distAbove100);
        log.info("距離判定をしない件数: {}", normalizedCount);
    }
}

package pro.eng.yui.oss.ksj2salvage;

import tools.jackson.dataformat.xml.XmlMapper;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.eng.yui.oss.ksj2salvage.osm.OsmNode;
import pro.eng.yui.oss.ksj2salvage.worker.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
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
        List<OsmNode> targetNodes = overpassClient.fetchTargetNodes(prefecture);
        totalCount = targetNodes.size();

        for (OsmNode node : targetNodes) {
            processedCount++;
            processNode(node).ifPresent(updates::add);
        }

        if (!updates.isEmpty()) {
            oscGenerator.generate(Paths.get("output", prefecture + ".osc"), updates);
        }

        printSummary();
    }

    private Optional<OscGenerator.NodeAddressUpdate> processNode(OsmNode node) {
        try {
            // 現在 addr:* が存在するか (Overpassクエリでもフィルタしているが念のため)
            Map<String, String> currentTags = node.getTagMap();
            if (currentTags.keySet().stream().anyMatch(k -> k.startsWith("addr:"))) {
                skippedCount++;
                return Optional.empty();
            }

            // History API から v1 取得
            Optional<OsmNode> v1NodeOpt = historyClient.fetchVersion1(node.getId());
            if (v1NodeOpt.isEmpty()) {
                skippedCount++;
                return Optional.empty();
            }

            OsmNode v1Node = v1NodeOpt.get();
            String ksj2ads = v1Node.getTagMap().get("KSJ2:ADS");
            if (ksj2ads == null || ksj2ads.isEmpty()) {
                skippedCount++;
                return Optional.empty();
            }

            // 行政界取得
            String adminArea = adminAreaClient.fetchAdminAreaName(node.getLat(), node.getLon());
            if (adminArea == null || adminArea.isEmpty()) {
                skippedCount++;
                return Optional.empty();
            }

            String fullAddress = adminArea + ksj2ads;
            successCount++;
            return Optional.of(new OscGenerator.NodeAddressUpdate(node, fullAddress));

        } catch (IOException e) {
            log.error("Node {} の処理中にエラーが発生しました: {}", node.getId(), e.getMessage());
            errorCount++;
            return Optional.empty();
        }
    }

    private void printSummary() {
        log.info("--- 処理完了 ---");
        log.info("取得件数: {}", totalCount);
        log.info("処理件数: {}", processedCount);
        log.info("成功件数: {}", successCount);
        log.info("対象外件数: {}", skippedCount);
        log.info("API失敗件数: {}", errorCount);
    }
}

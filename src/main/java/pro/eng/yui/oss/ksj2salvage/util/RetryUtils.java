package pro.eng.yui.oss.ksj2salvage.util;

import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public class RetryUtils {
    private static final Logger log = LogManager.getLogger(RetryUtils.class);

    /**
     * HTTP 429 Too Many Requests 等のレスポンスから Retry-After ヘッダを読み取り、
     * 指定された秒数（またはデフォルトの秒数）待機します。
     *
     * @param response HTTPレスポンス
     * @param defaultSeconds Retry-Afterヘッダがない場合のデフォルト待機秒数
     * @throws InterruptedException 待機中に割り込まれた場合
     */
    public static void waitForRetry(Response response, int defaultSeconds) throws InterruptedException {
        int waitSeconds = defaultSeconds;
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null && !retryAfter.isEmpty()) {
            try {
                // Retry-After は秒数(整数)またはHTTP日付
                // ここでは主にOverpass API等で返される秒数を想定
                waitSeconds = Integer.parseInt(retryAfter);
                log.info("Retry-After ヘッダを検出しました: {} 秒待機します", waitSeconds);
            } catch (NumberFormatException e) {
                log.warn("Retry-After ヘッダの解析に失敗しました: {}. デフォルトの {} 秒待機します", retryAfter, defaultSeconds);
            }
        } else {
            String host = response.request().url().host();
            log.info("{} API429エラー。 {} 秒待機してリトライします...", host, waitSeconds);
        }

        if (waitSeconds > 0) {
            TimeUnit.SECONDS.sleep(waitSeconds);
        }
    }
}

package pro.eng.yui.oss.ksj2salvage.util;

public class GeoUtils {

    /**
     * ハバーサインの公式を用いて2点間の距離を計算する
     * @param lat1 緯度1
     * @param lon1 経度1
     * @param lat2 緯度2
     * @param lon2 経度2
     * @return 距離 (メートル)
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0; // 地球の半径 (m)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}

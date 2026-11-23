package app.tools;

import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

/**
 * 공공데이터포털 식약처 의약품 API 중 키로 쓸 수 있는 것을 찾는 점검 도구 (개발용).
 * 요청 주소에 키가 들어가므로 주소는 출력하지 않는다. 응답에 키가 있으면 ****로 가린다.
 */
public final class DrugApiProbe {
    private static final List<String> CANDIDATES = List.of(
            "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService08/getDrugPrdtPrmsnInq08",
            "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService08/getDrugPrdtPrmsnDtlInq07",
            "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService08/getDrugPrdtPrmsnDtlInq08",
            "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService08/getDrugPrdtPrmsnDtlInq06");

    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(Path.of("config", "ai.properties"), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        String key = p.getProperty("drug.apiKey", "").trim();
        System.out.println("drug.apiKey " + (key.isEmpty() ? "없음" : "있음"));
        if (key.isEmpty()) return;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        for (String base : CANDIDATES) {
            String nameParam = base.contains("DrbEasy") ? "itemName" : "item_name";
            String url = base + "?serviceKey=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&type=json&pageNo=1&numOfRows=3&" + nameParam + "=" + URLEncoder.encode("아리피프라졸", StandardCharsets.UTF_8);
            String label = base.substring(base.indexOf("/1471000/") + 9);
            try {
                HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                String body = res.body() == null ? "" : res.body().replace(key, "****");
                System.out.println(label + " → HTTP " + res.statusCode() + " : "
                        + body.substring(0, Math.min(700, body.length())).replaceAll("\\s+", " "));
            } catch (Exception e) {
                System.out.println(label + " → 오류 " + e.getClass().getSimpleName());
            }
        }
    }
}

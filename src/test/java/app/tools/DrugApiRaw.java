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
import java.util.Properties;

/**
 * 허가정보 상세 API 원본 응답을 파일로 저장하는 점검 도구 (개발용). 키는 출력하지 않고 응답 안의 키는 ****로 가린다.
 * 인자: 저장 폴더, 검색어...
 */
public final class DrugApiRaw {
    private static final String DTL = "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService08/getDrugPrdtPrmsnDtlInq08";

    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(Path.of("config", "ai.properties"), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        String key = p.getProperty("drug.apiKey", "").trim();
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        for (int i = 1; i < args.length; i++) {
            String url = DTL + "?serviceKey=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&type=json&pageNo=1&numOfRows=100&item_name=" + URLEncoder.encode(args[i], StandardCharsets.UTF_8);
            HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = res.body() == null ? "" : res.body().replace(key, "****");
            Files.writeString(out.resolve(args[i] + ".json"), body, StandardCharsets.UTF_8);
            System.out.println(args[i] + " → HTTP " + res.statusCode() + ", " + body.length() + "자");
        }
    }
}

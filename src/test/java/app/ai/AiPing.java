package app.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 실제 AI 서버에 짧은 요청 1회를 보내 연결 방식을 확인하는 점검 도구 (개발용, 프로그램에 포함 안 됨).
 * 키는 출력하지 않는다. 응답에 키와 같은 문자열이 있으면 ****로 가린다. 주소는 호스트까지만 보여 준다.
 *
 * <p>실행: ./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dmain.class=app.ai.AiPing
 */
public final class AiPing {
    public static void main(String[] args) throws Exception {
        AiConfig cfg = AiConfig.load(args.length > 0 ? Path.of(args[0]) : AiConfig.DEFAULT_PATH);
        String key = cfg.apiKey();
        URI base = cfg.baseUrl().isEmpty() ? null : URI.create(cfg.baseUrl());
        System.out.println("provider=" + cfg.provider() + ", model=" + cfg.model()
                + ", host=" + (base == null ? "(없음)" : base.getScheme() + "://" + base.getHost() + base.getPath())
                + ", query=" + (base == null || base.getQuery() == null ? "없음" : "있음(내용 생략)")
                + ", key=" + (key.isEmpty() ? "없음" : "있음"));
        if (!cfg.isEnabled()) {
            System.out.println("AI 설정이 비어 있어 요청하지 않음");
            return;
        }
        String body = "{\"model\":\"" + cfg.model() + "\",\"messages\":[{\"role\":\"user\",\"content\":\"OK라고만 답해.\"}]}";
        String url = new AiClient(cfg, null).endpoint();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String text = res.body() == null ? "" : res.body().replace(key, "****");
        System.out.println("endpoint path=" + URI.create(url).getPath() + ", HTTP " + res.statusCode());
        System.out.println("응답 앞부분: " + text.substring(0, Math.min(400, text.length())).replaceAll("\\s+", " "));
    }

}

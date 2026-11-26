package app.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** JDK HttpClient로 실제 요청을 보내는 전송 구현. 테스트에서는 쓰지 않는다. */
public final class JdkHttpTransport implements AiTransport {

    /** 중계 서버가 하루 사용량을 다 썼을 때 보내는 상태 코드 */
    public static final int QUOTA_STATUS = 429;
    public static final String QUOTA_MESSAGE = "오늘 사용량을 모두 썼습니다";

    private final HttpClient client;
    private final Duration timeout;

    public JdkHttpTransport() {
        this(Duration.ofSeconds(10), Duration.ofSeconds(90));
    }

    /** 연결·응답 제한 시간을 정한다 (중계 서버는 위쪽 AI 응답을 기다려야 해서 길게 둔다). */
    public JdkHttpTransport(Duration connectTimeout, Duration timeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.timeout = timeout;
    }

    @Override
    public String post(String url, String apiKey, String jsonBody) throws IOException {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header(APP_HEADER, APP_ID)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            if (apiKey != null && !apiKey.isEmpty()) b.header("Authorization", "Bearer " + apiKey);
            return send(b.build());
        } catch (IllegalArgumentException e) {
            throw new IOException("AI 접속 주소 형식이 올바르지 않습니다.", e);
        }
    }

    @Override
    public String get(String url) throws IOException {
        try {
            return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header(APP_HEADER, APP_ID).GET().build());
        } catch (IllegalArgumentException e) {
            throw new IOException("AI 서버 주소 형식이 올바르지 않습니다.", e);
        }
    }

    private String send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == QUOTA_STATUS) throw new IOException(QUOTA_MESSAGE);
            if (response.statusCode() / 100 != 2) {
                throw new IOException("AI 서버 응답 오류 (HTTP " + response.statusCode() + ")");
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("AI 요청이 중단되었습니다.", e);
        }
    }
}

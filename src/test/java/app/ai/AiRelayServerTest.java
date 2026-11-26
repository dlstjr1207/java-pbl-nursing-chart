package app.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AI 중계 서버 테스트. 위쪽 AI 서비스는 가짜 전송으로 바꾸고, 중계 서버는 실제로 127.0.0.1 빈 포트에 연다.
 * 프로그램 쪽은 실제 JdkHttpTransport로 중계 서버에 보낸다. 실제 AI는 부르지 않는다. 키는 가짜 값이다.
 */
class AiRelayServerTest {

    private static final String SECRET = "sk-test-SECRET-1234567890";
    private static final String MODEL = "server-model";

    /** 가짜 위쪽 AI 서비스: 받은 요청을 기록하고 정해진 답을 돌려준다. */
    static final class FakeUpstream implements AiTransport {
        final List<String> urls = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        boolean fail;

        @Override
        public synchronized String post(String url, String apiKey, String jsonBody) throws IOException {
            urls.add(url);
            keys.add(apiKey);
            bodies.add(jsonBody);
            if (fail) throw new IOException("upstream down key=" + apiKey); // 오류 글에 키가 섞여도 밖으로 나가면 안 된다
            return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"안녕하세요\"}}]}";
        }
    }

    /** 날짜를 바꿀 수 있는 시계 */
    static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-26T03:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final FakeUpstream upstream = new FakeUpstream();
    private final MovableClock clock = new MovableClock();
    private final ByteArrayOutputStream logBytes = new ByteArrayOutputStream();
    private final List<String> responses = new ArrayList<>();
    private AiRelayServer relay;

    private String start(int dailyLimit, int maxBody) throws IOException {
        relay = new AiRelayServer(new AiConfig("openai-compatible", SECRET, MODEL, "https://upstream.example/v1"), upstream,
                new AiRelayServer.Settings(0, dailyLimit, maxBody, "127.0.0.1"), clock,
                new PrintStream(logBytes, true, StandardCharsets.UTF_8));
        relay.start();
        return "http://127.0.0.1:" + relay.port();
    }

    @AfterEach
    void stop() {
        if (relay != null) relay.stop();
    }

    private String log() {
        return logBytes.toString(StandardCharsets.UTF_8);
    }

    /** 헤더를 직접 정해 보내는 요청 (프로그램이 아닌 곳에서 보낸 경우) */
    private HttpResponse<String> raw(String url, String body, boolean appHeader) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
        if (appHeader) b.header(AiTransport.APP_HEADER, AiTransport.APP_ID);
        HttpRequest req = body == null ? b.GET().build() : b.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> r = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        responses.add(r.body());
        return r;
    }

    private static final String CHAT = "{\"model\":\"client-model\",\"temperature\":2,\"tools\":[],\"messages\":"
            + "[{\"role\":\"system\",\"content\":\"s\"},{\"role\":\"user\",\"content\":\"학생 기록 내용 비밀\"}]}";

    @Test
    void 프로그램_요청을_키를_붙여_중계하고_모델은_서버_설정으로_고정한다() throws Exception {
        String base = start(300, 64 * 1024);
        AiClient client = new AiClient(AiConfig.relay(base), new JdkHttpTransport());
        assertTrue(client.isEnabled());
        assertEquals("안녕하세요", client.complete("시스템", "사용자"));
        assertEquals(List.of(SECRET), upstream.keys); // 위쪽에는 서버의 키를 붙인다
        assertEquals("https://upstream.example/v1/chat/completions", upstream.urls.get(0));
        JsonObject forwarded = JsonParser.parseString(upstream.bodies.get(0)).getAsJsonObject();
        assertEquals(MODEL, forwarded.get("model").getAsString());
        // 요청의 다른 칸(temperature·tools 등)은 옮기지 않는다
        raw(base + "/chat/completions", CHAT, true);
        JsonObject second = JsonParser.parseString(upstream.bodies.get(1)).getAsJsonObject();
        assertEquals(MODEL, second.get("model").getAsString());
        assertEquals(java.util.Set.of("model", "messages"), second.keySet());
        assertEquals(2, second.getAsJsonArray("messages").size());
        // 연결 확인
        assertEquals(298, AiClient.checkRelay(new JdkHttpTransport(), base + "/v1/"));
    }

    @Test
    void 식별_헤더가_없으면_거절하고_위쪽으로_보내지_않는다() throws Exception {
        String base = start(300, 64 * 1024);
        HttpResponse<String> r = raw(base + "/v1/chat/completions", CHAT, false);
        assertEquals(403, r.statusCode());
        assertTrue(r.body().contains(AiRelayServer.MSG_NO_APP));
        assertEquals(403, raw(base + "/health", null, false).statusCode());
        assertTrue(upstream.bodies.isEmpty());
        assertEquals(300, relay.remainingToday()); // 거절은 사용량에 세지 않는다
    }

    @Test
    void 다른_주소와_큰_본문과_잘못된_형식은_거절한다() throws Exception {
        String base = start(300, 1000);
        assertEquals(404, raw(base + "/v1/models", null, true).statusCode());
        assertEquals(404, raw(base + "/v1/embeddings", CHAT, true).statusCode());
        assertEquals(405, raw(base + "/v1/chat/completions", null, true).statusCode());
        String big = "{\"messages\":[{\"role\":\"user\",\"content\":\"" + "가".repeat(2000) + "\"}]}";
        assertEquals(413, raw(base + "/v1/chat/completions", big, true).statusCode());
        assertEquals(400, raw(base + "/v1/chat/completions", "{\"prompt\":\"x\"}", true).statusCode());
        assertEquals(400, raw(base + "/v1/chat/completions", "{\"messages\":[{\"role\":\"tool\",\"content\":\"x\"}]}", true).statusCode());
        assertTrue(upstream.bodies.isEmpty());
    }

    @Test
    void 하루_상한을_넘으면_오늘_사용량을_모두_썼다고_돌려주고_다음_날_다시_쓴다() throws Exception {
        String base = start(2, 64 * 1024);
        AiClient client = new AiClient(AiConfig.relay(base), new JdkHttpTransport());
        client.complete("s", "u");
        client.complete("s", "u");
        IOException e = assertThrows(IOException.class, () -> client.complete("s", "u"));
        assertEquals("오늘 사용량을 모두 썼습니다", e.getMessage());
        HttpResponse<String> r = raw(base + "/v1/chat/completions", CHAT, true);
        assertEquals(429, r.statusCode());
        assertTrue(r.body().contains("오늘 사용량을 모두 썼습니다"));
        assertEquals(2, upstream.bodies.size());
        assertEquals(0, AiClient.checkRelay(new JdkHttpTransport(), base));
        // 해설은 실패 상태로 끝나고 예외를 던지지 않는다
        assertEquals(AiClient.STATUS_FAILED, client.explain("p").status());
        // 다음 날(서울 기준)에는 다시 쓴다
        clock.now = clock.now.plus(Duration.ofDays(1));
        assertEquals("안녕하세요", client.complete("s", "u"));
    }

    @Test
    void 키와_요청_내용은_응답과_접속_기록에_나오지_않는다() throws Exception {
        String base = start(300, 64 * 1024);
        raw(base + "/v1/chat/completions", CHAT, true); // 성공
        upstream.fail = true;
        HttpResponse<String> failed = raw(base + "/v1/chat/completions", CHAT, true); // 위쪽 실패 (오류 글에 키가 있음)
        assertEquals(502, failed.statusCode());
        raw(base + "/v1/chat/completions", CHAT, false); // 거절
        raw(base + "/health", null, true);
        for (String body : responses) {
            assertFalse(body.contains(SECRET), body);
            assertFalse(body.contains("upstream.example"), body);
        }
        String log = log();
        assertFalse(log.contains(SECRET), log);
        assertFalse(log.contains("학생 기록 내용"), log);
        // 접속 기록은 시각·요청 수·성공/실패만
        assertTrue(log.contains("2026-11-26 12:00:00 오늘 1/300 성공"), log);
        assertTrue(log.contains("오늘 2/300 실패"), log);
        assertTrue(log.contains("거절(식별 헤더 없음)"), log);
        assertFalse(relay.toString().contains(SECRET));
    }

    @Test
    void 키_없는_설정으로는_중계_서버를_켜지_않는다() {
        assertThrows(IllegalArgumentException.class, () -> new AiRelayServer(AiConfig.disabled(), upstream,
                AiRelayServer.Settings.defaults(), clock, System.out));
        assertThrows(IllegalArgumentException.class, () -> new AiRelayServer(AiConfig.relay("http://x"), upstream,
                AiRelayServer.Settings.defaults(), clock, System.out));
    }

    @Test
    void 중계_서버_설정은_파일에서_읽고_없거나_틀리면_기본값이다(@TempDir Path dir) throws IOException {
        assertEquals(AiRelayServer.Settings.defaults(), AiRelayServer.Settings.load(dir.resolve("없음.properties")));
        assertEquals(300, AiRelayServer.Settings.defaults().dailyLimit());
        assertEquals("127.0.0.1", AiRelayServer.Settings.defaults().bindHost());
        Path f = dir.resolve("relay.properties");
        Files.writeString(f, "relay.port=9000\nrelay.dailyLimit=50\nrelay.maxBodyBytes=abc\n");
        AiRelayServer.Settings s = AiRelayServer.Settings.load(f);
        assertEquals(9000, s.port());
        assertEquals(50, s.dailyLimit());
        assertEquals(AiRelayServer.Settings.DEFAULT_MAX_BODY, s.maxBodyBytes());
    }

    // ---- 프로그램 쪽: AI 서버 주소 ----

    @Test
    void 주소가_없으면_AI가_꺼지고_기본_케이스로_동작한다(@TempDir Path dir) {
        AiClient client = new AiClient(AiConfig.disabled(), new JdkHttpTransport());
        AiConnection c = new AiConnection(client, AiConfig.disabled(),
                new AiServerSettings(dir.resolve(AiServerSettings.FILE_NAME)), new JdkHttpTransport());
        assertEquals(AiConnection.STATUS_OFF, c.connect());
        assertFalse(client.isEnabled());
        assertEquals(AiClient.STATUS_NONE, client.explain("p").status());
    }

    @Test
    void 연결되지_않는_주소면_AI만_꺼진다(@TempDir Path dir) throws IOException {
        int closed;
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            closed = s.getLocalPort(); // 닫힌 포트
        }
        AiClient client = new AiClient(AiConfig.disabled(), new JdkHttpTransport());
        AiConnection c = new AiConnection(client, AiConfig.disabled(),
                new AiServerSettings(dir.resolve(AiServerSettings.FILE_NAME)), new JdkHttpTransport());
        assertEquals(AiConnection.STATUS_RELAY_FAIL, c.saveAndConnect("http://127.0.0.1:" + closed));
        assertFalse(client.isEnabled());
        assertTrue(c.detail().contains("기본 케이스와 규칙 점검만"));
    }

    @Test
    void 주소를_저장하면_다음_실행에도_기억하고_연결되면_AI가_켜진다(@TempDir Path dir) throws Exception {
        String base = start(300, 64 * 1024);
        Path file = dir.resolve(AiServerSettings.FILE_NAME);
        AiClient client = new AiClient(AiConfig.disabled(), new JdkHttpTransport());
        AiConnection c = new AiConnection(client, AiConfig.disabled(), new AiServerSettings(file), new JdkHttpTransport());
        assertEquals(AiConnection.STATUS_RELAY_OK, c.saveAndConnect(base));
        assertTrue(client.isEnabled());
        assertTrue(client.config().isRelay());
        assertTrue(c.detail().contains("오늘 남은 사용량 300회"), c.detail());
        assertFalse(Files.readString(file).contains("apiKey")); // 주소만 저장
        // 다음 실행
        AiClient again = new AiClient(AiConfig.disabled(), new JdkHttpTransport());
        AiConnection c2 = new AiConnection(again, AiConfig.disabled(), new AiServerSettings(file), new JdkHttpTransport());
        assertEquals(base, c2.serverUrl());
        assertEquals(AiConnection.STATUS_RELAY_OK, c2.connect());
        assertEquals("안녕하세요", again.complete("s", "u"));
        // 지우면 꺼진다
        c2.saveAndConnect("");
        assertFalse(Files.exists(file));
        assertFalse(again.isEnabled());
    }

    @Test
    void 키_파일이_있으면_기존처럼_키를_쓴다(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(AiServerSettings.FILE_NAME), "ai.serverUrl=http://127.0.0.1:1\n");
        AiConfig key = new AiConfig("openai-compatible", SECRET, MODEL, "https://upstream.example/v1");
        AiClient client = new AiClient(AiConfig.disabled(), upstream);
        AiConnection c = new AiConnection(client, key, new AiServerSettings(dir.resolve(AiServerSettings.FILE_NAME)), upstream);
        assertTrue(c.usesKeyFile());
        assertEquals(AiConnection.STATUS_KEY, c.connect());
        assertEquals("안녕하세요", client.complete("s", "u"));
        assertEquals(List.of(SECRET), upstream.keys);
        assertEquals("https://upstream.example/v1/chat/completions", upstream.urls.get(0));
    }

    @Test
    void 주소_형식을_검사한다() {
        assertNull(AiServerSettings.problem(""));
        assertNull(AiServerSettings.problem("https://abc-def.trycloudflare.com"));
        assertNull(AiServerSettings.problem("http://127.0.0.1:8787"));
        assertTrue(AiServerSettings.problem("abc.trycloudflare.com").contains("https://"));
        assertTrue(AiServerSettings.problem("https://a b.com").contains("공백"));
        assertEquals("https://x.com", AiClient.serverRoot("https://x.com/v1/chat/completions"));
        assertEquals("https://x.com", AiClient.serverRoot("https://x.com/"));
    }

    @Test
    void 중계_방식은_키를_보내지_않는다() throws IOException {
        FakeUpstream f = new FakeUpstream();
        AiClient client = new AiClient(AiConfig.relay("https://relay.example/"), f);
        client.complete("s", "u");
        assertEquals(List.of(""), f.keys);
        assertEquals("https://relay.example/v1/chat/completions", f.urls.get(0));
        assertFalse(AiConfig.relay("https://relay.example").toString().contains(SECRET));
    }
}

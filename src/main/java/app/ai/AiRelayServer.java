package app.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 중계 서버 (맥미니에서 실행). 팀원 PC의 프로그램이 보낸 AI 요청(OpenAI 호환 chat completions)을 받아
 * config/ai.properties의 키를 붙여 실제 AI 서비스로 보내고 응답을 돌려준다. 키는 이 서버에만 있다.
 * <ul>
 *   <li>받는 요청: POST /v1/chat/completions (또는 /chat/completions), GET /health. 그 밖은 404.</li>
 *   <li>앱 식별 헤더({@link AiTransport#APP_HEADER})가 없으면 403.</li>
 *   <li>요청 본문은 최대 크기를 넘으면 413. 모델은 서버 설정으로 고정하고, 요청에서는 messages만 옮긴다.</li>
 *   <li>하루 전체 요청 수가 상한(기본 300회)을 넘으면 429 "오늘 사용량을 모두 썼습니다".</li>
 *   <li>키는 출력·응답·로그에 넣지 않는다. 접속 기록에는 시각·오늘 요청 수·성공/실패만 남기고 요청 내용은 남기지 않는다.</li>
 * </ul>
 * 기본으로 127.0.0.1에만 열어(같은 맥의 Cloudflare 터널만 접속) 같은 와이파이의 다른 PC에서 바로 들어오지 못하게 한다.
 * 실행: java -cp nursing-chart-trainer.jar app.ai.AiRelayServer (config/가 있는 폴더에서)
 */
public final class AiRelayServer {

    /** 중계 서버 설정 파일 (없으면 기본값). config/ 안이라 Git에 올라가지 않는다. */
    public static final Path DEFAULT_SETTINGS = Path.of("config", "relay.properties");

    /** 중계 서버 설정: 포트, 하루 상한, 본문 최대 크기(바이트), 여는 주소. */
    public record Settings(int port, int dailyLimit, int maxBodyBytes, String bindHost) {
        public static final int DEFAULT_PORT = 8787;
        public static final int DEFAULT_DAILY_LIMIT = 300;
        /** 케이스 생성 지시와 재요청(이전 응답 포함)이 수십 KB라 넉넉히 둔다. */
        public static final int DEFAULT_MAX_BODY = 256 * 1024;

        public static Settings defaults() {
            return new Settings(DEFAULT_PORT, DEFAULT_DAILY_LIMIT, DEFAULT_MAX_BODY, "127.0.0.1");
        }

        /** relay.port, relay.dailyLimit, relay.maxBodyBytes, relay.bindHost를 읽는다. 없거나 잘못되면 기본값. */
        public static Settings load(Path path) {
            Settings d = defaults();
            if (path == null || !Files.isRegularFile(path)) return d;
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                return d;
            }
            String host = p.getProperty("relay.bindHost", d.bindHost()).trim();
            return new Settings(num(p, "relay.port", d.port()), num(p, "relay.dailyLimit", d.dailyLimit()),
                    num(p, "relay.maxBodyBytes", d.maxBodyBytes()), host.isEmpty() ? d.bindHost() : host);
        }

        private static int num(Properties p, String key, int def) {
            try {
                int v = Integer.parseInt(p.getProperty(key, "").trim());
                return v > 0 ? v : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }
    }

    static final String MSG_NO_APP = "이 프로그램에서 보낸 요청이 아닙니다";
    static final String MSG_TOO_LARGE = "요청이 너무 큽니다";
    static final String MSG_BAD_REQUEST = "요청 형식이 올바르지 않습니다";
    static final String MSG_UPSTREAM = "AI 서비스 응답에 실패했습니다";

    private final AiConfig upstream;
    private final AiTransport transport;
    private final Settings settings;
    private final Clock clock;
    private final PrintStream log;
    private HttpServer server;
    private ExecutorService pool;

    private LocalDate day;
    private int usedToday;

    public AiRelayServer(AiConfig upstream, AiTransport transport, Settings settings, Clock clock, PrintStream log) {
        if (upstream == null || !upstream.isEnabled() || upstream.isRelay()) {
            throw new IllegalArgumentException("config/ai.properties에 키·모델·접속 주소가 모두 있어야 중계 서버를 켤 수 있습니다.");
        }
        this.upstream = upstream;
        this.transport = transport;
        this.settings = settings;
        this.clock = clock;
        this.log = log;
    }

    /** 서버를 연다. 포트가 0이면 빈 포트를 고른다(테스트용). */
    public synchronized void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(settings.bindHost(), settings.port()), 0);
        server.createContext("/", this::handle);
        pool = Executors.newFixedThreadPool(4);
        server.setExecutor(pool);
        server.start();
    }

    /** 서버를 닫는다. */
    public synchronized void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    /** 실제로 열린 포트 */
    public int port() {
        return server.getAddress().getPort();
    }

    /** 오늘 남은 요청 수 */
    public synchronized int remainingToday() {
        rollDay();
        return Math.max(0, settings.dailyLimit() - usedToday);
    }

    private void rollDay() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            usedToday = 0;
        }
    }

    /** 오늘 상한 안이면 1회 쓰고 몇 번째인지, 넘었으면 -1. */
    private synchronized int acquire() {
        rollDay();
        if (usedToday >= settings.dailyLimit()) return -1;
        return ++usedToday;
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            boolean app = AiTransport.APP_ID.equals(ex.getRequestHeaders().getFirst(AiTransport.APP_HEADER));
            if ("GET".equals(method) && (path.equals("/health") || path.equals("/v1/health"))) {
                if (!app) {
                    reply(ex, 403, error(MSG_NO_APP));
                    return;
                }
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("remainingToday", remainingToday());
                o.addProperty("dailyLimit", settings.dailyLimit());
                reply(ex, 200, o.toString());
                return;
            }
            if (!path.equals("/v1/chat/completions") && !path.equals("/chat/completions")) {
                reply(ex, 404, error("없는 주소입니다"));
                return;
            }
            if (!"POST".equals(method)) {
                reply(ex, 405, error("POST만 받습니다"));
                return;
            }
            if (!app) {
                record("거절(식별 헤더 없음)", 0);
                reply(ex, 403, error(MSG_NO_APP));
                return;
            }
            byte[] body = readLimited(ex.getRequestBody(), settings.maxBodyBytes());
            if (body == null) {
                record("거절(본문 크기 초과)", 0);
                reply(ex, 413, error(MSG_TOO_LARGE));
                return;
            }
            JsonArray messages = messages(body);
            if (messages == null) {
                record("거절(형식 오류)", 0);
                reply(ex, 400, error(MSG_BAD_REQUEST));
                return;
            }
            int n = acquire();
            if (n < 0) {
                record("거절(오늘 상한 " + settings.dailyLimit() + "회 초과)", settings.dailyLimit());
                reply(ex, JdkHttpTransport.QUOTA_STATUS, error(JdkHttpTransport.QUOTA_MESSAGE));
                return;
            }
            JsonObject forward = new JsonObject();
            forward.addProperty("model", upstream.model()); // 모델은 서버 설정으로 고정
            forward.add("messages", messages);
            String answer;
            try {
                answer = transport.post(upstreamEndpoint(), upstream.apiKey(), forward.toString());
            } catch (IOException | RuntimeException e) {
                record("실패", n);
                reply(ex, 502, error(MSG_UPSTREAM)); // 위쪽 오류 내용은 돌려주지 않는다
                return;
            }
            record("성공", n);
            reply(ex, 200, answer);
        } catch (RuntimeException e) {
            // 요청 내용·키를 출력하지 않는다
            log.println(now() + " 오류 " + e.getClass().getSimpleName());
        }
    }

    /** messages 배열만 꺼낸다 (각 항목은 role·content 문자열). 형식이 틀리면 null. */
    static JsonArray messages(byte[] body) {
        try {
            JsonObject o = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray in = o.getAsJsonArray("messages");
            if (in == null || in.isEmpty()) return null;
            JsonArray out = new JsonArray();
            for (JsonElement e : in) {
                JsonObject m = e.getAsJsonObject();
                String role = m.get("role").getAsString();
                if (!role.equals("system") && !role.equals("user") && !role.equals("assistant")) return null;
                JsonObject copy = new JsonObject();
                copy.addProperty("role", role);
                copy.addProperty("content", m.get("content").getAsString());
                out.add(copy);
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 최대 크기까지 읽는다. 넘으면 null. */
    static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > max) return null;
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private String upstreamEndpoint() {
        String base = upstream.baseUrl();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    /** 접속 기록 한 줄: 시각, 오늘 요청 수/상한, 결과. 요청 내용은 남기지 않는다. */
    private void record(String result, int n) {
        log.println(now() + " 오늘 " + n + "/" + settings.dailyLimit() + " " + result);
    }

    private String now() {
        return LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String error(String message) {
        JsonObject e = new JsonObject();
        e.addProperty("message", message);
        JsonObject o = new JsonObject();
        o.add("error", e);
        return o.toString();
    }

    private static void reply(HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    /** 맥미니에서 실행한다. config/가 있는 폴더(프로젝트 폴더)에서 실행해야 키를 읽는다. */
    public static void main(String[] args) throws IOException {
        AiConfig config = AiConfig.load(AiConfig.DEFAULT_PATH);
        if (!config.isEnabled() || config.isRelay()) {
            System.err.println("config/ai.properties에 ai.apiKey·ai.model·ai.baseUrl이 모두 있어야 합니다. (이 폴더에서 실행했는지 확인하세요)");
            System.exit(1);
        }
        Settings s = Settings.load(DEFAULT_SETTINGS);
        // 위쪽 AI 응답을 오래 기다릴 수 있게 응답 제한 시간을 길게 둔다 (Cloudflare 터널은 약 100초에서 끊는다)
        AiRelayServer relay = new AiRelayServer(config, new JdkHttpTransport(Duration.ofSeconds(10), Duration.ofSeconds(95)),
                s, Clock.systemDefaultZone(), System.out);
        relay.start();
        System.out.println("AI 중계 서버를 켰습니다: http://" + s.bindHost() + ":" + relay.port()
                + " (모델은 config/ai.properties 값, 하루 상한 " + s.dailyLimit() + "회, 본문 최대 " + s.maxBodyBytes() / 1024 + "KB)");
        System.out.println("끄려면 Ctrl+C 또는 scripts/share-stop.sh");
        Runtime.getRuntime().addShutdownHook(new Thread(relay::stop));
    }
}

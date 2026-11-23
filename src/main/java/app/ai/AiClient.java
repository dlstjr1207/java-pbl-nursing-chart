package app.ai;

import app.domain.AiFeedback;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 해설·생성 요청 클라이언트 (OpenAI 호환 chat completions).
 * 점수에는 관여하지 않는다. 실패해도 예외를 던지지 않고 상태(FAILED/NONE)만 돌려줘 기존 결과가 유지된다.
 */
public final class AiClient {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_NONE = "NONE";

    private final AiConfig config;
    private final AiTransport transport;

    public AiClient(AiConfig config, AiTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    /** 키가 없으면 false. 이때 AI 기능(해설·생성)은 꺼진다. */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * 학생 기록·점검 결과 요약으로 해설을 요청한다.
     * 키 없음 → NONE, 통신 실패·형식 오류 → FAILED, 정상 → SUCCESS.
     */
    public AiFeedback explain(String prompt) {
        if (!isEnabled()) {
            return new AiFeedback(STATUS_NONE, null, null, null);
        }
        try {
            String content = complete(
                    "너는 간호학생의 기록을 돕는 교육용 조언자다. 점수는 매기지 않는다. 반드시 JSON 객체 하나만 답한다. "
                            + "키는 strengths(문자열 배열), improvements(문자열 배열), example_rewrite(문자열)이다.",
                    prompt);
            return parseFeedback(content);
        } catch (IOException | RuntimeException e) {
            return new AiFeedback(STATUS_FAILED, null, null, null);
        }
    }

    /**
     * 시스템·사용자 지시를 보내 응답 본문 문자열을 돌려준다 (케이스 생성 등에서 재사용).
     *
     * @throws IOException 통신 실패 또는 응답 구조가 chat completions 형식이 아님
     */
    public String complete(String system, String user) throws IOException {
        if (!isEnabled()) {
            throw new IOException("AI 키가 설정되지 않았습니다.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", user));
        body.add("messages", messages);
        String raw = transport.post(endpoint(), config.apiKey(), body.toString());
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            return root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        } catch (RuntimeException e) {
            throw new IOException("AI 응답 형식이 올바르지 않습니다.", e);
        }
    }

    /** 해설 JSON(strengths, improvements, example_rewrite)을 검증해 변환한다. 코드 펜스는 벗긴다. */
    static AiFeedback parseFeedback(String content) {
        // 앞뒤 설명문·코드 펜스가 붙어도 JSON 객체 부분만 꺼내 읽는다
        String json = content == null ? "" : content;
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("해설 응답에 JSON 객체가 없음");
        json = json.substring(start, end + 1);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        List<String> strengths = stringList(obj, "strengths");
        List<String> improvements = stringList(obj, "improvements");
        JsonElement rewrite = obj.get("example_rewrite");
        if (rewrite == null || !rewrite.isJsonPrimitive()) {
            throw new IllegalArgumentException("example_rewrite 누락");
        }
        return new AiFeedback(STATUS_SUCCESS, strengths, improvements, rewrite.getAsString());
    }

    private static List<String> stringList(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonArray()) {
            throw new IllegalArgumentException(key + " 누락 또는 배열 아님");
        }
        List<String> list = new ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonPrimitive()) {
                throw new IllegalArgumentException(key + " 항목이 문자열이 아님");
            }
            list.add(item.getAsString());
        }
        return list;
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    /** baseUrl 뒤에 /chat/completions를 붙인다. */
    String endpoint() {
        String base = config.baseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }
}

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

    /** 해설 지시: 교육용이라 없는 사실을 보여 주면 안 된다 (시연 3회차에서 지어낸 발언이 나온 뒤 추가). */
    public static final String NO_FABRICATION = "케이스와 학생 기록에 없는 환자 발언·수치·사건, 간호 조치(예: 격리·강박·추가 투약)를 만들지 말 것. "
            + "예시 문장(example_rewrite)에는 케이스의 실제 발언만 인용할 것. 사실이 부족하면 [확인 필요]라고 쓸 것.";

    /** 해설 요청의 시스템 지시. 문자열 안 따옴표는 이스케이프하게 한다(실제 발언 인용 때 JSON이 깨지지 않게). */
    private static final String EXPLAIN_SYSTEM = "너는 간호학생의 기록을 돕는 교육용 조언자다. 점수는 매기지 않는다. 반드시 JSON 객체 하나만 답한다. "
            + "키는 strengths(문자열 배열), improvements(문자열 배열), example_rewrite(문자열)이다. "
            + "문자열 안에서 환자 발언을 인용할 때 큰따옴표는 \\\"처럼 이스케이프한다. " + NO_FABRICATION;

    /** 해설 응답을 읽지 못했을 때 다시 보내는 최대 횟수 (첫 요청 포함 2번). */
    static final int EXPLAIN_TRIES = 2;

    /**
     * 학생 기록·점검 결과 요약으로 해설을 요청한다.
     * 키 없음 → NONE, 정상 → SUCCESS. 통신 실패·형식 오류면 한 번 더 요청하고, 두 번 다 실패하면 FAILED.
     */
    public AiFeedback explain(String prompt) {
        if (!isEnabled()) {
            return new AiFeedback(STATUS_NONE, null, null, null);
        }
        for (int i = 0; i < EXPLAIN_TRIES; i++) {
            try {
                return parseFeedback(complete(EXPLAIN_SYSTEM, prompt));
            } catch (IOException | RuntimeException e) {
                // 다음 시도로 넘어간다. 실패 내용(응답 본문·키)은 출력하지 않는다.
            }
        }
        return new AiFeedback(STATUS_FAILED, null, null, null);
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
        JsonObject obj;
        try {
            obj = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            // 문자열 안에 이스케이프하지 않은 따옴표("누가 나를 …")가 섞인 응답을 고쳐서 한 번 더 읽는다
            obj = JsonParser.parseString(escapeInnerQuotes(json)).getAsJsonObject();
        }
        List<String> strengths = stringList(obj, "strengths");
        List<String> improvements = stringList(obj, "improvements");
        JsonElement rewrite = obj.get("example_rewrite");
        if (rewrite == null || !rewrite.isJsonPrimitive()) {
            throw new IllegalArgumentException("example_rewrite 누락");
        }
        return new AiFeedback(STATUS_SUCCESS, strengths, improvements, rewrite.getAsString());
    }

    /**
     * JSON 문자열 값 안의 이스케이프되지 않은 큰따옴표를 \\"로 바꾼다. 문자열 안에서 만난 따옴표 뒤에
     * (공백을 건너뛰고) , : ] } 가 오거나 글이 끝나면 문자열의 끝으로 보고, 아니면 본문 속 따옴표로 본다.
     */
    static String escapeInnerQuotes(String json) {
        StringBuilder out = new StringBuilder(json.length() + 16);
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString && ch == '\\') { // 이미 이스케이프된 문자는 그대로
                out.append(ch);
                if (i + 1 < json.length()) out.append(json.charAt(++i));
                continue;
            }
            if (ch == '"') {
                if (!inString) {
                    inString = true;
                } else {
                    int j = i + 1;
                    while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
                    char next = j < json.length() ? json.charAt(j) : '}';
                    if (next == ',' || next == ':' || next == ']' || next == '}') {
                        inString = false;
                    } else {
                        out.append('\\');
                    }
                }
            }
            out.append(ch);
        }
        return out.toString();
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

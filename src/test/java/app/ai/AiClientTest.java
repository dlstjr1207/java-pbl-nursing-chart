package app.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.domain.AiFeedback;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AiClient 검증. 실제 AI를 호출하지 않고 가짜 전송 구현을 쓴다 (S7). */
class AiClientTest {

    private static final AiConfig ON = new AiConfig("test", "fake-key", "fake-model", "http://fake.local/v1/");

    /** 정해 둔 응답(또는 예외)을 돌려주고 요청을 기록하는 가짜 전송 */
    private static final class FakeTransport implements AiTransport {
        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        String response;
        IOException failure;

        @Override
        public String post(String url, String apiKey, String jsonBody) throws IOException {
            urls.add(url);
            bodies.add(jsonBody);
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    private static String wrap(String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "assistant");
        msg.addProperty("content", content);
        JsonObject choice = new JsonObject();
        choice.add("message", msg);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject root = new JsonObject();
        root.add("choices", choices);
        return root.toString();
    }

    @Test
    void 성공_응답은_해설로_변환되고_요청_형식이_맞다() {
        FakeTransport t = new FakeTransport();
        t.response = wrap("{\"strengths\":[\"관찰이 구체적\"],\"improvements\":[\"시각 보완\"],\"example_rewrite\":\"예시 문장\"}");
        AiFeedback fb = new AiClient(ON, t).explain("합성 기록");
        assertEquals("SUCCESS", fb.status());
        assertEquals(List.of("관찰이 구체적"), fb.strengths());
        assertEquals("예시 문장", fb.exampleRewrite());
        assertEquals("http://fake.local/v1/chat/completions", t.urls.get(0));
        JsonObject sent = JsonParser.parseString(t.bodies.get(0)).getAsJsonObject();
        assertEquals("fake-model", sent.get("model").getAsString());
        assertEquals(2, sent.getAsJsonArray("messages").size());
    }

    @Test
    void 코드펜스로_감싼_JSON도_받는다() {
        FakeTransport t = new FakeTransport();
        t.response = wrap("```json\n{\"strengths\":[],\"improvements\":[\"a\"],\"example_rewrite\":\"b\"}\n```");
        assertEquals("SUCCESS", new AiClient(ON, t).explain("x").status());
    }

    @Test
    void 통신_실패는_FAILED로_남기고_예외를_던지지_않는다() {
        FakeTransport t = new FakeTransport();
        t.failure = new IOException("시간 초과");
        AiFeedback fb = new AiClient(ON, t).explain("x");
        assertEquals("FAILED", fb.status());
        assertTrue(fb.strengths().isEmpty());
    }

    @Test
    void 잘못된_JSON과_필수키_누락은_FAILED() {
        String[] bad = {
            "JSON이 아님",
            "{\"strengths\":[\"a\"],\"improvements\":[\"b\"]}",
            "{\"strengths\":\"문자열\",\"improvements\":[],\"example_rewrite\":\"e\"}",
            "{\"strengths\":[1,{}],\"improvements\":[],\"example_rewrite\":\"e\"}",
        };
        for (String content : bad) {
            FakeTransport t = new FakeTransport();
            t.response = wrap(content);
            assertEquals("FAILED", new AiClient(ON, t).explain("x").status(), content);
        }
    }

    @Test
    void 응답_구조가_다르면_FAILED() {
        FakeTransport t = new FakeTransport();
        t.response = "{\"error\":\"x\"}";
        assertEquals("FAILED", new AiClient(ON, t).explain("x").status());
        t.response = "전혀 JSON 아님";
        assertEquals("FAILED", new AiClient(ON, t).explain("x").status());
    }

    @Test
    void 키가_없으면_해설_비활성이고_요청도_보내지_않는다() {
        FakeTransport t = new FakeTransport();
        AiClient client = new AiClient(AiConfig.disabled(), t);
        assertFalse(client.isEnabled());
        assertEquals("NONE", client.explain("x").status());
        assertTrue(t.urls.isEmpty());
    }

    @Test
    void 설정_파일을_읽고_없으면_꺼진다(@TempDir Path dir) throws IOException {
        assertFalse(AiConfig.load(dir.resolve("없음.properties")).isEnabled());
        Path file = dir.resolve("ai.properties");
        Files.writeString(file, "ai.provider=p\nai.apiKey=fake\nai.model=m\nai.baseUrl=http://x\n");
        AiConfig cfg = AiConfig.load(file);
        assertTrue(cfg.isEnabled());
        assertFalse(cfg.toString().contains("fake"));
        Files.writeString(file, "ai.provider=p\nai.apiKey=\nai.model=m\nai.baseUrl=http://x\n");
        assertFalse(AiConfig.load(file).isEnabled());
    }
}

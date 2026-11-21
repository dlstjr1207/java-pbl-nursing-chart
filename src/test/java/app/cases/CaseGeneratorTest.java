package app.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.AiTransport;
import app.domain.Attempt;
import app.domain.CaseSource;
import app.domain.DomainJson;
import app.domain.PatientCase;
import app.storage.InMemoryAttemptRepository;
import app.ui.AppState;
import app.ui.HomeController;
import app.ui.Screen;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T-31: CaseGenerator 검증 (S9). 실제 AI를 호출하지 않고 가짜 응답만 쓴다. 약물·값은 합성이다. */
class CaseGeneratorTest {

    private static final AiConfig ON = new AiConfig("test", "fake-key", "fake-model", "http://fake.local/v1");
    private static final AllowedDrugs ALLOWED = AllowedDrugs.load(
            CaseGeneratorTest.class.getClassLoader(), "cases/allowed-drugs-test.json");

    /** 준비한 응답을 차례로 돌려주는 가짜 전송 (다 쓰면 마지막 응답 반복) */
    private static final class Fake implements AiTransport {
        final List<String> contents = new ArrayList<>();
        final List<String> prompts = new ArrayList<>();
        boolean fail;
        int calls;

        @Override
        public String post(String url, String apiKey, String body) throws IOException {
            prompts.add(body);
            calls++;
            if (fail) throw new IOException("가짜 통신 실패");
            String c = contents.get(Math.min(calls - 1, contents.size() - 1));
            JsonObject msg = new JsonObject();
            msg.addProperty("content", c);
            JsonObject choice = new JsonObject();
            choice.add("message", msg);
            JsonArray choices = new JsonArray();
            choices.add(choice);
            JsonObject root = new JsonObject();
            root.add("choices", choices);
            return root.toString();
        }
    }

    private static String caseJson(String drug, String dose) {
        return """
            {"id":"GEN-1","title":"합성 생성","source":"FIXED",
             "patient":{"name":"가상환자"},"briefing":"상황",
             "vitals":[{"time":"09:30","pulse":80}],
             "orders":[{"orderId":"O1","type":"REGULAR","drugName":"%s","dose":"%s","unit":"mg","route":"PO","scheduleTimes":["08:00"]}]}
            """.formatted(drug, dose);
    }

    private static CaseGenerator generator(AiConfig cfg, Fake f, AllowedDrugs allowed, int max) {
        return new CaseGenerator(new AiClient(cfg, f), new CaseValidator(allowed),
                new CaseLoader(CaseGeneratorTest.class.getClassLoader()), max);
    }

    @Test
    void 정상_응답은_AI_생성_케이스로_쓴다() {
        Fake f = new Fake();
        f.contents.add("```json\n" + caseJson("테스트약A", "3") + "\n```");
        CaseGenerator.Result r = generator(ON, f, ALLOWED, 3).generate();
        assertTrue(r.generated());
        assertEquals(CaseSource.AI_GENERATED, r.patientCase().source()); // 응답이 FIXED라 해도 고정
        assertNull(r.patientCase().answerKey());
        assertEquals(1, f.calls);
        assertTrue(f.prompts.get(0).contains("테스트약A")); // 허용 약물을 요청에 보냄
    }

    @Test
    void 위반_응답은_버리고_다시_요청해_통과하면_쓴다() {
        Fake f = new Fake();
        f.contents.add(caseJson("없는약", "3"));      // 목록 밖 약물
        f.contents.add(caseJson("테스트약A", "99"));  // 용량 범위 밖
        f.contents.add(caseJson("테스트약A", "3"));
        CaseGenerator.Result r = generator(ON, f, ALLOWED, 3).generate();
        assertTrue(r.generated());
        assertEquals(3, f.calls);
        assertTrue(f.prompts.get(1).contains("허용 약물 목록에 없음")); // 위반 이유를 알려 줌
    }

    @Test
    void 형식_오류와_필수_항목_누락은_한도_후_기본_케이스로_돌아간다() {
        Fake f = new Fake();
        f.contents.add("JSON이 아님");
        f.contents.add("{\"title\":\"x\"}");
        CaseGenerator.Result r = generator(ON, f, ALLOWED, 2).generate();
        assertFalse(r.generated());
        assertEquals(2, f.calls);
        assertEquals("demo", r.patientCase().id()); // PC-01이 없으면 DEMO (테스트 클래스패스에 PC-01 없음)
        assertTrue(r.message().contains("기본 케이스"));
        assertFalse(r.problems().isEmpty());
    }

    @Test
    void 통신_실패도_한도_후_기본_케이스로_돌아간다() {
        Fake f = new Fake();
        f.fail = true;
        CaseGenerator.Result r = generator(ON, f, ALLOWED, 2).generate();
        assertFalse(r.generated());
        assertEquals(2, f.calls);
    }

    @Test
    void 키가_없거나_허용_약물이_비어_있으면_호출_없이_기본_케이스다() {
        Fake f = new Fake();
        CaseGenerator noKey = generator(AiConfig.disabled(), f, ALLOWED, 3);
        assertFalse(noKey.available());
        assertFalse(noKey.generate().generated());
        CaseGenerator noDrugs = generator(ON, f, new AllowedDrugs(List.of()), 3);
        assertFalse(noDrugs.available());
        assertTrue(noDrugs.unavailableReason().contains("허용 약물"));
        assertFalse(noDrugs.generate().generated());
        assertEquals(0, f.calls);
    }

    @Test
    void 케이스를_JSON_파일로_내보내고_다시_읽는다(@TempDir Path dir) throws IOException {
        Fake f = new Fake();
        f.contents.add(caseJson("테스트약A", "3"));
        PatientCase c = generator(ON, f, ALLOWED, 1).generate().patientCase();
        Path file = dir.resolve("PC-01.json");
        CaseGenerator.export(c, file);
        PatientCase back = new CaseLoader().parse(Files.readString(file), "export");
        assertEquals(c, back);
    }

    @Test
    void 생성_케이스는_실습_기록에_원문이_저장되어_재현된다() {
        Fake f = new Fake();
        f.contents.add(caseJson("테스트약A", "3"));
        AppState state = new AppState();
        state.login("테스트");
        HomeController home = new HomeController(state, new CaseLoader(CaseGeneratorTest.class.getClassLoader()),
                generator(ON, f, ALLOWED, 3));
        assertTrue(home.aiGenerationAvailable());
        assertNull(home.startGeneratedPractice());
        assertEquals(Screen.PATIENT, state.current());
        assertTrue(state.notices().contains(AppState.AI_NOTICE));

        // 저장소를 거쳐 원문에서 같은 환자를 되살린다.
        PatientCase used = state.currentCase();
        InMemoryAttemptRepository repo = new InMemoryAttemptRepository();
        Attempt saved = repo.save(new Attempt(null, 1L, used.id(), used.source(), DomainJson.toJson(used),
                java.time.LocalDateTime.now(), null, null, null, null, null, null, null));
        assertEquals(used, CaseGenerator.restore(repo.findById(1L, saved.attemptId()).orElseThrow()));
        assertNull(CaseGenerator.restore(new Attempt(null, 1L, "demo", CaseSource.DEMO, null,
                java.time.LocalDateTime.now(), null, null, null, null, null, null, null)));
    }

    @Test
    void 생성이_실패하면_기본_케이스로_시작하고_안내를_준다() {
        Fake f = new Fake();
        f.contents.add("망가진 응답");
        AppState state = new AppState();
        state.login("테스트");
        HomeController home = new HomeController(state, new CaseLoader(CaseGeneratorTest.class.getClassLoader()),
                generator(ON, f, ALLOWED, 2));
        String notice = home.startGeneratedPractice();
        assertNotNull(notice);
        assertEquals("demo", state.currentCase().id());
        assertEquals(Screen.PATIENT, state.current());
    }
}

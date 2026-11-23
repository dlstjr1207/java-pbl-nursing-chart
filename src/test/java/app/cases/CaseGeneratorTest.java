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

    /**
     * 합성 생성 응답. 정답 키(장소·발언·투약)와 브리핑이 맞고, 구조화된 모범 기록이 자기 정답 키로 100점이다.
     * 약명은 한글(테스트약A)·영어(TestDrugA) 둘 다, 경로는 PO|경구.
     */
    private static String caseJson(String drug, String dose) {
        return """
            {"id":"GEN-1","title":"합성 생성","source":"FIXED",
             "patient":{"name":"가상환자","age":35,"room":"합성-1호","diagnosis":"합성 진단"},
             "briefing":"08:00 데이룸에서 서성이며 \\"무서워요\\"라고 말함.",
             "vitals":[{"time":"09:30","pulse":80},{"time":"10:30","pulse":82}],
             "observationLog":[{"time":"09:00","shift":"DAY","tags":["기타"],"narrative":"합성 관찰"},
                               {"time":"10:00","shift":"DAY","tags":["기타"],"narrative":"합성 관찰"}],
             "orders":[{"orderId":"O1","type":"REGULAR","drugName":"%1$s","dose":"%2$s","unit":"mg","route":"PO","scheduleTimes":["08:00"]}],
             "answerKey":{"observationElements":["장소=데이룸","발언 인용=무서워","투약=%1$s|TestDrugA&%2$smg|%2$s mg&PO|경구"],
                          "medicationElements":["약명=%1$s|TestDrugA","용량=%2$s","단위=mg","경로=PO|경구","시각=08:00"],
                          "sbarElements":{"situation":["불안 사건=불안"],"background":["진단=합성 진단"],
                                          "assessment":["재평가 결과=안정"],"recommendation":["다음 근무 관찰=관찰"]}},
             "modelAnswer":{"observation":"합성 모범","medication":"합성 모범","sbar":"S: 합성",
               "observationRecord":{"time":"08:00","shift":"DAY","tags":["불안 급증"],
                 "narrative":"데이룸에서 서성이며 \\"무서워요\\"라고 말함. 08:00 %1$s %2$smg 경구 투여함. 08:30 재평가 예정.",
                 "intervention":"1:1로 조용한 곳에서 대화함"},
               "medicationRecord":{"orderId":"O1","drugName":"%1$s","dose":"%2$s","unit":"mg","route":"PO","administeredTime":"08:00",
                 "result":"GIVEN","reason":"정규 투약","orderBasis":"정규 처방","oralCheck":true,"reassessmentPlan":"08:30 재평가"},
               "sbarReport":{"situation":"08:00 불안으로 서성임","background":"합성 진단","assessment":"08:30 안정됨",
                 "recommendations":[{"time":"16:00","intervention":"불안 재발 관찰"}]}}}
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
        assertNotNull(r.patientCase().answerKey()); // 같은 규칙으로 채점하기 위해 정답 키를 유지
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
        assertEquals("PC-01", r.patientCase().id()); // 기본 케이스(PC-01)로 돌아감
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
        assertEquals("PC-01", state.currentCase().id());
        assertEquals(Screen.PATIENT, state.current());
    }

    @Test
    void 정답_키가_없거나_처방과_다르면_버린다() {
        Fake f = new Fake();
        f.contents.add(caseJson("테스트약A", "3").replaceAll(",\\s*\"answerKey\"[\\s\\S]*\\}\\}\\s*$", "}"));
        f.contents.add(caseJson("테스트약A", "3").replace("\"용량=3\"", "\"용량=4\""));
        CaseGenerator.Result r = generator(ON, f, ALLOWED, 2).generate();
        assertFalse(r.generated());
        assertTrue(f.prompts.get(1).contains("answerKey(정답 키) 누락"), f.prompts.get(1));
        assertTrue(String.join(" ", r.problems()).contains("처방(정규·PRN)의 약·용량과 다름"), r.problems().toString());
    }

    @Test
    void 생성_케이스도_PC01과_같은_규칙으로_채점한다() {
        Fake f = new Fake();
        f.contents.add(caseJson("테스트약A", "3"));
        CaseGenerator.Result r = generator(ON, f, ALLOWED, 1).generate();
        assertTrue(r.generated());
        var med = new app.domain.MedicationRecord("O1", "테스트약A", "3", "mg", "PO", "08:00",
                app.domain.MedicationResult.GIVEN, null, "합성 근거", true, "합성 재평가");
        var scored = app.scoring.Scorer.evaluate(r.patientCase(), List.of(), List.of(med), null);
        assertEquals(25.0, scored.ruleScores().get("R7")); // 정답 키의 약명·용량·단위·경로·시각 모두 일치
        assertTrue(scored.ruleScores().containsKey("R2"));
    }

    @Test
    void 요청마다_환자_조건을_바꾸고_기본_케이스의_이름_나이_병실을_금지한다() {
        CaseGenerator g1 = new CaseGenerator(new AiClient(ON, new Fake()), new CaseValidator(ALLOWED),
                new CaseLoader(CaseGeneratorTest.class.getClassLoader()), 3, new java.util.Random(1));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 5; i++) seen.add(g1.conditions().lines().findFirst().orElse(""));
        assertTrue(seen.size() > 1, "조건이 매번 같으면 안 됨: " + seen);
        String c = g1.conditions();
        assertTrue(c.contains("금지: 이름 '김하늘', 나이 28세, 병실 '501-1호'"), c);
        assertTrue(c.contains("조현병, 조현정동장애, 양극성 장애"));
    }

    @Test
    void 기본_케이스와_같은_이름이나_범위_밖_나이는_다시_요청한다() {
        Fake f = new Fake();
        f.contents.add(caseJson("테스트약A", "3").replace("\"name\":\"가상환자\"", "\"name\":\"김하늘\""));
        f.contents.add(caseJson("테스트약A", "3").replace("\"age\":35", "\"age\":70"));
        f.contents.add(caseJson("테스트약A", "3"));
        CaseGenerator.Result r = generator(ON, f, ALLOWED, 3).generate();
        assertTrue(r.generated());
        assertEquals(3, f.calls);
        assertTrue(f.prompts.get(1).contains("patient.name이 기본 케이스와 같음"));
        assertTrue(f.prompts.get(2).contains("patient.age는 20~60세여야 함"));
    }

    @Test
    void 관찰_로그와_활력징후가_부족하면_다시_요청한다() {
        Fake f = new Fake();
        f.contents.add(caseJson("테스트약A", "3").replace(",{\"time\":\"10:30\",\"pulse\":82}", ""));
        f.contents.add(caseJson("테스트약A", "3"));
        CaseGenerator.Result r = generator(ON, f, ALLOWED, 3).generate();
        assertTrue(r.generated());
        assertTrue(f.prompts.get(1).contains("vitals(활력징후) 2회 이상 필요 (현재 1회)"));
    }

    private static java.util.function.Supplier<java.time.LocalDateTime> at(String t) {
        return () -> java.time.LocalDateTime.parse(t);
    }

    @Test
    void 케이스_ID는_AI가_아니라_프로그램이_생성_시각으로_붙인다() {
        Fake f = new Fake();
        f.contents.add(caseJson("테스트약A", "3")); // 응답의 id는 "GEN-1"
        CaseGenerator g = generator(ON, f, ALLOWED, 3);
        PatientCase a = g.withClock(at("2026-11-23T15:30:10")).generate().patientCase();
        PatientCase b = g.withClock(at("2026-11-23T15:30:50")).generate().patientCase(); // 같은 분
        PatientCase c = g.withClock(at("2026-11-23T16:05:00")).generate().patientCase();
        assertEquals("GEN-20261123-1530", a.id());
        assertEquals("GEN-20261123-1530-2", b.id());
        assertEquals("GEN-20261123-1605", c.id());
        // 과거 기록 목록에서 서로 구분된다
        var lines = java.util.stream.Stream.of(a, b, c)
                .map(x -> app.ui.ResultView.historyLine(new Attempt(1L, 1L, x.id(), CaseSource.AI_GENERATED, DomainJson.toJson(x),
                        null, java.time.LocalDateTime.parse("2026-11-23T17:00"), List.of(), List.of(), null, List.of(), 80.0, null)))
                .toList();
        assertEquals(3, new java.util.HashSet<>(lines).size(), lines.toString());
    }

    @Test
    void 생성_프롬프트는_학생이_담당_간호사_입장이고_정답_키는_한글_영어_PO_경구를_요구한다() {
        Fake f = new Fake();
        f.contents.add(caseJson("테스트약A", "3"));
        generator(ON, f, ALLOWED, 3).generate();
        String prompt = f.prompts.get(0);
        assertTrue(prompt.contains("학생이 담당 간호사 입장이다"));
        assertTrue(prompt.contains("1:1로 조용한 곳에서 대화하며 안정을 도움"));
        assertTrue(prompt.contains("간호사에게 알리고"));
        assertTrue(prompt.contains("PO|경구"));
        assertTrue(prompt.contains("한글·영어"));
        assertFalse(prompt.contains("\\\"id\\\":\\\"GEN-1\\\"")); // 견본에 ID를 주지 않는다
    }

    @Test
    void 모범_기록이_자기_정답_키로_100점이_아니면_다시_생성한다() {
        Fake f = new Fake();
        // 모범 관찰 기록에서 정답 장소(데이룸)를 빼 R2 감점 → 거부
        f.contents.add(caseJson("테스트약A", "3").replace("\"narrative\":\"데이룸에서", "\"narrative\":\"복도에서"));
        f.contents.add(caseJson("테스트약A", "3"));
        CaseGenerator.Result r = generator(ON, f, ALLOWED, 3).generate();
        assertTrue(r.generated());
        assertEquals(2, f.calls);
        assertTrue(f.prompts.get(1).contains("100점이어야 함"), f.prompts.get(1));
        // 구조화된 모범 기록이 없어도 거부
        Fake g = new Fake();
        g.contents.add(caseJson("테스트약A", "3").replaceAll(",\\s*\"observationRecord\"[\\s\\S]*\\}\\}\\s*$", "}}"));
        CaseGenerator.Result r2 = generator(ON, g, ALLOWED, 1).generate();
        assertFalse(r2.generated());
        assertTrue(r2.problems().stream().anyMatch(x -> x.contains("구조화된 모범 기록")), r2.problems().toString());
    }

    @Test
    void 학생이_한글로_쓴_모범_문장으로도_100점이다() {
        Fake f = new Fake();
        f.contents.add(caseJson("TestDrugA", "3").replace("약명=TestDrugA|TestDrugA", "약명=TestDrugA|테스트약A")
                .replace("투약=TestDrugA|TestDrugA", "투약=TestDrugA|테스트약A"));
        PatientCase c = generator(ON, f, ALLOWED_EN, 1).generate().patientCase();
        assertTrue(c.id().startsWith("GEN-"), "생성 실패: " + c.id());
        // 약명·경로를 한글로 쓴 학생 기록
        var obs = new app.domain.ObservationEntry("08:00", app.domain.Shift.DAY, List.of("불안 급증"),
                "데이룸에서 왔다 갔다 하며 \"무서워요\"라고 말함. 08:00 테스트약A 3 mg 경구로 투여함. 08:30 재평가하기로 함.",
                "조용한 곳에서 1:1로 이야기함", null);
        var med = new app.domain.MedicationRecord("O1", "테스트약A", "3", "mg", "경구", "08:00",
                app.domain.MedicationResult.GIVEN, "정규 투약", "정규 처방", true, "08:30 재평가");
        var sbar = new app.domain.SbarReport("08:00 불안해하며 서성임", "합성 진단으로 입원", "08:30 안정됨",
                List.of(new app.domain.SbarRecommendation("16:00", "불안 다시 오는지 관찰")));
        app.scoring.Scorer.Result r = app.scoring.Scorer.evaluate(c, List.of(obs), List.of(med), sbar);
        assertEquals(100.0, r.score(), r.checks().stream().filter(x -> x.status() != app.domain.CheckStatus.PASS
                && x.status() != app.domain.CheckStatus.FEEDBACK).toList().toString());
    }

    @Test
    void 영어로만_쓴_정답_키는_거부한다() {
        Fake f = new Fake();
        f.contents.add(caseJson("TestDrugA", "3")); // 약명 동의어가 TestDrugA|TestDrugA → 한글 없음
        CaseGenerator.Result r = generator(ON, f, ALLOWED_EN, 1).generate();
        assertFalse(r.generated());
        assertTrue(r.problems().stream().anyMatch(x -> x.contains("한글·영어 약명")), r.problems().toString());
    }

    /** 영어 이름이 기본이고 한글 별칭이 있는 합성 허용 목록 */
    private static final AllowedDrugs ALLOWED_EN = AllowedDrugs.parse("""
            {"drugs":[{"name":"TestDrugA","aliases":["테스트약A"],"unit":"mg","route":"PO","minDailyDose":1,"maxDailyDose":5}]}
            """, "test");
}

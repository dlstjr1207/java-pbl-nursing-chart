package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.cases.CaseLoader;
import app.domain.MedicationRecord;
import app.domain.MedicationResult;
import app.domain.SbarRecommendation;
import app.storage.InMemoryAttemptRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SBAR 제출을 준비(화면 스레드)와 완료(백그라운드)로 나눈 동작을 확인한다 (단계 1-1).
 * AI가 실패하거나 예외를 던져도 기록과 점검 결과는 저장된다 (S7). 값은 모두 합성 값이다.
 */
class SbarBackgroundSubmitTest {
    private static final AiConfig ON = new AiConfig("test", "fake-key", "fake-model", "http://fake.local/v1");
    private static final List<SbarRecommendation> ROWS = List.of(new SbarRecommendation("09:00", "합성 중재"));

    private AppState state;
    private InMemoryAttemptRepository repo;

    @BeforeEach
    void setUp() {
        state = new AppState();
        state.login("테스터");
        state.startPractice(new CaseLoader().load("demo"));
        state.go(Screen.SBAR);
        repo = new InMemoryAttemptRepository();
    }

    @Test
    void 준비_단계는_저장하지_않고_화면도_그대로다() {
        SbarController c = new SbarController(state, repo, () -> 7L);
        var p = c.prepare("상황", "배경", "평가", ROWS);
        assertTrue(p.ok());
        assertFalse(p.draft().checks().isEmpty());
        assertTrue(repo.findByUser(7L).isEmpty());
        assertEquals(Screen.SBAR, state.current());
    }

    @Test
    void AI가_예외를_던져도_기록과_점검_결과는_저장되고_해설만_실패로_남는다() {
        AiClient broken = new AiClient(ON, (url, key, body) -> {
            throw new IllegalStateException("합성 오류");
        });
        SbarController c = new SbarController(state, repo, () -> 7L, broken);
        var p = c.prepare("상황", "배경", "평가", ROWS);
        var r = c.complete(p);
        assertTrue(r.submitted());
        assertEquals(AiClient.STATUS_FAILED, r.attempt().aiFeedback().status());
        assertEquals(p.draft().checks(), r.attempt().checks());
        // complete는 화면 상태를 바꾸지 않는다. finish()가 방금 제출한 기록의 결과 화면으로 옮긴다.
        assertEquals(Screen.SBAR, state.current());
        c.finish(r.attempt());
        assertEquals(Screen.RESULT, state.current());
        assertEquals(r.attempt(), state.resultAttempt());
        assertEquals(null, state.currentCase());
    }

    @Test
    void AI_해설_요청에_지어내기_금지와_케이스의_실제_사실을_넣는다() {
        List<String> bodies = new java.util.ArrayList<>();
        AiClient fake = new AiClient(ON, (url, key, body) -> {
            bodies.add(body);
            throw new java.io.IOException("가짜 응답 없음");
        });
        SbarController c = new SbarController(state, repo, () -> 7L, fake);
        String briefing = state.currentCase().briefing();
        var p = c.prepare("상황", "배경", "평가", ROWS);
        c.complete(p);
        assertEquals(1, bodies.size());
        String body = com.google.gson.JsonParser.parseString(bodies.get(0)).toString();
        // 시스템 지시와 사용자 요청 모두에 지어내기 금지 문구
        assertTrue(body.contains("케이스와 학생 기록에 없는 환자 발언·수치·사건을 만들지 말 것"), body);
        assertTrue(body.contains("예시 문장(example_rewrite)에는 케이스의 실제 발언만 인용할 것"), body);
        String prompt = SbarController.explainPrompt(state.currentCase(), p.draft(), p.draft().sbar(), p.draft().checks());
        assertTrue(prompt.contains(briefing), "케이스 브리핑을 사실 자료로 보냄");
        assertTrue(prompt.indexOf(app.ai.AiClient.NO_FABRICATION) >= 0);
        // 환자 정보 화면의 활력징후·관찰 로그도 사실로 보낸다 (실제 수치를 지어냈다고 잘못 지적하지 않게)
        var pc01 = new CaseLoader().load("PC-01");
        String p01 = SbarController.explainPrompt(pc01, p.draft(), p.draft().sbar(), p.draft().checks());
        var v = pc01.vitals().get(0);
        assertTrue(p01.contains("[케이스 활력징후] " + v.time() + " BP " + v.systolic() + "/" + v.diastolic()), p01);
        assertTrue(p01.contains(pc01.observationLog().get(0).narrative()), p01);
    }

    @Test
    void 작성한_관찰이나_투약이_있으면_작성_중으로_본다() {
        assertFalse(state.hasStudentRecords());
        state.addMedication(new MedicationRecord("ORD", "합성약", "1", "정", "PO", "09:00",
                MedicationResult.GIVEN, null, "합성 근거", true, null));
        assertTrue(state.hasStudentRecords());
        state.go(Screen.HOME);
        assertFalse(state.hasStudentRecords());
    }

    @Test
    void 파일_저장_모드면_상단_안내에_표시한다() {
        state.setStorageNotice(AppState.FILE_MODE_NOTICE);
        assertTrue(state.notices().contains(AppState.FILE_MODE_NOTICE));
    }
}

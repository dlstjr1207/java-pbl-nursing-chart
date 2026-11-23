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
        // complete는 화면 상태를 바꾸지 않는다. finish()가 개인 메인으로 옮긴다.
        assertEquals(Screen.SBAR, state.current());
        c.finish();
        assertEquals(Screen.HOME, state.current());
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

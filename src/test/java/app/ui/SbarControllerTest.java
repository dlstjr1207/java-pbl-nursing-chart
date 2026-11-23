package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import app.domain.Attempt;
import app.domain.SbarRecommendation;
import app.storage.InMemoryAttemptRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SBAR 필수값 검증·제출·저장·이동을 확인한다 (S5, V5, V7). DEMO 케이스와 합성 값만 쓴다. */
class SbarControllerTest {
    private AppState state;
    private InMemoryAttemptRepository repo;
    private SbarController controller;
    private static final List<SbarRecommendation> ROWS = List.of(new SbarRecommendation("09:00", "활력징후 재측정"));

    @BeforeEach
    void setUp() {
        state = new AppState();
        state.login("테스터");
        state.startPractice(new CaseLoader().load("demo"));
        state.go(Screen.SBAR);
        repo = new InMemoryAttemptRepository();
        controller = new SbarController(state, repo, () -> 7L);
    }

    @Test
    void S만_입력하면_B_A_R_안내와_함께_제출이_보류되고_저장되지_않는다() {
        var r = controller.submit("상황 입력", "", " ", List.of());
        assertFalse(r.submitted());
        assertTrue(r.errors().containsKey("background"));
        assertTrue(r.errors().containsKey("assessment"));
        assertTrue(r.errors().containsKey("rec"));
        assertFalse(r.errors().containsKey("situation"));
        assertTrue(repo.findByUser(7L).isEmpty());
        assertEquals(Screen.SBAR, state.current());
    }

    @Test
    void R_행의_시각_또는_중재가_빠지면_행별_오류를_낸다() {
        var r = controller.submit("s", "b", "a", List.of(
                new SbarRecommendation("", "중재 있음"), new SbarRecommendation("10:00", " "),
                new SbarRecommendation("25:00", "x")));
        assertFalse(r.submitted());
        assertTrue(r.errors().containsKey("rec1.time"));
        assertTrue(r.errors().containsKey("rec2.intervention"));
        assertTrue(r.errors().containsKey("rec3.time"));
    }

    @Test
    void 성공하면_사용자별로_저장하고_개인_메인으로_이동한다() {
        var r = controller.submit("상황", "배경", "평가", ROWS);
        assertTrue(r.submitted());
        Attempt saved = r.attempt();
        assertNotNull(saved.attemptId());
        assertEquals(7L, saved.userId());
        assertEquals("상황", saved.sbar().situation());
        assertEquals(1, saved.sbar().recommendations().size());
        assertTrue(saved.isSubmitted());
        assertEquals(1, repo.findByUser(7L).size());
        assertTrue(repo.findByUser(8L).isEmpty());
        assertEquals(Screen.HOME, state.current());
        assertEquals(null, state.currentCase());
    }

    @Test
    void 학생이_쓴_관찰_투약_기록도_함께_저장된다() {
        state.addObservation(new app.domain.ObservationEntry("09:00", app.domain.Shift.DAY, List.of("기타"),
                "서술", null, null));
        var r = controller.submit("s", "b", "a", ROWS);
        assertEquals(1, r.attempt().observationEntries().size());
    }
}

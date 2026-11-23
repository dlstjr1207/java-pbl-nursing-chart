package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import app.domain.AiFeedback;
import app.domain.Attempt;
import app.domain.CheckStatus;
import app.domain.SbarRecommendation;
import app.storage.InMemoryAttemptRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 결과 패널·과거 기록·평가지표 (S6~S8). DEMO 케이스와 합성 값만 쓴다. */
class ResultViewTest {
    private final InMemoryAttemptRepository repo = new InMemoryAttemptRepository();
    private AppState state;

    @BeforeEach
    void setUp() {
        state = new AppState();
        state.login("테스터");
    }

    private Attempt submitAs(long user, String situation) {
        state.startPractice(new CaseLoader().load("demo"));
        state.go(Screen.SBAR);
        var r = new SbarController(state, repo, () -> user)
                .submit(situation, "배경", "평가", List.of(new SbarRecommendation("09:00", "재측정")));
        return r.attempt();
    }

    @Test
    void 제출하면_점검_결과가_저장되고_누락이_항목별로_표시된다() {
        Attempt a = submitAs(1L, "상황");
        // 관찰·투약 기록 없이 제출했으므로 R8 누락이 있어야 한다
        assertTrue(a.checks().stream().anyMatch(c -> c.status() == CheckStatus.MISSING && c.ruleId().equals("R8")));
        String text = ResultView.checksText(a.checks());
        assertTrue(text.contains("누락"));
        assertTrue(text.contains("잘 작성한 내용"));
        assertTrue(text.contains("관찰 기록")); // 위치는 사람 말로 (polish A-2)
        assertFalse(text.contains("observation"));
        assertTrue(text.contains("피드백")); // R9: 필수 요소 목록 없음 → 미산출은 피드백 묶음
    }

    @Test
    void 과거_기록_상세는_제출_당시_입력과_결과를_재현한다() {
        Attempt a = submitAs(1L, "상황 문장");
        ResultView view = new ResultView(repo, 1L);
        String detail = view.detail(a.attemptId()).orElseThrow();
        assertTrue(detail.contains("S: 상황 문장"));
        assertTrue(detail.contains("R: 09:00 재측정"));
        assertTrue(detail.contains("점수 " + ResultView.scoreText(a)));
        assertTrue(detail.contains("준비 중")); // DEMO에는 모범 기록이 없다
    }

    @Test
    void 평가지표는_횟수와_점수를_보여_준다() {
        ResultView view = new ResultView(repo, 1L);
        assertTrue(view.metrics().get(0).contains("0회"));
        submitAs(1L, "a");
        state.login("테스터");
        submitAs(1L, "b");
        List<String> m = view.metrics();
        assertEquals("총 실습 횟수: 2회", m.get(0));
        assertTrue(m.get(1).startsWith("최근 점수: ") && m.get(1).endsWith("점"), m.get(1));
        assertTrue(m.stream().anyMatch(s -> s.startsWith("항목별 R8")));
        assertTrue(m.stream().anyMatch(s -> s.startsWith("최근 2회")));
    }

    @Test
    void 다른_사용자의_기록은_보이지_않는다() {
        Attempt a = submitAs(1L, "A의 기록");
        ResultView b = new ResultView(repo, 2L);
        assertTrue(b.history().isEmpty());
        assertTrue(b.detail(a.attemptId()).isEmpty());
        assertTrue(b.metrics().get(0).contains("0회"));
    }

    @Test
    void AI_해설_실패와_없음에도_안내_문구가_나온다() {
        assertTrue(ResultView.aiText(null).contains("없음"));
        assertTrue(ResultView.aiText(new AiFeedback("FAILED", List.of(), List.of(), null)).contains("불러오지 못했습니다"));
        String ok = ResultView.aiText(new AiFeedback("SUCCESS", List.of("잘함"), List.of("보완"), "예시"));
        assertTrue(ok.contains("잘함") && ok.contains("보완") && ok.contains("예시"));
        assertFalse(ok.contains("불러오지"));
    }
}

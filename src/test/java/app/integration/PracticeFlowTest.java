package app.integration;

import static org.junit.jupiter.api.Assertions.*;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.AiTransport;
import app.auth.AuthService;
import app.cases.CaseLoader;
import app.domain.Attempt;
import app.domain.MedicationResult;
import app.domain.SbarRecommendation;
import app.domain.Shift;
import app.export.ResultExporter;
import app.storage.InMemoryAttemptRepository;
import app.storage.InMemoryUserRepository;
import app.ui.AppState;
import app.ui.AuthFormController;
import app.ui.HomeController;
import app.ui.MedicationController;
import app.ui.ObservationController;
import app.ui.ResultView;
import app.ui.SbarController;
import app.ui.Screen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 가입→로그인→실습 시작→관찰·투약·SBAR 제출→결과·과거 기록→로그아웃 한 회차와 실패 경로를 이어서 확인한다
 * (S1~S8). DEMO 케이스·메모리 저장소·가짜 AI 응답만 쓰며, 실제 AI·DB는 호출하지 않는다.
 */
class PracticeFlowTest {
    private static final AiConfig ON = new AiConfig("test", "fake-key", "fake-model", "http://fake.local/v1");
    private static final List<SbarRecommendation> ROWS = List.of(new SbarRecommendation("09:00", "활력징후 재측정"));
    private static final String GOOD_JSON =
            "{\"strengths\":[\"구체적\"],\"improvements\":[\"시각 보완\"],\"example_rewrite\":\"예시\"}";

    private AppState state;
    private AuthService auth;
    private AuthFormController form;
    private InMemoryAttemptRepository attempts;

    @BeforeEach
    void setUp() {
        state = new AppState();
        auth = new AuthService(new InMemoryUserRepository(), state);
        form = new AuthFormController(auth, state);
        attempts = new InMemoryAttemptRepository();
    }

    private void signupAndLogin(String id) {
        assertTrue(form.signup(id, "password1", "password1", "학생" + id).success());
        assertEquals(Screen.LOGIN, state.current());
        assertTrue(form.login(id, "password1").success());
        assertEquals(Screen.HOME, state.current());
    }

    private SbarController sbar(AiClient ai) {
        return new SbarController(state, attempts, () -> auth.currentUser().orElseThrow().id(), ai);
    }

    private void startDemo() {
        assertNull(new HomeController(state, new CaseLoader()).startDefaultPractice());
    }

    /** 관찰 1건·투약 1건을 저장한다. */
    private void writeRecords() {
        var obs = new ObservationController(state).save("09:00", Shift.DAY, List.of("기타"), "합성 서술", null, Map.of());
        assertTrue(obs.saved());
        MedicationController med = new MedicationController(state);
        var order = med.selectableOrders().get(0);
        assertTrue(med.save(order, "15", "mg", "PO", "09:00", MedicationResult.GIVEN, null, "처방 확인", null, null).saved());
    }

    /** chat completions 형식으로 content를 감싸 돌려주는 가짜 전송. */
    private static AiTransport reply(String content) {
        return (url, key, body) -> {
            var msg = new com.google.gson.JsonObject();
            msg.addProperty("content", content);
            var choice = new com.google.gson.JsonObject();
            choice.add("message", msg);
            var choices = new com.google.gson.JsonArray();
            choices.add(choice);
            var root = new com.google.gson.JsonObject();
            root.add("choices", choices);
            return root.toString();
        };
    }

    @Test
    void 실습_한_회차_가입부터_결과_조회_내보내기_로그아웃까지(@TempDir Path dir) throws IOException {
        signupAndLogin("tester01");
        startDemo();
        assertEquals(Screen.PATIENT, state.current());
        assertTrue(state.notices().contains(AppState.DEMO_NOTICE));
        writeRecords();
        assertTrue(state.go(Screen.SBAR));

        var r = sbar(new AiClient(ON, reply(GOOD_JSON))).submit("상황", "배경", "평가", ROWS);
        assertTrue(r.submitted());
        assertEquals(Screen.HOME, state.current());
        assertEquals("SUCCESS", r.attempt().aiFeedback().status());
        assertNull(r.attempt().score(), "AI 해설과 무관하게 점수는 미산출");
        assertFalse(r.attempt().checks().isEmpty());

        ResultView view = new ResultView(attempts, auth.currentUser().orElseThrow().id());
        assertEquals("총 실습 횟수: 1회", view.metrics().get(0));
        String detail = view.detail(r.attempt().attemptId()).orElseThrow();
        assertTrue(detail.contains("합성 서술") && detail.contains("상황") && detail.contains("구체적"));

        Path out = dir.resolve("result.txt");
        assertNull(new ResultExporter().export(r.attempt(), out));
        assertTrue(Files.readString(out).contains("합성 서술"));

        auth.logout();
        assertEquals(Screen.LOGIN, state.current());
        assertNull(state.currentCase());
    }

    @Test
    void 중복_가입은_막고_로그인_실패는_원인을_구분하지_않는다() {
        signupAndLogin("tester01");
        auth.logout();
        assertFalse(form.signup("tester01", "password1", "password1", "다른이").success());
        var wrongPw = form.login("tester01", "wrong-pass");
        var noUser = form.login("nobody99", "password1");
        assertFalse(wrongPw.success());
        assertEquals(wrongPw.message(), noUser.message());
        assertEquals(Screen.LOGIN, state.current());
    }

    @Test
    void SBAR_빈_영역은_제출이_보류되고_보완_후_재제출하면_저장된다() {
        signupAndLogin("tester01");
        startDemo();
        state.go(Screen.SBAR);
        var first = sbar(null).submit("상황만", "", "", List.of());
        assertFalse(first.submitted());
        assertEquals(Screen.SBAR, state.current());
        assertTrue(attempts.findByUser(auth.currentUser().orElseThrow().id()).isEmpty());
        var second = sbar(null).submit("상황만", "배경", "평가", ROWS);
        assertTrue(second.submitted());
        assertEquals("NONE", second.attempt().aiFeedback().status(), "AI 없으면 규칙 점검만");
    }

    @Test
    void AI_통신_실패와_형식_오류에도_점검_결과와_저장은_유지된다() {
        AiTransport down = (u, k, b) -> {
            throw new IOException("통신 실패(가짜)");
        };
        int n = 0;
        for (AiTransport t : List.of(down, reply("JSON 아님"))) {
            signupAndLogin("tester0" + (n++));
            startDemo();
            writeRecords();
            state.go(Screen.SBAR);
            var r = sbar(new AiClient(ON, t)).submit("상황", "배경", "평가", ROWS);
            assertTrue(r.submitted());
            assertEquals("FAILED", r.attempt().aiFeedback().status());
            assertFalse(r.attempt().checks().isEmpty());
            assertNull(r.attempt().score());
            assertTrue(ResultView.aiText(r.attempt().aiFeedback()).contains("불러오지 못했습니다"));
            auth.logout();
        }
    }

    @Test
    void 사용자_A의_기록은_B에게_보이지_않는다() {
        signupAndLogin("userAAAA");
        startDemo();
        writeRecords();
        state.go(Screen.SBAR);
        Attempt a = sbar(null).submit("상황A", "배경", "평가", ROWS).attempt();
        long aId = auth.currentUser().orElseThrow().id();
        auth.logout();

        signupAndLogin("userBBBB");
        long bId = auth.currentUser().orElseThrow().id();
        assertNotEquals(aId, bId);
        ResultView bView = new ResultView(attempts, bId);
        assertTrue(bView.history().isEmpty());
        assertTrue(bView.detail(a.attemptId()).isEmpty());
        assertEquals(1, new ResultView(attempts, aId).history().size());
    }

    @Test
    void 로그인_없이는_실습_화면에_들어가지_못한다() {
        assertFalse(state.go(Screen.HOME));
        assertFalse(state.go(Screen.SBAR));
        assertEquals(Screen.LOGIN, state.current());
    }
}

package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import app.cases.CaseLoader;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.Medication;
import app.domain.OrderType;
import app.domain.PatientCase;
import app.ui.ObservationController.ChartPoint;
import app.ui.ObservationController.Metric;
import java.awt.GraphicsEnvironment;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 학생 화면 다듬기(polish/1130) 확인. 기능·채점 규칙은 바꾸지 않고 보여 주는 방식만 확인한다. */
class PolishTest {
    private final PatientCase pc01 = new CaseLoader().load("PC-01");

    @Test
    void 비고에서_근거번호와_검증필요_문장을_뺀다() {
        assertEquals("1일 최대 4mg", StudentText.note("1일 최대 4mg. E-29. PRN 불안·초조 적응증 [검증필요]"));
        assertEquals("", StudentText.note("E-20. 적응증(정신분열병) [검증필요]"));
        assertEquals("2024-11 Aripiprazole로 교체", StudentText.note("2024-11 Aripiprazole로 교체 (E-28)"));
        assertEquals("-", StudentText.value("[확인필요]"));
        assertEquals("관찰한 행동으로 바꿔 쓰세요.", StudentText.message("관찰한 행동으로 바꿔 쓰세요. [검증필요 교육용 예시, 배점 없음]"));
    }

    @Test
    void PC01_환자_정보와_처방에_개발용_표기가_보이지_않는다() {
        for (PatientInfoView.Section s : PatientInfoView.sections(pc01)) {
            for (String line : s.lines()) assertFalse(StudentText.hasMarks(line), s.title() + ": " + line);
        }
        for (Medication m : pc01.orders()) {
            assertFalse(StudentText.hasMarks(MedicationController.orderSummary(m)), MedicationController.orderSummary(m));
        }
    }

    @Test
    void 비어_있는_위험_지표_카드는_만들지_않는다() {
        assertTrue(PatientInfoView.sections(pc01).stream().noneMatch(s -> s.title().startsWith("위험 지표")));
    }

    @Test
    void 근거_창에는_원문_근거번호와_검토상태가_있다() {
        String t = EvidenceDialog.text(pc01);
        assertTrue(t.contains("E-20"));
        assertTrue(t.contains("검토 상태: 팀 확정 초안"));
        assertTrue(t.contains("[확인필요]"));
    }

    @Test
    void 점검_위치는_사람_말로_바꾼다() {
        assertEquals("관찰 기록 · 장소", CheckLabels.where("observation.fact[장소]"));
        assertEquals("투약 기록 1 · 용량", CheckLabels.where("medication[1].용량"));
        assertEquals("투약 기록 1 · 처방 근거", CheckLabels.where("medication[1].orderBasis"));
        assertEquals("SBAR R 1행 · 시각", CheckLabels.where("rec1.time"));
        assertEquals("관찰 기록 1 · 서술", CheckLabels.where("observation[1].narrative"));
        assertEquals("SBAR B(배경) · 필수 요소 2", CheckLabels.where("background.element[2]"));
        assertEquals("재평가 계획", CheckLabels.where("reassessment"));
        CheckResult c = new CheckResult("R7", CheckTarget.MEDICATION, "medication[1].용량", CheckStatus.MISMATCH,
                "투약 용량: 입력 '2', 기준 '1'", null);
        assertEquals("투약 기록 1 · 용량 — 투약 용량: 입력 '2', 기준 '1' (R7 투약 값 일치)", CheckLabels.line(c));
    }

    @Test
    void 결과_묶음은_고칠점_피드백_잘한점으로_나누고_R9는_피드백이다() {
        List<CheckResult> checks = List.of(
                new CheckResult("R8", CheckTarget.SBAR, "situation", CheckStatus.MISSING, "S 누락", null),
                new CheckResult("R7", CheckTarget.MEDICATION, "medication[1].용량", CheckStatus.MISMATCH, "다름", null),
                new CheckResult("R9", CheckTarget.SBAR, "situation.element[1]", CheckStatus.MISSING, "요소", null),
                new CheckResult("R1", CheckTarget.OBSERVATION, "observation[1].narrative", CheckStatus.FEEDBACK, "판단", null),
                new CheckResult("R6", CheckTarget.MEDICATION, "medication[1].dose", CheckStatus.PASS, "있음", null));
        ResultView.Groups g = ResultView.groups(checks);
        assertEquals(1, g.missing().size());
        assertEquals(1, g.mismatch().size());
        assertEquals(List.of("R9", "R1"), g.feedback().stream().map(CheckResult::ruleId).toList());
        assertEquals(1, g.passed().size());
    }

    @Test
    void 선택한_처방_문구() {
        Medication prn = pc01.orders().stream().filter(m -> m.type() == OrderType.PRN).findFirst().orElseThrow();
        assertEquals("Lorazepam 1mg PO PRN (불안·초조 시, 1일 최대 4mg)", MedicationController.orderSummary(prn));
    }

    @Test
    void 투약_화면은_처방을_골라도_입력칸을_채우지_않는다() throws Exception {
        AppState state = new AppState();
        state.login("테스터");
        state.startPractice(pc01);
        MedicationPanel panel = new MedicationPanel(state, s -> { });
        @SuppressWarnings("unchecked")
        var box = (javax.swing.JComboBox<Medication>) field(panel, "orderBox");
        box.setSelectedIndex(box.getItemCount() - 1);
        assertTrue(panel.selectedOrderText().startsWith("선택한 처방: Lorazepam 1mg PO PRN"), panel.selectedOrderText());
        for (String f : List.of("doseField", "unitField", "routeField", "timeField")) {
            assertEquals("", ((javax.swing.JTextField) field(panel, f)).getText(), f);
        }
    }

    @Test
    void 활력징후_차트는_항목별_최소_범위를_쓰고_벗어나면_넓힌다() {
        VitalChartPanel chart = new VitalChartPanel();
        chart.setData("맥박", List.of(new ChartPoint("10:00", 88, true)), Metric.PULSE.axisMin(), Metric.PULSE.axisMax(),
                Metric.PULSE.tick());
        assertEquals(40, chart.axisRange()[0]);
        assertEquals(140, chart.axisRange()[1]);
        chart.setData("맥박", List.of(new ChartPoint("10:00", 150, true)), 40, 140, 20);
        assertEquals(160, chart.axisRange()[1]);
        assertEquals(85, Metric.SPO2.axisMin());
        assertEquals(35, Metric.TEMPERATURE.axisMin());
    }

    @Test
    void 로그인_전에는_왼쪽_메뉴를_숨긴다_판단() {
        AppState state = new AppState();
        assertFalse(state.showSideMenu());
        state.login("테스터");
        assertTrue(state.showSideMenu());
        state.logout();
        assertFalse(state.showSideMenu());
    }

    @Test
    void 결과를_보는_동안_상단에_그_실습의_환자를_보인다() {
        AppState state = new AppState();
        state.login("테스터");
        app.domain.Attempt a = new app.domain.Attempt(1L, 1L, "PC-01", app.domain.CaseSource.FIXED, null,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), List.of(), List.of(), null, List.of(), 100.0, null);
        assertTrue(state.showResult(a));
        assertEquals("환자: " + pc01.patient().name() + " (PC-01) · 결과 보기", state.patientLabel());
        state.go(Screen.HOME);
        assertEquals("환자: 선택 안 함", state.patientLabel());
    }

    /** 실제 JFrame을 만드므로 화면 없는 환경(headless)에서는 건너뛴다. CI는 xvfb-run으로 화면을 준다. */
    @Test
    void 로그인_전에는_왼쪽_메뉴를_숨긴다_창() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "화면 없는 환경");
        AppState state = new AppState();
        MainFrame frame = new MainFrame(state);
        javax.swing.JPanel menu = (javax.swing.JPanel) field(frame, "sideMenu");
        assertFalse(menu.isVisible());
        state.login("테스터");
        frame.refresh();
        assertTrue(menu.isVisible());
        frame.dispose();
    }

    @Test
    void 결과_화면은_로그인과_기록이_있어야_열린다() {
        AppState state = new AppState();
        assertFalse(state.canGo(Screen.RESULT));
        state.login("테스터");
        assertFalse(state.canGo(Screen.RESULT)); // 기록 없음
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        var f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}

package app.ui;

import app.cases.CaseLoader;
import app.domain.Attempt;
import app.domain.CheckStatus;
import app.domain.FiveRights;
import app.domain.Medication;
import app.domain.MedicationResult;
import app.domain.MentalStatus;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.Shift;
import app.scoring.MentalStatusChecker;
import app.storage.InMemoryAttemptRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** D38: 기록 서명, 투약 5원칙, 정신상태 평가(MSE), SBAR 실제 인계 기록 형태. 모두 점수에는 넣지 않는다. */
class FinalFeaturesTest {

    private final PatientCase pc01 = new CaseLoader().load("PC-01");

    private AppState practice() {
        AppState state = new AppState();
        state.login("김간호");
        state.startPractice(pc01);
        return state;
    }

    private Medication prn() {
        return pc01.orders().stream().filter(o -> o.orderId().equals("PC01-LZP-PRN")).findFirst().orElseThrow();
    }

    @Test
    void 투약_5원칙을_모두_체크해야_저장한다() {
        AppState state = practice();
        MedicationController c = new MedicationController(state);
        var r = c.save(prn(), "1", "mg", "PO", "09:30", MedicationResult.GIVEN, "불안", "PRN 처방", true, null,
                new FiveRights(true, true, false, true, false));
        assertFalse(r.saved());
        assertEquals("투약 5원칙을 모두 확인하세요 (확인 안 함: 대상자·시간).", r.errors().byField().get("rights"));
        assertTrue(state.studentMedications().isEmpty());
        assertFalse(c.save(prn(), "1", "mg", "PO", "09:30", MedicationResult.GIVEN, "불안", "PRN 처방", true, null, null).saved());
        assertTrue(c.save(prn(), "1", "mg", "PO", "09:30", MedicationResult.GIVEN, "불안", "PRN 처방", true, null, FiveRights.ALL).saved());
        assertEquals(FiveRights.ALL, state.studentMedications().get(0).rights());
        String line = c.recordLines().get(0);
        assertTrue(line.contains("5 Rights: 약물 ✓ · 용량 ✓ · 대상자 ✓ · 경로 ✓ · 시간 ✓"), line);
        assertTrue(line.endsWith("서명 김간호"), line);
    }

    @Test
    void 관찰_기록은_날짜_시간과_서명을_보인다() {
        AppState state = practice();
        ObservationController c = new ObservationController(state);
        assertTrue(c.save("09:25", Shift.DAY, List.of("불안 급증"), "합성 서술", null, Map.of()).saved());
        var row = c.timeline().get(0);
        assertEquals("2026-10-17 09:25", c.dateTime(row)); // PC-01 근무 날짜 + 시각
        assertEquals("김간호", c.signature(row));
        var caseRow = new ObservationController.TimelineRow("08:00", true, row.entry());
        assertEquals(ObservationController.PREVIOUS_STAFF, c.signature(caseRow));
        assertEquals("이전 근무자", ObservationController.PREVIOUS_STAFF);
    }

    @Test
    void 정신상태_평가는_피드백만_주고_점수는_같다() {
        // 비어 있으면 "정신상태 평가를 기록하세요" (피드백), 적으면 잘한 내용. 점수는 바뀌지 않는다
        Attempt empty = submit(practice(), null);
        AppState filled = practice();
        var saved = new ObservationController(filled).saveMentalStatus("명료(alert)", "단정함", "불안", "", "환청 없다고 함", "시간·장소·사람 정확");
        assertTrue(saved.saved());
        Attempt withMse = submit(filled, null);
        var e = empty.checks().stream().filter(x -> x.ruleId().equals(MentalStatusChecker.RULE)).findFirst().orElseThrow();
        assertEquals(CheckStatus.FEEDBACK, e.status());
        assertEquals("정신상태 평가를 기록하세요.", e.message());
        assertTrue(ResultView.groups(empty.checks()).feedback().contains(e));
        var w = withMse.checks().stream().filter(x -> x.ruleId().equals(MentalStatusChecker.RULE)).findFirst().orElseThrow();
        assertEquals(CheckStatus.PASS, w.status());
        assertEquals(empty.score(), withMse.score());
        assertEquals("명료(alert)", withMse.mentalStatus().consciousness());
        assertNull(withMse.mentalStatus().thought()); // 빈 칸은 null
        assertTrue(CheckLabels.line(e).startsWith("관찰 기록 · 정신상태 평가 — 정신상태 평가를 기록하세요."), CheckLabels.line(e));
        // 5단계 밖의 값은 받지 않는다
        assertFalse(new ObservationController(practice()).saveMentalStatus("졸림", "", "", "", "", "").saved());
        assertEquals(List.of("명료(alert)", "기면(drowsy)", "혼미(stupor)", "반혼수(semicoma)", "혼수(coma)"), MentalStatus.CONSCIOUSNESS);
    }

    private Attempt submit(AppState state, String ignored) {
        state.addObservation(pc01.modelAnswer().observationRecord());
        state.addMedication(pc01.modelAnswer().medicationRecord().withRights(FiveRights.ALL));
        var s = pc01.modelAnswer().sbarReport();
        var r = new SbarController(state, new InMemoryAttemptRepository(), () -> 1L)
                .submit(s.situation(), s.background(), s.assessment(), s.recommendations());
        assertTrue(r.submitted(), r.message());
        return r.attempt();
    }

    @Test
    void 결과와_다시_보기에_서명_5원칙_정신상태_한_문단이_보인다() {
        AppState state = practice();
        new ObservationController(state).saveMentalStatus("기면(drowsy)", "", "", "", "", "");
        Attempt a = submit(state, null);
        String t = ResultView.submittedText(a, "김간호");
        assertTrue(t.contains("[관찰 기록] 날짜·시간 | 내용 | 서명"), t);
        assertTrue(t.contains("2026-10-17 " + pc01.modelAnswer().observationRecord().time() + " | "), t);
        assertTrue(t.contains(" | 김간호\n"), t);
        assertTrue(t.contains("의식 수준: 기면(drowsy)"), t);
        assertTrue(t.contains("5 Rights: 약물 ✓"), t);
        assertTrue(t.contains("서명: 김간호"), t);
        String paragraph = ResultView.handoffParagraph(a.sbar());
        var s = a.sbar();
        assertTrue(paragraph.startsWith(s.situation().strip().replaceAll("\\s*\\R\\s*", " ")), paragraph);
        assertTrue(paragraph.indexOf(s.assessment().strip().substring(0, 5)) > paragraph.indexOf(s.background().strip().substring(0, 5)));
        assertTrue(paragraph.endsWith(s.recommendations().get(s.recommendations().size() - 1).intervention().strip() + "."), paragraph);
        // 칸 끝에 문장부호가 없으면 마침표를 붙여 문장 경계가 보이게 한다
        assertEquals("환자가 초조 증상을 보임. 조현병 환자임. 투약 후 차분해짐! 18:30 정규 투약 및 상태 관찰.",
                ResultView.handoffParagraph(new app.domain.SbarReport("환자가 초조 증상을 보임", "조현병 환자임.", "투약 후 차분해짐!",
                        List.of(new app.domain.SbarRecommendation("18:30", "정규 투약 및 상태 관찰")))));
        assertFalse(paragraph.contains("\n"));
        assertTrue(t.contains("[실제 인계 기록 형태]\n" + paragraph + " (서명: 김간호)"), t);
        assertTrue(t.contains("실제 인계 기록은 칸을 나누지 않고 이 순서로 한 문단으로 씁니다."));
        assertTrue(app.export.ResultExporter.content(a, "김간호").contains("[실제 인계 기록 형태]"));
        // 다시 보기: 정신상태 평가도 그때 값으로, 고칠 수 없다
        assertTrue(state.showResult(a));
        assertEquals("기면(drowsy)", state.mentalStatus().consciousness());
        assertThrows(IllegalStateException.class, () -> state.setMentalStatus(new MentalStatus("명료(alert)", null, null, null, null, null)));
        assertFalse(new ObservationController(state).saveMentalStatus("명료(alert)", "", "", "", "", "").saved());
        // 새 실습이면 비어 있다
        state.startPractice(pc01);
        assertNull(state.mentalStatus());
    }

    @Test
    void 결과의_투약_줄은_단위와_구강_확인을_보인다() {
        // Windows 실습 검토: "Lorazepam 1 PO 09:30 투여"처럼 단위가 빠지던 것을 "1mg"로, 구강 확인 상태를 함께
        AppState state = practice();
        assertTrue(new MedicationController(state).save(prn(), "1", "mg", "PO", "09:30", MedicationResult.GIVEN, "불안",
                "PRN 처방", true, null, FiveRights.ALL).saved());
        var s = pc01.modelAnswer().sbarReport();
        var r = new SbarController(state, new InMemoryAttemptRepository(), () -> 1L)
                .submit(s.situation(), s.background(), s.assessment(), s.recommendations());
        assertTrue(r.submitted(), r.message());
        String line = "1. Lorazepam 1mg PO 09:30 투여 · 구강 확인 ✓ | 5 Rights: 약물 ✓ · 용량 ✓ · 대상자 ✓ · 경로 ✓ · 시간 ✓ | 서명 김간호";
        assertTrue(ResultView.submittedText(r.attempt(), "김간호").contains(line), ResultView.submittedText(r.attempt(), "김간호"));
        // 내보내기 파일도 같은 줄
        assertTrue(app.export.ResultExporter.content(r.attempt(), "김간호").contains(line));
        assertEquals("구강 확인 ✗", ResultView.oralText(false));
        assertEquals("구강 확인 기록 없음", ResultView.oralText(null));
    }

    @Test
    void R_행_끝_마침표는_쉼표와_겹치지_않는다() {
        assertEquals("상황. 배경. 평가. 18:30 탄산리튬 투약 확인 바랍니다, 23:30 관찰 바랍니다.",
                ResultView.handoffParagraph(new app.domain.SbarReport("상황", "배경", "평가", List.of(
                        new app.domain.SbarRecommendation("18:30", "탄산리튬 투약 확인 바랍니다."),
                        new app.domain.SbarRecommendation("23:30", "관찰 바랍니다.")))));
    }

    @Test
    void 예전_기록은_5원칙_없음으로_보인다() {
        assertEquals("5 Rights: 기록 없음 (이 기능 전에 저장한 기록)", FiveRights.text(null));
        assertEquals("5 Rights: 약물 ✓ · 용량 ✗ · 대상자 ✓ · 경로 ✓ · 시간 ✓",
                FiveRights.text(new FiveRights(true, false, true, true, true)));
    }
}

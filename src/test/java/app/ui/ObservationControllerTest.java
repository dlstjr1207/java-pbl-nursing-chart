package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.Shift;
import app.domain.VitalSign;
import app.ui.ObservationController.Metric;
import app.ui.ObservationController.SaveResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 관찰 입력 검증·저장·시각순 표시·차트 데이터를 확인한다 (S3). 값은 모두 합성 테스트 값이다. */
class ObservationControllerTest {
    private AppState state;
    private ObservationController controller;

    @BeforeEach
    void setUp() {
        PatientCase demo = new CaseLoader().load("demo");
        // DEMO 케이스에 합성 활력징후·기존 로그를 얹은 테스트용 케이스
        PatientCase c = new PatientCase(demo.id(), demo.title(), demo.difficulty(), demo.objectives(),
                demo.reviewStatus(), demo.source(), demo.patient(), demo.adr(), demo.riskTier(),
                demo.orders(), demo.regimenHistory(), demo.lithiumLevel(),
                List.of(new VitalSign("m1", "08:00", 120, 80, 70, 16, 36.5, 98),
                        new VitalSign("m2", "12:00", 130, 85, 90, 18, null, 97)),
                List.of(new ObservationEntry("09:00", Shift.DAY, List.of("기타"), "기존 기록", null, null)),
                demo.safetyRounds(), demo.briefing(), demo.sbarReference(), demo.answerKey(), demo.modelAnswer(),
                demo.rawNotes(), demo.task());
        state = new AppState();
        state.login("테스터");
        state.startPractice(c);
        controller = new ObservationController(state);
    }

    private SaveResult saveValid(String time, String narrative, Map<Metric, String> vitals) {
        return controller.save(time, Shift.DAY, List.of("안정 회복"), narrative, "  ", vitals);
    }

    @Test
    void 정상_입력은_그대로_보존되어_저장된다() {
        SaveResult r = controller.save("10:30", Shift.EVENING, List.of("불안 급증", "PRN 투여"),
                " 서술 원문 ", "심호흡 유도", Map.of(Metric.PULSE, "88", Metric.TEMPERATURE, "36.8"));
        assertTrue(r.saved());
        ObservationEntry e = state.studentObservations().get(0);
        assertEquals("10:30", e.time());
        assertEquals(Shift.EVENING, e.shift());
        assertEquals(List.of("불안 급증", "PRN 투여"), e.tags());
        assertEquals(" 서술 원문 ", e.narrative());
        assertEquals("심호흡 유도", e.intervention());
        assertEquals(88, e.vitals().pulse());
        assertEquals(36.8, e.vitals().temperature());
        assertNull(e.vitals().systolic());
    }

    @Test
    void 빈_서술은_저장하지_않고_서술_칸에_안내한다() {
        SaveResult r = saveValid("10:30", "   ", Map.of());
        assertFalse(r.saved());
        assertTrue(r.errors().byField().containsKey("narrative"));
        assertTrue(state.studentObservations().isEmpty());
    }

    @Test
    void 시각_근무_태그가_없으면_각각_안내한다() {
        SaveResult r = controller.save("25:99", null, List.of(), "내용", null, Map.of());
        assertFalse(r.saved());
        assertEquals(java.util.Set.of("time", "shift", "tags"), r.errors().byField().keySet());
        assertTrue(controller.save("", Shift.DAY, List.of("기타"), "내용", null, Map.of())
                .errors().byField().containsKey("time"));
    }

    @Test
    void 잘못된_활력징후_숫자는_해당_칸에_안내한다() {
        SaveResult r = saveValid("10:30", "내용", Map.of(Metric.PULSE, "abc", Metric.SPO2, "9.5"));
        assertFalse(r.saved());
        assertTrue(r.errors().byField().containsKey("vital.PULSE"));
        assertTrue(r.errors().byField().containsKey("vital.SPO2"));
    }

    @Test
    void 처치와_활력징후가_없으면_null로_저장한다() {
        assertTrue(saveValid("10:30", "내용", Map.of()).saved());
        ObservationEntry e = state.studentObservations().get(0);
        assertNull(e.intervention());
        assertNull(e.vitals());
    }

    @Test
    void 관찰_목록에는_학생_기록만_시각순으로_보이고_기존_로그는_숨긴다() {
        saveValid("10:30", "학생 나중", Map.of());
        saveValid("08:30", "학생 먼저", Map.of());
        List<ObservationController.TimelineRow> rows = controller.timeline();
        assertEquals(List.of("08:30", "10:30"), rows.stream().map(r -> r.time()).toList());
        assertTrue(rows.stream().noneMatch(r -> r.fromCase()));
        assertTrue(rows.stream().allMatch(r -> r.text().startsWith("[학생]")));
        // 기존 로그 데이터는 지우지 않는다 (AI 해설 자료)
        assertEquals(1, state.currentCase().observationLog().size());
    }

    @Test
    void 기록이_없으면_목록이_비어_있다() {
        assertTrue(controller.timeline().isEmpty());
    }

    @Test
    void 차트_데이터는_케이스_활력징후와_학생_입력을_시각순으로_일치시킨다() {
        saveValid("10:00", "내용", Map.of(Metric.PULSE, "100"));
        saveValid("11:00", "체온 없음", Map.of(Metric.SYSTOLIC, "140"));
        List<ObservationController.ChartPoint> pulse = controller.chartPoints(Metric.PULSE);
        assertEquals(List.of("08:00", "10:00", "12:00"), pulse.stream().map(p -> p.time()).toList());
        assertEquals(List.of(70.0, 100.0, 90.0), pulse.stream().map(p -> p.value()).toList());
        assertEquals(List.of(true, false, true), pulse.stream().map(p -> p.fromCase()).toList());
        // 측정하지 않은 항목(2번째 체온 null)은 점이 없다.
        assertEquals(1, controller.chartPoints(Metric.TEMPERATURE).size());
        assertEquals(3, controller.chartPoints(Metric.SYSTOLIC).size());
    }

    @Test
    void 새_실습과_로그아웃은_학생_기록을_비운다() {
        saveValid("10:00", "내용", Map.of());
        state.logout();
        assertTrue(state.studentObservations().isEmpty());
        state.login("다른사용자");
        assertTrue(state.studentObservations().isEmpty());
    }
}

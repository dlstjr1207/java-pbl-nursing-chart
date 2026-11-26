package app.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.MedicationRecord;
import app.domain.MedicationResult;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PC-01 정답 키로 채점을 검증한다 (단계 4, 시나리오 V1~V7, D33 배점).
 * 모범 기록은 만점이고, 변형마다 해당 규칙이 지적된다.
 */
class Pc01ScoringTest {
    private final PatientCase pc01 = new CaseLoader().load("PC-01");
    private final ObservationEntry obs = pc01.modelAnswer().observationRecord();
    private final MedicationRecord med = pc01.modelAnswer().medicationRecord();
    private final SbarReport sbar = pc01.modelAnswer().sbarReport();

    private Scorer.Result evaluate(ObservationEntry o, MedicationRecord m, SbarReport s) {
        return Scorer.evaluate(pc01, List.of(o), List.of(m), s);
    }

    private static List<CheckResult> of(Scorer.Result r, String rule, CheckStatus st) {
        return r.checks().stream().filter(c -> c.ruleId().equals(rule) && c.status() == st).toList();
    }

    private static boolean flagged(Scorer.Result r, String rule) {
        return r.checks().stream().anyMatch(c -> c.ruleId().equals(rule)
                && (c.status() == CheckStatus.MISSING || c.status() == CheckStatus.MISMATCH));
    }

    private ObservationEntry obsWith(String narrative) {
        return new ObservationEntry(obs.time(), obs.shift(), obs.tags(), narrative, obs.intervention(), obs.vitals());
    }

    @Test
    void 모범_기록은_모든_점검을_통과하고_만점이다() {
        Scorer.Result r = evaluate(obs, med, sbar);
        List<CheckResult> bad = r.checks().stream()
                .filter(c -> c.status() != CheckStatus.PASS).toList();
        assertTrue(bad.isEmpty(), "통과하지 못한 점검: " + bad);
        assertEquals(100.0, r.score());
        assertEquals(Scorer.WEIGHTS.keySet(), r.ruleScores().keySet());
    }

    @Test
    void V1_판단_표현은_R1_피드백만_주고_점수는_그대로다() {
        Scorer.Result r = evaluate(obsWith(obs.narrative() + " 오후에는 협조적임."), med, sbar);
        assertEquals(1, of(r, "R1", CheckStatus.FEEDBACK).size());
        assertTrue(of(r, "R1", CheckStatus.FEEDBACK).get(0).message().contains("관찰한 행동으로 바꿔 쓰세요"));
        assertEquals(100.0, r.score());
    }

    @Test
    void 비협조적은_한_번만_지적한다() {
        Scorer.Result r = evaluate(obsWith(obs.narrative() + " 비협조적임."), med, sbar);
        assertEquals(1, of(r, "R1", CheckStatus.FEEDBACK).size());
    }

    @Test
    void V2_발언_인용이_없으면_R1을_지적하고_10점이_빠진다() {
        Scorer.Result r = evaluate(obsWith(obs.narrative().replace("\"", "")), med, sbar);
        assertTrue(flagged(r, "R1"));
        assertEquals(90.0, r.score());
    }

    @Test
    void V3_용량이_다르면_R7에서_입력값과_기준값을_보여_준다() {
        MedicationRecord wrong = new MedicationRecord(med.orderId(), med.drugName(), "0.5", med.unit(), med.route(),
                med.administeredTime(), med.result(), med.reason(), med.orderBasis(), med.oralCheck(), med.reassessmentPlan());
        Scorer.Result r = evaluate(obs, wrong, sbar);
        List<CheckResult> mismatch = of(r, "R7", CheckStatus.MISMATCH);
        assertEquals(1, mismatch.size());
        assertTrue(mismatch.get(0).message().contains("입력 '0.5', 기준 '1'"), mismatch.get(0).message());
        assertEquals(95.0, r.score()); // R7 25점 중 1/5 감점
    }

    @Test
    void V4_재평가_계획이_관찰과_투약에_모두_없으면_R4를_지적한다() {
        ObservationEntry noPlan = new ObservationEntry(obs.time(), obs.shift(), obs.tags(),
                "데이룸에서 서성이며 손톱을 물어뜯는 모습이 관찰됨. 환자는 \"누가 나를 감시하는 것 같아요\"라고 말함. "
                        + "조용한 곳으로 이동해 1:1로 지지적 대화를 함. 09:30 로라제팜 1mg PO 투여함.",
                obs.intervention(), null);
        Scorer.Result r = evaluate(noPlan, medPlan(null), sbar);
        assertTrue(flagged(r, "R4"));
        assertEquals(0.0, r.ruleScores().get("R4"));
        // 투약 기록에만 계획이 있으면 통과
        assertFalse(flagged(evaluate(noPlan, med, sbar), "R4"));
    }

    private MedicationRecord medPlan(String plan) {
        return new MedicationRecord(med.orderId(), med.drugName(), med.dose(), med.unit(), med.route(),
                med.administeredTime(), med.result(), med.reason(), med.orderBasis(), med.oralCheck(), plan);
    }

    private ObservationEntry noPlanObs() {
        return obsWith(obs.narrative().replace("10:00(투여 30분 후) 불안 정도 재평가 예정.", ""));
    }

    @Test
    void R4는_재평가_낱말과_시각이_함께_있어야_통과한다() {
        ObservationEntry o = noPlanObs();
        for (String ok : List.of("10:00 불안 VAS·RASS 재평가", "30분 후 재평가", "1시간 뒤 불안 정도 재사정",
                "재측정 예정(10:30)", "11시 재확인")) {
            assertFalse(flagged(evaluate(o, medPlan(ok), sbar), "R4"), ok);
        }
        for (String bad : List.of("재평가", "추후 재평가 예정", "10:00 활력징후 측정", "불안 재사정 필요")) {
            assertTrue(flagged(evaluate(o, medPlan(bad), sbar), "R4"), bad);
        }
    }

    @Test
    void R4는_부정_표현이_붙으면_통과하지_않는다() {
        ObservationEntry o = noPlanObs();
        for (String bad : List.of("10:00 재평가 안 함", "재평가 하지 않음(10:00)", "10:00 재평가 계획 없음",
                "30분 후 재사정 생략", "10:00 재평가안함")) {
            assertTrue(flagged(evaluate(o, medPlan(bad), sbar), "R4"), bad);
        }
        // 재평가 결과를 적은 "이상 없음"은 부정이 아니다
        assertFalse(flagged(evaluate(o, medPlan("10:00 재평가 이상 없음"), sbar), "R4"));
        // 한 문장은 부정이어도 다른 문장에 시각 있는 계획이 있으면 통과
        assertFalse(flagged(evaluate(o, medPlan("09:45 재평가 생략. 10:00 재평가 예정"), sbar), "R4"));
    }

    @Test
    void PRN_투여에_구강_확인을_체크하지_않으면_R6을_지적한다() {
        MedicationRecord unchecked = new MedicationRecord(med.orderId(), med.drugName(), med.dose(), med.unit(), med.route(),
                med.administeredTime(), med.result(), med.reason(), med.orderBasis(), false, med.reassessmentPlan());
        Scorer.Result r = evaluate(obs, unchecked, sbar);
        assertEquals(List.of("medication[1].oralCheck"), of(r, "R6", CheckStatus.MISSING).stream().map(CheckResult::targetRef).toList());
        assertEquals(98.1, r.score()); // R6 15점 중 1/8 감점
    }

    @Test
    void V5_B가_비면_R8을_지적하지만_R8은_점수에_넣지_않는다() {
        SbarReport noB = new SbarReport(sbar.situation(), "", sbar.assessment(), sbar.recommendations());
        Scorer.Result r = evaluate(obs, med, noB);
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R8")
                && c.targetRef().equals("background") && c.status() == CheckStatus.MISSING));
        assertFalse(r.ruleScores().containsKey("R8"));
        // B 칸의 R9 필수 요소(진단·입원 14일·현재 처방) 3개가 빠져 R9 10점 중 3/9가 빠진다
        assertEquals(96.7, r.score());
    }

    @Test
    void V6_거부인데_사유가_없으면_R6을_지적한다() {
        MedicationRecord refused = new MedicationRecord(med.orderId(), med.drugName(), med.dose(), med.unit(), med.route(),
                med.administeredTime(), MedicationResult.REFUSED, "", med.orderBasis(), med.oralCheck(), med.reassessmentPlan());
        assertTrue(flagged(evaluate(obs, refused, sbar), "R6"));
    }

    @Test
    void V7_R_행의_시각이_빠지면_R8과_R9_요소를_지적한다() {
        SbarReport noTime = new SbarReport(sbar.situation(), sbar.background(), sbar.assessment(),
                List.of(new SbarRecommendation("", "탄산리튬 300mg PO 투약"), sbar.recommendations().get(1)));
        Scorer.Result r = evaluate(obs, med, noTime);
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R8")
                && c.targetRef().equals("rec1.time") && c.status() == CheckStatus.MISSING));
        // 시각이 빠져 R 요소 '18:30 리튬 투약'을 찾지 못한다 (R9 10점 중 1/9)
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R9")
                && c.targetRef().equals("rec.element[1]") && c.status() == CheckStatus.MISSING));
        assertEquals(98.9, r.score());
    }

    @Test
    void SBAR_R에_18시30분_리튬이_빠지면_R9에서_감점된다() {
        SbarReport noLithium = new SbarReport(sbar.situation(), sbar.background(), sbar.assessment(),
                List.of(new SbarRecommendation("16:00", "불안·초조 재발 여부 관찰")));
        Scorer.Result r = evaluate(obs, med, noLithium);
        assertEquals(8.9, r.ruleScores().get("R9"));
        assertEquals(98.9, r.score());
    }

    @Test
    void 대표_기록_6가지의_점수() {
        java.util.Map<String, Double> expected = new java.util.LinkedHashMap<>();
        expected.put("모범 기록", 100.0);
        expected.put("메모 통째로 붙여 넣기", 95.0);
        expected.put("관련 없는 정보 포함", 95.0);
        expected.put("재평가 안 함", 90.0);
        expected.put("구강 확인 미체크", 98.1);
        expected.put("SBAR R에 18:30 리튬 누락", 98.9);
        java.util.Map<String, Double> actual = new java.util.LinkedHashMap<>();
        app.tools.ScoreTable.records(pc01).forEach((name, rec) ->
                actual.put(name, Scorer.evaluate(pc01, List.of(rec.obs()), List.of(rec.med()), rec.sbar()).score()));
        assertEquals(expected, actual);
    }

    @Test
    void 관찰_투약_기록_없이_SBAR만_제출하면_R8_누락이_남지만_점수는_다른_규칙에서_깎인다() {
        // 화면은 SBAR 칸의 빈칸만 막고, 관찰·투약 기록 0건 제출은 막지 않는다 (R8이 항상 만점은 아니다)
        Scorer.Result r = Scorer.evaluate(pc01, List.of(), List.of(), sbar);
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R8") && c.targetRef().equals("observation")));
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R8") && c.targetRef().equals("medication")));
        assertFalse(r.ruleScores().containsKey("R8"));
        assertEquals(0.0, r.ruleScores().get("R2"));
        assertEquals(0.0, r.ruleScores().get("R7"));
        assertEquals(23.5, r.score()); // R9·R10만 통과 (20 / 85)
    }

    @Test
    void 배점은_D33이고_R8은_빠진다() {
        assertEquals(java.util.Map.of("R7", 25, "R2", 20, "R6", 15, "R1", 10, "R4", 10, "R9", 10, "R10", 10), Scorer.WEIGHTS);
        assertEquals(100, Scorer.WEIGHTS.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void 핵심_사실이_빠지면_R2를_요소별로_지적한다() {
        Scorer.Result r = evaluate(obsWith("환자가 불안해 보임."), med, sbar);
        List<CheckResult> missing = of(r, "R2", CheckStatus.MISSING);
        // 처치 칸(간호·투약)은 모범 기록 그대로라 서술에서 빠진 장소·행동·발언·재평가 4개만 지적된다
        assertEquals(4, missing.size(), missing.toString());
        assertTrue(missing.stream().anyMatch(c -> c.message().contains("장소")));
    }

    @Test
    void 정답_키가_없는_케이스는_R7_R2를_빼고_비율로_환산한다() {
        PatientCase demo = new CaseLoader().load("demo");
        Scorer.Result r = Scorer.evaluate(demo, List.of(obs), List.of(med), sbar);
        assertFalse(r.ruleScores().containsKey("R7"));
        assertFalse(r.ruleScores().containsKey("R2"));
        assertEquals(100.0, r.score()); // 남은 규칙(R6·R1·R4)을 모두 통과 (원자료·SBAR 요소가 없어 R10·R9도 빠진다)
        assertFalse(r.ruleScores().containsKey("R10"));
    }
}

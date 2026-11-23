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
 * PC-01 정답 키로 채점을 검증한다 (단계 4, 시나리오 V1~V7, D30 배점).
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
        MedicationRecord noPlanMed = new MedicationRecord(med.orderId(), med.drugName(), med.dose(), med.unit(), med.route(),
                med.administeredTime(), med.result(), med.reason(), med.orderBasis(), med.oralCheck(), null);
        Scorer.Result r = evaluate(noPlan, noPlanMed, sbar);
        assertTrue(flagged(r, "R4"));
        // 투약 기록에만 계획이 있으면 통과
        assertFalse(flagged(evaluate(noPlan, med, sbar), "R4"));
    }

    @Test
    void V5_B가_비면_R8을_지적한다() {
        SbarReport noB = new SbarReport(sbar.situation(), "", sbar.assessment(), sbar.recommendations());
        Scorer.Result r = evaluate(obs, med, noB);
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R8")
                && c.targetRef().equals("background") && c.status() == CheckStatus.MISSING));
    }

    @Test
    void V6_거부인데_사유가_없으면_R6을_지적한다() {
        MedicationRecord refused = new MedicationRecord(med.orderId(), med.drugName(), med.dose(), med.unit(), med.route(),
                med.administeredTime(), MedicationResult.REFUSED, "", med.orderBasis(), med.oralCheck(), med.reassessmentPlan());
        assertTrue(flagged(evaluate(obs, refused, sbar), "R6"));
    }

    @Test
    void V7_R_행의_시각이_빠지면_R9를_지적한다() {
        SbarReport noTime = new SbarReport(sbar.situation(), sbar.background(), sbar.assessment(),
                List.of(new SbarRecommendation("", "탄산리튬 300mg PO 투약"), sbar.recommendations().get(1)));
        Scorer.Result r = evaluate(obs, med, noTime);
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R9")
                && c.targetRef().equals("rec1.time") && c.status() == CheckStatus.MISSING));
        // R9는 피드백만: 점수에 들어가지 않는다 (R8의 시각 누락만 반영)
        assertFalse(r.ruleScores().containsKey("R9"));
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
        assertEquals(100.0, r.score()); // 남은 규칙(R8·R6·R1·R4)을 모두 통과
    }
}

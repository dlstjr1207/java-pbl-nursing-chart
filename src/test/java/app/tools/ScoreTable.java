package app.tools;

import app.cases.CaseLoader;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.MedicationRecord;
import app.domain.ModelAnswer;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import app.scoring.RawNotesChecker;
import app.scoring.Scorer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 채점 기준을 바꿀 때 PC-01의 대표 기록 6가지 점수를 표로 보는 점검 도구 (개발용, 프로그램에 포함 안 됨).
 * 실제 제출과 같은 Scorer.evaluate를 쓴다. 사용: ScoreTable
 */
public final class ScoreTable {
    public static void main(String[] args) {
        PatientCase c = new CaseLoader().load("PC-01");
        for (Map.Entry<String, Rec> e : records(c).entrySet()) {
            Rec r = e.getValue();
            Scorer.Result s = Scorer.evaluate(c, List.of(r.obs()), List.of(r.med()), r.sbar());
            List<String> bad = s.checks().stream()
                    .filter(x -> x.status() == CheckStatus.MISSING || x.status() == CheckStatus.MISMATCH)
                    .map(ScoreTable::brief).toList();
            System.out.println(e.getKey() + " | " + s.score() + " | " + s.ruleScores() + " | " + bad);
        }
    }

    private static String brief(CheckResult x) {
        return x.ruleId() + ":" + x.targetRef();
    }

    /** 한 번 제출하는 관찰·투약·SBAR 묶음. */
    public record Rec(ObservationEntry obs, MedicationRecord med, SbarReport sbar) {}

    /** 모범 기록과 다섯 가지 변형. 테스트에서도 같은 기록을 쓴다. */
    public static Map<String, Rec> records(PatientCase c) {
        ModelAnswer m = c.modelAnswer();
        ObservationEntry o = m.observationRecord();
        MedicationRecord med = m.medicationRecord();
        SbarReport s = m.sbarReport();
        Map<String, Rec> out = new LinkedHashMap<>();
        out.put("모범 기록", new Rec(o, med, s));
        String pasted = RawNotesChecker.allNotes(c.rawNotes().stream().filter(n -> !n.irrelevant()).toList());
        out.put("메모 통째로 붙여 넣기", new Rec(obs(o, pasted), med, s));
        out.put("관련 없는 정보 포함", new Rec(obs(o, o.narrative() + " 08:05 옆 침상 환자 채혈 같이 들어감."), med,
                new SbarReport(s.situation(), s.background() + " 13:20 어머니가 주말 면회 시간 문의함.",
                        s.assessment(), s.recommendations())));
        out.put("재평가 안 함", new Rec(obs(o, o.narrative().replace("10:00(투여 30분 후) 불안 정도 재평가 예정.", "불안 정도 재평가 안 함.")),
                medWith(med, med.oralCheck(), "재평가 안 함"), s));
        out.put("구강 확인 미체크", new Rec(o, medWith(med, false, med.reassessmentPlan()), s));
        out.put("SBAR R에 18:30 리튬 누락", new Rec(o, med, new SbarReport(s.situation(), s.background(), s.assessment(),
                List.of(new SbarRecommendation("16:00", "불안·초조 재발 여부 관찰")))));
        return out;
    }

    private static ObservationEntry obs(ObservationEntry b, String narrative) {
        return new ObservationEntry(b.time(), b.shift(), b.tags(), narrative, b.intervention(), b.vitals());
    }

    private static MedicationRecord medWith(MedicationRecord m, Boolean oral, String plan) {
        return new MedicationRecord(m.orderId(), m.drugName(), m.dose(), m.unit(), m.route(), m.administeredTime(),
                m.result(), m.reason(), m.orderBasis(), oral, plan);
    }
}

package app.scoring;

import app.domain.AnswerKey;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.MedicationRecord;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.SbarReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 제출한 기록 전체를 점검하고 점수를 낸다 (D33, D30을 바꿈).
 * 배점: R7 25, R2 20, R6 15, R1 10, R4 10, R9 10, R10 10. R5는 제외.
 * R8(필수 입력)은 점검 결과만 남기고 점수에서 뺀다: 화면이 빈칸 제출을 막아 거의 항상 만점이라 변별력이 없다.
 * 규칙마다 (통과 수 / 산출된 점검 수) × 배점을 더하고, 산출할 수 없는 규칙(예: 원자료 메모가 없는 예전 케이스의 R10)은
 * 배점에서 빼 100점 비율로 환산한다.
 */
public final class Scorer {
    /** 규칙별 배점 (D33). 여기에 없는 규칙(R8)은 점수에 들어가지 않는다. */
    public static final Map<String, Integer> WEIGHTS = weights();

    private Scorer() {
    }

    /** 이전 배점 (D30): R7 25, R2 20, R8 20, R6 15, R1 10, R4 10. D33 전에 저장된 기록의 항목별 막대를 저장 당시 기준으로 보일 때만 쓴다. */
    public static final Map<String, Integer> WEIGHTS_D30 = weightsD30();

    /** 결과 화면 안내 문구 (예전 기록) */
    public static final String D30_NOTICE = "이전 채점 기준(D30)으로 채점된 기록입니다";

    private static Map<String, Integer> weightsD30() {
        Map<String, Integer> w = new LinkedHashMap<>();
        w.put("R7", 25);
        w.put("R2", 20);
        w.put("R8", 20);
        w.put("R6", 15);
        w.put("R1", 10);
        w.put("R4", 10);
        return java.util.Collections.unmodifiableMap(w);
    }

    /**
     * 저장된 점검 결과가 D33 전(D30 기준)에 만들어졌는지. 저장 형식을 바꾸지 않고 결과 모양으로 알아본다:
     * D30의 R9는 R 행마다 시각·중재 점검(rec1.time 등, 행이 없으면 rec)을 냈고, R10은 FEEDBACK 상태였다.
     * D33부터는 둘 다 나오지 않는다. 제출 화면은 R 행을 1행 이상 요구하므로 D30 기록에는 항상 rec1.time이 있다.
     */
    public static boolean scoredUnderD30(List<CheckResult> checks) {
        if (checks == null) return false;
        for (CheckResult r : checks) {
            if ("R9".equals(r.ruleId()) && r.targetRef() != null && r.targetRef().matches("rec(\\d+\\.(time|intervention))?")) {
                return true;
            }
            if ("R10".equals(r.ruleId()) && r.status() == CheckStatus.FEEDBACK) return true;
        }
        return false;
    }

    /** 이 점검 결과를 채점한 배점: D30 기록이면 WEIGHTS_D30, 아니면 WEIGHTS. */
    public static Map<String, Integer> weightsFor(List<CheckResult> checks) {
        return scoredUnderD30(checks) ? WEIGHTS_D30 : WEIGHTS;
    }

    private static Map<String, Integer> weights() {
        Map<String, Integer> w = new LinkedHashMap<>();
        w.put("R7", 25);
        w.put("R2", 20);
        w.put("R6", 15);
        w.put("R1", 10);
        w.put("R4", 10);
        w.put("R9", 10);
        w.put("R10", 10);
        return java.util.Collections.unmodifiableMap(w);
    }

    /** 점검 결과와 점수. score는 산출할 수 있는 규칙이 하나도 없으면 null. */
    public record Result(List<CheckResult> checks, Double score, Map<String, Double> ruleScores) {}

    /** 케이스의 정답 키로 모든 규칙을 점검하고 점수를 낸다. AI 생성 케이스도 같은 규칙을 쓴다. */
    public static Result evaluate(PatientCase c, List<ObservationEntry> observations,
                                  List<MedicationRecord> medications, SbarReport sbar) {
        AnswerKey key = c == null ? null : c.answerKey();
        List<CheckResult> checks = new ArrayList<>();
        checks.addAll(RequiredInputChecker.check(observations, medications, sbar)); // R8 결과만 (배점 없음)
        checks.addAll(MedicationCompletenessChecker.check(medications, c == null ? List.of() : c.orders()));
        checks.addAll(ValueMatchChecker.check(medications, key));
        checks.addAll(KeyFactChecker.check(observations, key));
        checks.addAll(ObservationWordingChecker.check(observations));
        checks.addAll(ReassessmentChecker.check(observations, medications));
        checks.addAll(SbarStructureChecker.check(sbar, key == null ? null : key.sbarElements()));
        checks.addAll(RawNotesChecker.check(c, observations, medications, sbar)); // R10 (원자료 메모가 없으면 미산출)
        return score(checks);
    }

    /** 점검 결과로 점수를 계산한다. D33 전에 저장된 기록이면 저장 당시 배점(D30)으로 계산한다. */
    public static Result score(List<CheckResult> checks) {
        return score(checks, weightsFor(checks));
    }

    /** 주어진 배점으로 점수를 계산한다. */
    public static Result score(List<CheckResult> checks, Map<String, Integer> weights) {
        Map<String, Double> ruleScores = new LinkedHashMap<>();
        double earned = 0;
        int possible = 0;
        for (Map.Entry<String, Integer> w : weights.entrySet()) {
            int scored = 0;
            int passed = 0;
            for (CheckResult r : checks) {
                if (!r.ruleId().equals(w.getKey())) continue;
                if (r.status() == CheckStatus.PASS) {
                    passed++;
                    scored++;
                } else if (r.status() == CheckStatus.MISSING || r.status() == CheckStatus.MISMATCH) {
                    scored++;
                }
            }
            if (scored == 0) continue; // 미산출 규칙은 배점에서 뺀다
            double s = w.getValue() * (double) passed / scored;
            ruleScores.put(w.getKey(), round(s));
            earned += s;
            possible += w.getValue();
        }
        Double total = possible == 0 ? null : round(earned * 100.0 / possible);
        return new Result(checks, total, ruleScores);
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}

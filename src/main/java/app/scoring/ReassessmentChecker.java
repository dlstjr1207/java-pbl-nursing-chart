package app.scoring;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.MedicationRecord;
import app.domain.ObservationEntry;
import java.util.List;
import java.util.regex.Pattern;

/**
 * R4 재평가 연결. 관찰 기록(서술·처치) 또는 투약 기록(재평가 계획)에 재평가 계획이 있는지 확인한다 (V4).
 */
public final class ReassessmentChecker {
    public static final String RULE = "R4";
    private static final Pattern PLAN = Pattern.compile("재평가|재사정|재측정|재확인");

    private ReassessmentChecker() {
    }

    public static List<CheckResult> check(List<ObservationEntry> observations, List<MedicationRecord> medications) {
        boolean found = false;
        if (medications != null) {
            for (MedicationRecord m : medications) {
                if (m.reassessmentPlan() != null && !m.reassessmentPlan().isBlank()) found = true;
            }
        }
        if (observations != null) {
            for (ObservationEntry o : observations) {
                String t = (o.narrative() == null ? "" : o.narrative()) + " " + (o.intervention() == null ? "" : o.intervention());
                if (PLAN.matcher(t).find()) found = true;
            }
        }
        return List.of(new CheckResult(RULE, CheckTarget.MEDICATION, "reassessment",
                found ? CheckStatus.PASS : CheckStatus.MISSING,
                found ? "재평가 계획이 있습니다."
                      : "재평가 계획(언제 무엇을 다시 확인할지)을 관찰 기록이나 투약 기록에 적으세요.", null));
    }
}

package app.scoring;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.MentalStatus;

import java.util.List;

/**
 * 정신상태 평가(MSE) 점검 (D38, B4). 배점 없이 피드백만 준다. 정답 키와 비교하지 않고, 적었는지만 본다.
 */
public final class MentalStatusChecker {
    /** 규칙 ID (배점 표 WEIGHTS에 없으므로 점수에 들어가지 않는다) */
    public static final String RULE = "MSE";
    public static final String MISSING_MESSAGE = "정신상태 평가를 기록하세요.";
    public static final String DONE_MESSAGE = "정신상태 평가를 기록했습니다.";

    private MentalStatusChecker() {
    }

    public static List<CheckResult> check(MentalStatus m) {
        if (MentalStatus.blank(m)) {
            return List.of(new CheckResult(RULE, CheckTarget.OBSERVATION, "mse", CheckStatus.FEEDBACK, MISSING_MESSAGE, null));
        }
        return List.of(new CheckResult(RULE, CheckTarget.OBSERVATION, "mse", CheckStatus.PASS, DONE_MESSAGE, null));
    }
}

package app.domain;

/** 점검 결과 상태 (check_results.status). 기준값이 없으면 NOT_SCORED(미산출)로 둔다. */
public enum CheckStatus {
    PASS,
    MISSING,
    MISMATCH,
    NOT_SCORED,
    /** 배점 없이 보여 주는 피드백 (예: R1 판단 표현, D30) */
    FEEDBACK
}

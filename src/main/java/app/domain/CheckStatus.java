package app.domain;

/** 점검 결과 상태 (check_results.status). 기준값이 없으면 NOT_SCORED(미산출)로 둔다. */
public enum CheckStatus {
    PASS,
    MISSING,
    MISMATCH,
    NOT_SCORED
}

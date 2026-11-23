package app.domain;

/** 투약 결과. 거부·보류는 사유가 필수다 (R6). */
public enum MedicationResult {
    /** 투여 */
    GIVEN,
    /** 거부 */
    REFUSED,
    /** 보류 */
    HELD
}

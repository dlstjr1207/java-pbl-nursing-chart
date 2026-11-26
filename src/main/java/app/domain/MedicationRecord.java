package app.domain;

/**
 * 학생이 작성한 투약 기록 1건 (F-05). 용량이 처방과 달라도 저장하고 R7에서 지적한다.
 *
 * @param orderId          참조한 케이스 처방 식별자
 * @param drugName         약명
 * @param dose             용량 (입력 그대로)
 * @param unit             단위
 * @param route            경로
 * @param administeredTime 투약 시각 (HH:mm)
 * @param result           투여·거부·보류
 * @param reason           사유 (PRN·거부·보류는 필수, R6)
 * @param orderBasis       처방 근거
 * @param oralCheck        구강 확인 여부 (입력하지 않았으면 null)
 * @param reassessmentPlan 재평가 계획
 * @param rights           투약 5원칙 확인 (D38. 이 기능 전에 저장한 기록이면 null)
 */
public record MedicationRecord(
        String orderId,
        String drugName,
        String dose,
        String unit,
        String route,
        String administeredTime,
        MedicationResult result,
        String reason,
        String orderBasis,
        Boolean oralCheck,
        String reassessmentPlan,
        FiveRights rights) {

    /** 5원칙 확인이 없는 기록 (D38 전 기록·테스트용) */
    public MedicationRecord(String orderId, String drugName, String dose, String unit, String route,
                            String administeredTime, MedicationResult result, String reason, String orderBasis,
                            Boolean oralCheck, String reassessmentPlan) {
        this(orderId, drugName, dose, unit, route, administeredTime, result, reason, orderBasis, oralCheck,
                reassessmentPlan, null);
    }

    /** 5원칙 확인을 붙인 사본 */
    public MedicationRecord withRights(FiveRights r) {
        return new MedicationRecord(orderId, drugName, dose, unit, route, administeredTime, result, reason, orderBasis,
                oralCheck, reassessmentPlan, r);
    }
}

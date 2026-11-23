package app.domain;

/**
 * 측정 시각별 활력징후 (vitals, 관찰 기록의 선택적 활력징후). 측정하지 않은 항목은 null이다.
 *
 * @param measurementId 측정 식별자 (R7에서 측정 시점 구별, 학생 입력이면 null 가능)
 * @param time          측정 시각 (HH:mm)
 * @param systolic      수축기 혈압
 * @param diastolic     이완기 혈압
 * @param pulse         맥박
 * @param respiration   호흡수
 * @param temperature   체온
 * @param spo2          산소포화도
 */
public record VitalSign(
        String measurementId,
        String time,
        Integer systolic,
        Integer diastolic,
        Integer pulse,
        Integer respiration,
        Double temperature,
        Integer spo2) {
}

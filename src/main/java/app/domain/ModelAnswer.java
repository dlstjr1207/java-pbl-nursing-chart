package app.domain;

/**
 * 모범 기록 (제출 후 공개, 참고용). 정답 키의 요소를 모두 담은 기록이며 모든 점검을 통과해야 한다(만점 기준).
 * 문장(observation·medication·sbar)은 화면 표시용, 구조화된 기록(…Record, sbarReport)은 만점 테스트용이다.
 * 검토 전이면 null로 두고 화면에 "준비 중"을 표시한다.
 */
public record ModelAnswer(
        String observation,
        String medication,
        String sbar,
        ObservationEntry observationRecord,
        MedicationRecord medicationRecord,
        SbarReport sbarReport) {

    /** 문장만 있는 모범 기록 (구조화된 기록 없음). */
    public ModelAnswer(String observation, String medication, String sbar) {
        this(observation, medication, sbar, null, null, null);
    }
}

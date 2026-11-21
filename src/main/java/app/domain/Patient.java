package app.domain;

/**
 * 가상 환자 기본 정보 (F-02). 값은 입력 그대로 보관한다.
 *
 * @param name          이름
 * @param age           나이 (모르면 null)
 * @param sex           성별
 * @param patientNo     등록번호
 * @param room          병실
 * @param diagnosis     진단
 * @param admissionType 입원 유형 (C-02 확정 전이면 null 또는 [확인필요])
 * @param admissionDate 입원일
 * @param hospitalDay   입원 일수 (모르면 null)
 */
public record Patient(
        String name,
        Integer age,
        String sex,
        String patientNo,
        String room,
        String diagnosis,
        String admissionType,
        String admissionDate,
        Integer hospitalDay) {
}

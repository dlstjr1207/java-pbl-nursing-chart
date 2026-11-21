package app.domain;

/**
 * 검사 결과 1건 (lithiumLevel 등). 값은 입력 그대로 문자열로 보관한다.
 *
 * @param time  측정 시각 또는 날짜
 * @param value 결과 값
 * @param unit  단위
 */
public record LabResult(String time, String value, String unit) {
}

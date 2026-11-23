package app.domain;

/**
 * SBAR의 R(권고) 행 1개. 시각과 중재를 가진다 (R9).
 *
 * @param time         중재 시각 (HH:mm)
 * @param intervention 중재 내용
 */
public record SbarRecommendation(String time, String intervention) {
}

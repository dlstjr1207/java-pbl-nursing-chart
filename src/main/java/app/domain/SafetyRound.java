package app.domain;

/**
 * 안전 라운딩 기록 1건 (safetyRounds). 참고 정보로만 표시한다.
 *
 * @param time 라운딩 시각
 * @param note 확인 내용
 */
public record SafetyRound(String time, String note) {
}

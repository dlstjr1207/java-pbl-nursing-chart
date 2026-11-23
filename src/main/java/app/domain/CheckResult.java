package app.domain;

/**
 * 규칙 점검 결과 1건 (F-08). 규칙 ID·입력 위치·이유를 가진다.
 *
 * @param ruleId    규칙 ID (R4, R6, R8, R9 등)
 * @param target    점검 대상 영역
 * @param targetRef 입력 위치 (기록 순번, 필드 이름 등)
 * @param status    통과·누락·불일치·미산출
 * @param message   학생에게 보여 줄 이유
 * @param points    점수 (가중치 미정이면 null)
 */
public record CheckResult(
        String ruleId,
        CheckTarget target,
        String targetRef,
        CheckStatus status,
        String message,
        Double points) {
}

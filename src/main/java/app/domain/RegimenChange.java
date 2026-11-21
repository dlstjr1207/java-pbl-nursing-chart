package app.domain;

/**
 * 처방 변경 이력 1건 (regimenHistory).
 *
 * @param date        변경일
 * @param description 변경 내용
 * @param reason      변경 이유
 */
public record RegimenChange(String date, String description, String reason) {
}

package app.domain;

/**
 * 모범 기록 (제출 후 공개, 참고용). 사실 데이터에서 만든 문장이며 채점에 쓰지 않는다.
 * 검토 전이면 null로 두고 화면에 "준비 중"을 표시한다.
 */
public record ModelAnswer(String observation, String medication, String sbar) {
}

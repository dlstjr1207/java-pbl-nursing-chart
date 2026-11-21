package app.domain;

import java.util.List;

/**
 * 정답 키 (제출 후 공개). 환자케이스 '정답 키와 모범 기록'의 입력별 필수 요소다.
 * 미확정이면 비워 두고 채점을 보류한다.
 *
 * @param observationElements 관찰 기록 필수 요소
 * @param medicationElements  투약 기록 필수 요소
 * @param sbarElements        SBAR 영역별 필수 요소 (없으면 null)
 */
public record AnswerKey(
        List<String> observationElements,
        List<String> medicationElements,
        SbarElements sbarElements) {

    public AnswerKey {
        observationElements = Lists.copy(observationElements);
        medicationElements = Lists.copy(medicationElements);
    }
}

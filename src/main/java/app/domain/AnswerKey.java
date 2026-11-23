package app.domain;

import java.util.List;

/**
 * 정답 키 (제출 후 공개). 환자케이스 '정답 키'의 입력별 필수 요소다. 미확정이면 비워 두고 채점을 보류한다.
 *
 * <p>요소 표기: {@code 이름=동의어1|동의어2}. 여러 조건이 모두 있어야 하면 {@code &}로 묶는다
 * (예: {@code 투약=로라제팜|lorazepam&1mg|1 mg&po|경구}). 대소문자와 띄어쓰기는 무시하고 포함 여부로 판정한다.
 *
 * @param observationElements 관찰 기록 핵심 사실(R2)
 * @param medicationElements  투약 기록 정답 값(R7). 이름은 약명·용량·단위·경로·시각
 *                            (해당 칸과 비교)
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

package app.domain;

import java.util.List;

/** SBAR 영역별 필수 요소 목록 (R9 기준). 목록이 비어 있으면 해당 영역은 미산출로 둔다. */
public record SbarElements(
        List<String> situation,
        List<String> background,
        List<String> assessment,
        List<String> recommendation) {

    public SbarElements {
        situation = Lists.copy(situation);
        background = Lists.copy(background);
        assessment = Lists.copy(assessment);
        recommendation = Lists.copy(recommendation);
    }
}

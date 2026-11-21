package app.domain;

import java.util.List;

/** 학생이 작성한 SBAR 인계 (F-06). S·B·A는 텍스트, R은 시각·중재 행 목록이다. */
public record SbarReport(
        String situation,
        String background,
        String assessment,
        List<SbarRecommendation> recommendations) {

    public SbarReport {
        recommendations = Lists.copy(recommendations);
    }
}

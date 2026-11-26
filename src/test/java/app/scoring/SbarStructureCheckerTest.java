package app.scoring;

import static org.junit.jupiter.api.Assertions.*;

import app.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R9 SBAR 구성(필수 요소) 테스트 (S5, D33). 합성 값만 쓴다. */
class SbarStructureCheckerTest {
    private static CheckResult find(List<CheckResult> rs, String ref) {
        return rs.stream().filter(r -> r.targetRef().equals(ref)).findFirst().orElseThrow();
    }

    @Test
    void 필수_요소_기준이_없으면_영역은_미산출이다() {
        var sbar = new SbarReport("s", "b", "a", List.of(new SbarRecommendation("09:00", "관찰")));
        var rs = SbarStructureChecker.check(sbar, null);
        assertEquals(CheckStatus.NOT_SCORED, find(rs, "situation").status());
        assertEquals(CheckStatus.NOT_SCORED, find(rs, "rec.element").status());
        assertEquals(4, rs.size()); // 영역마다 미산출 1개, R 행 점검은 R8이 한다
        assertTrue(rs.stream().allMatch(r -> r.ruleId().equals("R9") && r.points() == null));
    }

    @Test
    void 필수_요소_포함_여부를_영역별로_본다() {
        var el = new SbarElements(List.of("불안"), List.of("입원"), List.of("활력징후"), List.of("재평가"));
        var sbar = new SbarReport("환자가 불안 호소", "배경 없음", "활력징후 확인",
                List.of(new SbarRecommendation("10:00", "30분 후 재평가")));
        var rs = SbarStructureChecker.check(sbar, el);
        assertEquals(CheckStatus.PASS, find(rs, "situation.element[1]").status());
        assertEquals(CheckStatus.MISSING, find(rs, "background.element[1]").status());
        assertEquals(CheckStatus.PASS, find(rs, "assessment.element[1]").status());
        assertEquals(CheckStatus.PASS, find(rs, "rec.element[1]").status());
    }

    @Test
    void R_행의_시각_중재_빈칸은_R9에서_세지_않는다() {
        // 빈칸·형식 오류는 화면이 제출 전에 막고 R8이 점검한다. R9는 S·B·A·R 필수 요소만 센다 (D33).
        var el = new SbarElements(List.of(), List.of(), List.of(), List.of("18:30&리튬"));
        var sbar = new SbarReport("s", "b", "a", List.of(new SbarRecommendation("", "중재"),
                new SbarRecommendation("18:30", "리튬 투약")));
        var rs = SbarStructureChecker.check(sbar, el);
        assertTrue(rs.stream().noneMatch(r -> r.targetRef().startsWith("rec1") || r.targetRef().startsWith("rec2")));
        assertEquals(CheckStatus.PASS, find(rs, "rec.element[1]").status()); // 시각과 중재를 이어 붙인 글에서 찾는다
        var noLithium = SbarStructureChecker.check(new SbarReport("s", "b", "a",
                List.of(new SbarRecommendation("16:00", "관찰"))), el);
        assertEquals(CheckStatus.MISSING, find(noLithium, "rec.element[1]").status());
    }

    @Test
    void R_행이_없거나_SBAR가_null이어도_예외_없이_누락을_알린다() {
        var el = new SbarElements(List.of("x"), List.of(), List.of(), List.of("y"));
        var rs = SbarStructureChecker.check(null, el);
        assertEquals(CheckStatus.MISSING, find(rs, "situation.element[1]").status());
        assertEquals(CheckStatus.MISSING, find(rs, "rec.element[1]").status());
        assertEquals(CheckStatus.NOT_SCORED, find(SbarStructureChecker.check(null, null), "rec.element").status());
    }
}

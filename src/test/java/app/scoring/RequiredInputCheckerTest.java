package app.scoring;

import static org.junit.jupiter.api.Assertions.*;

import app.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R8 필수 입력 점검 테스트 (V5). 합성 값만 쓴다. */
class RequiredInputCheckerTest {
    private static ObservationEntry obs(List<String> tags, String text) {
        return new ObservationEntry("09:00", Shift.DAY, tags, text, null, null);
    }

    private static MedicationRecord med() {
        return new MedicationRecord("o1", "테스트약", "1", "정", "PO", "09:00", MedicationResult.GIVEN, null, "처방", null, null);
    }

    private static SbarReport sbar(String s, String b, String a, List<SbarRecommendation> r) {
        return new SbarReport(s, b, a, r);
    }

    private static List<CheckResult> missing(List<CheckResult> rs) {
        return rs.stream().filter(r -> r.status() == CheckStatus.MISSING).toList();
    }

    @Test
    void 모두_입력하면_누락이_없다() {
        var rs = RequiredInputChecker.check(List.of(obs(List.of("기분"), "서술")), List.of(med()),
                sbar("s", "b", "a", List.of(new SbarRecommendation("10:00", "관찰"))));
        assertTrue(missing(rs).isEmpty());
        assertTrue(rs.stream().allMatch(r -> r.ruleId().equals("R8") && r.points() == null));
    }

    @Test
    void B_영역_누락은_background에_연결된다() {
        var rs = RequiredInputChecker.checkSbar(sbar("s", " ", "a", List.of(new SbarRecommendation("10:00", "x"))));
        var m = missing(rs);
        assertEquals(1, m.size());
        assertEquals("background", m.get(0).targetRef());
        assertEquals(CheckTarget.SBAR, m.get(0).target());
        assertTrue(m.get(0).message().contains("B"));
    }

    @Test
    void SBAR_없으면_S_B_A_R_모두_누락() {
        assertEquals(4, missing(RequiredInputChecker.checkSbar(null)).size());
    }

    @Test
    void R_행의_시각_또는_중재_누락() {
        var m = missing(RequiredInputChecker.checkSbar(
                sbar("s", "b", "a", List.of(new SbarRecommendation("", "x"), new SbarRecommendation("10:00", null)))));
        assertEquals(List.of("rec1.time", "rec2.intervention"), m.stream().map(CheckResult::targetRef).toList());
    }

    @Test
    void 관찰_태그_서술_누락() {
        var m = missing(RequiredInputChecker.checkObservations(List.of(obs(List.of(), "서술"), obs(List.of("기분"), " "))));
        assertEquals(List.of("observation[1].tags", "observation[2].narrative"),
                m.stream().map(CheckResult::targetRef).toList());
        assertEquals(1, missing(RequiredInputChecker.checkObservations(List.of())).size());
        assertEquals(1, missing(RequiredInputChecker.checkObservations(null)).size());
    }

    @Test
    void 투약_기록_없으면_누락() {
        assertEquals(1, missing(RequiredInputChecker.checkMedications(List.of())).size());
        assertTrue(missing(RequiredInputChecker.checkMedications(List.of(med()))).isEmpty());
    }
}

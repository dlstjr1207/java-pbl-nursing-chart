package app.scoring;

import static org.junit.jupiter.api.Assertions.*;

import app.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R6 투약 기록 완결성 테스트 (S4, V6). 합성 값만 쓴다. */
class MedicationCompletenessCheckerTest {
    private static final List<Medication> ORDERS = List.of(
            new Medication("reg", OrderType.REGULAR, "테스트약A", "1", "정", "PO", "1일 1회", List.of("09:00"), null, null),
            new Medication("prn", OrderType.PRN, "테스트약B", "1", "정", "PO", "필요시", List.of(), "불안", null));

    private static MedicationRecord rec(String orderId, MedicationResult res, String reason) {
        return new MedicationRecord(orderId, "테스트약", "1", "정", "PO", "09:00", res, reason, "처방", null, null);
    }

    private static List<CheckResult> missing(List<CheckResult> rs) {
        return rs.stream().filter(r -> r.status() == CheckStatus.MISSING).toList();
    }

    @Test
    void 정규_투여는_사유_없이_통과한다() {
        var rs = MedicationCompletenessChecker.check(List.of(rec("reg", MedicationResult.GIVEN, null)), ORDERS);
        assertTrue(missing(rs).isEmpty());
        assertTrue(rs.stream().allMatch(r -> r.ruleId().equals("R6") && r.points() == null));
    }

    @Test
    void 거부에_사유가_없으면_reason에_연결된다() {
        var m = missing(MedicationCompletenessChecker.check(List.of(rec("reg", MedicationResult.REFUSED, " ")), ORDERS));
        assertEquals(1, m.size());
        assertEquals("medication[1].reason", m.get(0).targetRef());
    }

    @Test
    void 보류와_PRN도_사유가_필수다() {
        assertEquals(1, missing(MedicationCompletenessChecker.check(List.of(rec("reg", MedicationResult.HELD, null)), ORDERS)).size());
        assertEquals(1, missing(MedicationCompletenessChecker.check(List.of(rec("prn", MedicationResult.GIVEN, null)), ORDERS)).size());
        assertTrue(missing(MedicationCompletenessChecker.check(List.of(rec("prn", MedicationResult.GIVEN, "불안 호소")), ORDERS)).isEmpty());
    }

    @Test
    void 필수_필드_누락을_기록_순번과_함께_알린다() {
        var bad = new MedicationRecord("reg", "", "1", "정", null, "", MedicationResult.GIVEN, null, " ", null, null);
        var refs = missing(MedicationCompletenessChecker.check(List.of(rec("reg", MedicationResult.GIVEN, null), bad), ORDERS))
                .stream().map(CheckResult::targetRef).toList();
        assertEquals(List.of("medication[2].drugName", "medication[2].route",
                "medication[2].administeredTime", "medication[2].orderBasis"), refs);
    }

    @Test
    void 기록이_없으면_결과를_만들지_않는다() {
        assertTrue(MedicationCompletenessChecker.check(List.of(), ORDERS).isEmpty());
        assertTrue(MedicationCompletenessChecker.check(null, null).isEmpty());
    }
}

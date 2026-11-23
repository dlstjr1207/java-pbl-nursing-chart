package app.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.cases.CaseLoader;
import app.domain.AnswerKey;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.MedicationRecord;
import app.domain.MedicationResult;
import app.domain.ObservationEntry;
import app.domain.Shift;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 용량 표기 맞춤: 처방 화면의 "1.0mg"을 그대로 써도 "1mg"과 같은 값으로 본다 (시연 5회차 버그).
 * 배점·판단 기준은 그대로이고, 같은 값을 다르게 쓴 표기만 맞춘다. "0.5mg"은 계속 틀린다.
 */
class DoseNotationTest {
    private static final AnswerKey KEY = new CaseLoader().load("PC-01").answerKey();

    private static CheckStatus r2Dose(String narrative) {
        ObservationEntry o = new ObservationEntry("09:25", Shift.DAY, List.of("PRN 투여"), narrative, null, null);
        return KeyFactChecker.check(List.of(o), KEY).stream()
                .filter(c -> c.targetRef().equals("observation.fact[투약]")).map(CheckResult::status).findFirst().orElseThrow();
    }

    @Test
    void 관찰_기록의_1점0mg_표기도_투약으로_인정한다() {
        assertEquals(CheckStatus.PASS, r2Dose("09:30 PRN Lorazepam 1.0mg PO 투여함."));
        assertEquals(CheckStatus.PASS, r2Dose("09:30 로라제팜 1.0 mg 경구 투여함."));
        assertEquals(CheckStatus.PASS, r2Dose("09:30 로라제팜 1mg 경구 투여함."));
        assertEquals(CheckStatus.PASS, r2Dose("09:30 로라제팜 1 mg PO 투여, 용량 1.0."));
    }

    @Test
    void 다른_용량은_계속_틀린다() {
        assertEquals(CheckStatus.MISSING, r2Dose("09:30 로라제팜 0.5mg 경구 투여함."));
        assertEquals(CheckStatus.MISSING, r2Dose("09:30 Lorazepam 0.50 mg PO 투여함."));
    }

    private static CheckStatus r7Dose(String dose) {
        MedicationRecord m = new MedicationRecord("PC01-LZP-PRN", "Lorazepam", dose, "mg", "PO", "09:30",
                MedicationResult.GIVEN, "불안", "PRN 처방", true, null);
        return ValueMatchChecker.check(List.of(m), KEY).stream()
                .filter(c -> c.targetRef().endsWith("용량")).map(CheckResult::status).findFirst().orElseThrow();
    }

    @Test
    void 투약_기록_용량도_1점0과_1을_같게_보고_0점5는_불일치() {
        assertEquals(CheckStatus.PASS, r7Dose("1.0"));
        assertEquals(CheckStatus.PASS, r7Dose("1"));
        assertEquals(CheckStatus.MISMATCH, r7Dose("0.5"));
    }

    @Test
    void 정답_키_쪽의_1점0도_같은_값으로_본다() {
        ElementPattern key = ElementPattern.parse("투약=로라제팜|Lorazepam&1.0mg|1.0 mg&PO|경구");
        assertEquals(true, key.foundIn("로라제팜 1mg 경구"));
        assertEquals(true, key.foundIn("Lorazepam 1.0 mg PO"));
        assertEquals(false, key.foundIn("로라제팜 0.5mg 경구"));
    }

    @Test
    void 끝자리_점0만_없애고_다른_숫자와_시각은_그대로_둔다() {
        assertEquals("1mg", ElementPattern.trimZeroDecimal("1.0mg"));
        assertEquals("10 mg", ElementPattern.trimZeroDecimal("10.00 mg"));
        assertEquals("용량 1.", ElementPattern.trimZeroDecimal("용량 1.0."));
        assertEquals("0.5mg", ElementPattern.trimZeroDecimal("0.5mg"));
        assertEquals("1.05", ElementPattern.trimZeroDecimal("1.05"));
        assertEquals("09:30", ElementPattern.trimZeroDecimal("09:30"));
        assertEquals("VAS 8→2", ElementPattern.trimZeroDecimal("VAS 8→2"));
    }
}

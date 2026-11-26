package app.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.domain.CaseSource;
import app.domain.Medication;
import app.domain.OrderType;
import app.domain.PatientCase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PC-01.json과 allowed-drugs.json이 환자케이스·결정 D28·D29와 맞는지 확인한다 (단계 3). */
class Pc01DataTest {
    private final PatientCase pc01 = new CaseLoader().load("PC-01");
    private final AllowedDrugs allowed = AllowedDrugs.load();

    @Test
    void PC01은_고정_케이스이고_환자케이스의_값과_같다() {
        assertEquals(CaseSource.FIXED, pc01.source());
        assertEquals("김하늘", pc01.patient().name());
        assertEquals("501-1호", pc01.patient().room());
        assertEquals("동의입원", pc01.patient().admissionType());
        assertEquals(14, pc01.patient().hospitalDay());
        assertEquals(4, pc01.orders().size());
        Medication prn = pc01.orders().stream().filter(m -> m.type() == OrderType.PRN).findFirst().orElseThrow();
        assertEquals("Lorazepam", prn.drugName());
        assertEquals("1", prn.dose());
        assertEquals("PO", prn.route());
        assertTrue(pc01.reviewStatus().startsWith("팀 확정 초안"));
    }

    @Test
    void 정답_키와_구조화된_모범_기록이_있다() {
        assertNotNull(pc01.answerKey());
        assertEquals(6, pc01.answerKey().observationElements().size());
        assertEquals(5, pc01.answerKey().medicationElements().size());
        assertNotNull(pc01.modelAnswer().observationRecord());
        assertNotNull(pc01.modelAnswer().medicationRecord());
        assertNotNull(pc01.modelAnswer().sbarReport());
    }

    @Test
    void 허용_약물_목록은_4개이고_PC01_처방은_모두_통과한다() {
        assertEquals(4, allowed.all().size());
        List<String> problems = new CaseValidator(allowed).check(pc01);
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void 허용_범위를_벗어난_처방은_원인과_함께_거부한다() {
        List<Medication> orders = new ArrayList<>();
        orders.add(new Medication("X1", OrderType.REGULAR, "Aripiprazole", "40", "mg", "PO", "1일 1회", List.of("08:30"), null, null));
        orders.add(new Medication("X2", OrderType.REGULAR, "탄산리튬", "300", "mg", "PO", "1일 2회", List.of("08:30", "18:30"), null, null));
        orders.add(new Medication("X3", OrderType.PRN, "Lorazepam", "2", "mg", "PO", "필요 시", List.of(), "불안 시", null));
        orders.add(new Medication("X4", OrderType.REGULAR, "Haloperidol", "5", "mg", "PO", "1일 1회", List.of("08:30"), null, null));
        PatientCase bad = new PatientCase(pc01.id(), pc01.title(), pc01.difficulty(), pc01.objectives(), pc01.reviewStatus(),
                pc01.source(), pc01.patient(), pc01.adr(), pc01.riskTier(), orders, pc01.regimenHistory(),
                pc01.lithiumLevel(), pc01.vitals(), pc01.observationLog(), pc01.safetyRounds(), pc01.briefing(),
                pc01.sbarReference(), pc01.answerKey(), pc01.modelAnswer(), pc01.rawNotes(), pc01.task());
        String all = String.join("\n", new CaseValidator(allowed).check(bad));
        assertTrue(all.contains("orders[0] 1일 총량 40.0"), all);
        assertTrue(all.contains("orders[1] 1일 2회"), all);
        assertTrue(all.contains("orders[2].dose PRN 1회 용량 2"), all);
        assertTrue(all.contains("orders[3].drugName 허용 약물 목록에 없음"), all);
    }

    @Test
    void 제출_전에_보이는_브리핑에는_모범_기록_문장이_없다() {
        assertFalse(pc01.briefing().contains("재평가 예정"));
        assertFalse(pc01.briefing().contains(pc01.modelAnswer().observationRecord().narrative()));
    }
}

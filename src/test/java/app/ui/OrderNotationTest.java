package app.ui;

import app.cases.CaseLoader;
import app.domain.Medication;
import app.domain.OrderType;
import app.domain.PatientCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** D38 B3: 처방을 실제 EMR 형식으로. 정제 개수는 허가 제품 함량과 맞을 때만. */
class OrderNotationTest {

    private final PatientCase pc01 = new CaseLoader().load("PC-01");

    private Medication order(String id) {
        return pc01.orders().stream().filter(o -> o.orderId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void PC01_처방은_EMR_형식으로_보인다() {
        assertEquals("Aripiprazole 10mg 1T PO QD 08:30", OrderNotation.format(order("PC01-ARI")));
        assertEquals("Lithium carbonate 300mg 1T PO TID 08:30·12:30·18:30", OrderNotation.format(order("PC01-LI")));
        assertEquals("Lorazepam 1mg 1T PO PRN (불안·초조 시, 1일 최대 4mg)", OrderNotation.format(order("PC01-LZP-PRN")));
        // 15mg은 허가 제품 함량(10mg)과 맞지 않아 정제 개수를 뺀다
        assertEquals("Olanzapine 15mg PO QD (2024-11 Aripiprazole로 교체)", OrderNotation.format(order("PC01-OLZ-PAST")));
    }

    @Test
    void 환자_정보_투약_점검_근거_창이_같은_표기를_쓴다() {
        String prn = OrderNotation.format(order("PC01-LZP-PRN"));
        assertEquals(prn, MedicationController.orderSummary(order("PC01-LZP-PRN")));
        assertTrue(PatientInfoView.sections(pc01).stream().anyMatch(s -> s.lines().contains("[PRN] " + prn)));
        String evidence = EvidenceDialog.text(pc01);
        assertTrue(evidence.contains("  - " + prn + " [PRN] — 원문 비고: "), evidence);
        assertTrue(evidence.contains("  - Aripiprazole 10mg 1T PO QD 08:30 [REGULAR]"), evidence);
    }

    @Test
    void 함량이_맞지_않거나_목록에_없는_약은_개수를_뺀다() {
        Medication odd = new Medication("X", OrderType.REGULAR, "Aripiprazole", "15", "mg", "PO", "1일 1회",
                List.of("08:30"), null, null);
        assertEquals("Aripiprazole 15mg PO QD 08:30", OrderNotation.format(odd));
        Medication unknown = new Medication("Y", OrderType.REGULAR, "테스트약", "5", "mg", "PO", "1일 2회",
                List.of("08:00", "20:00"), null, null);
        assertEquals("테스트약 5mg PO BID 08:00·20:00", OrderNotation.format(unknown));
        Medication lz = new Medication("Z", OrderType.PRN, "Lorazepam", "0.5", "mg", "PO", "필요 시", List.of(), "불안 시", null);
        assertEquals("Lorazepam 0.5mg 1T PO PRN (불안 시)", OrderNotation.format(lz));
    }
}

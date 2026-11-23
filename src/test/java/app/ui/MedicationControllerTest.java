package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import app.domain.Medication;
import app.domain.MedicationResult;
import app.domain.OrderType;
import app.ui.MedicationController.SaveResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 투약 입력 검증·저장·처방 선택지를 확인한다 (S4, V6). DEMO 케이스와 합성 값만 쓴다. */
class MedicationControllerTest {
    private AppState state;
    private MedicationController controller;
    private Medication regular;
    private Medication prn;

    @BeforeEach
    void setUp() {
        state = new AppState();
        state.login("테스터");
        state.startPractice(new CaseLoader().load("demo"));
        controller = new MedicationController(state);
        List<Medication> orders = controller.selectableOrders();
        regular = orders.stream().filter(o -> o.type() == OrderType.REGULAR).findFirst().orElseThrow();
        prn = orders.stream().filter(o -> o.type() == OrderType.PRN).findFirst().orElseThrow();
    }

    private SaveResult save(Medication o, String dose, MedicationResult r, String reason) {
        return controller.save(o, dose, "mg", "PO", "09:00", r, reason, "처방 확인", true, "  ");
    }

    @Test
    void 선택지는_케이스_처방_목록으로_제한된다() {
        long expected = state.currentCase().orders().stream().filter(o -> o.type() != OrderType.PAST).count();
        assertEquals(expected, controller.selectableOrders().size());
    }

    @Test
    void 투여는_사유_없이_저장되고_입력이_보존된다() {
        assertTrue(save(regular, " 7 ", MedicationResult.GIVEN, null).saved());
        var rec = state.studentMedications().get(0);
        assertEquals(regular.orderId(), rec.orderId());
        assertEquals(" 7 ", rec.dose());
        assertNull(rec.reason());
        assertEquals(Boolean.TRUE, rec.oralCheck());
        assertNull(rec.reassessmentPlan());
    }

    @Test
    void 처방과_다른_용량도_저장된다() {
        assertTrue(save(regular, "999", MedicationResult.GIVEN, null).saved());
        assertEquals(1, state.studentMedications().size());
    }

    @Test
    void 거부와_보류는_사유가_없으면_저장되지_않는다() {
        for (MedicationResult r : List.of(MedicationResult.REFUSED, MedicationResult.HELD)) {
            SaveResult res = save(regular, "5", r, "  ");
            assertFalse(res.saved());
            assertTrue(res.errors().byField().containsKey("reason"));
        }
        assertTrue(state.studentMedications().isEmpty());
        assertTrue(save(regular, "5", MedicationResult.REFUSED, "환자 거부").saved());
    }

    @Test
    void PRN은_투여여도_사유가_필수다() {
        assertFalse(save(prn, "5", MedicationResult.GIVEN, "").saved());
        assertTrue(save(prn, "5", MedicationResult.GIVEN, "불안 호소").saved());
    }

    @Test
    void 필수값_누락은_칸별_오류가_된다() {
        SaveResult r = controller.save(null, "", "", " ", "25:99", null, null, "", null, null);
        assertFalse(r.saved());
        for (String f : List.of("order", "dose", "unit", "route", "time", "result", "orderBasis")) {
            assertTrue(r.errors().byField().containsKey(f), f);
        }
        assertTrue(state.studentMedications().isEmpty());
    }

    @Test
    void 개인_메인으로_돌아가면_투약_기록이_비워진다() {
        save(regular, "5", MedicationResult.GIVEN, null);
        state.go(Screen.HOME);
        assertTrue(state.studentMedications().isEmpty());
    }
}

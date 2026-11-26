package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import app.cases.CaseLoader;
import app.domain.Attempt;
import app.domain.CaseSource;
import app.domain.DomainJson;
import app.domain.MedicationResult;
import app.domain.PatientCase;
import app.domain.Shift;
import app.storage.InMemoryAttemptRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

/**
 * 제출한 기록 보기(읽기 전용): 결과 화면에서 환자 정보·메모·처방·활력징후와 제출한 관찰·투약·SBAR를 다시 보되
 * 기록을 고치거나 다시 제출할 수 없다. 과거 기록을 다시 열어도 같고, AI 환자면 그때의 환자가 보인다.
 */
class ReviewModeTest {

    private final PatientCase pc01 = new CaseLoader().load("PC-01");

    /** PC-01 모범 기록으로 실제 제출 경로(SbarController)를 거쳐 결과 화면까지 간다. */
    private Attempt submitModel(AppState state, PatientCase c) {
        state.login("학생");
        state.startPractice(c);
        state.addObservation(c.modelAnswer().observationRecord());
        state.addMedication(c.modelAnswer().medicationRecord());
        var s = c.modelAnswer().sbarReport();
        var r = new SbarController(state, new InMemoryAttemptRepository(), () -> 1L)
                .submit(s.situation(), s.background(), s.assessment(), s.recommendations());
        assertTrue(r.submitted(), r.message());
        return r.attempt();
    }

    @Test
    void 결과_화면에서_환자_정보와_입력_화면을_읽기_전용으로_열_수_있다() {
        AppState state = new AppState();
        Attempt a = submitModel(state, pc01);
        assertEquals(Screen.RESULT, state.current());
        assertTrue(state.isReviewing());
        for (Screen s : List.of(Screen.PATIENT, Screen.OBSERVATION, Screen.MEDICATION, Screen.SBAR, Screen.RESULT)) {
            assertTrue(state.canGo(s), s.name());
        }
        assertEquals(pc01.id(), state.currentCase().id());
        assertEquals(a.observationEntries(), state.studentObservations());
        assertEquals(a.medicationRecords(), state.studentMedications());
        assertFalse(state.hasStudentRecords()); // 떠날 때 확인할 작성 중 기록이 아니다
        assertTrue(state.go(Screen.PATIENT));
        assertTrue(state.patientLabel().endsWith("제출한 기록 보기"));
        assertTrue(state.go(Screen.RESULT));
    }

    @Test
    void 제출한_뒤에는_기록을_고치거나_다시_제출할_수_없다() {
        AppState state = new AppState();
        Attempt a = submitModel(state, pc01);
        var obs = new ObservationController(state).save("11:00", Shift.DAY, List.of("기타"), "점수를 바꾸려는 기록", null, Map.of());
        assertFalse(obs.saved());
        assertEquals(AppState.REVIEW_LOCKED, obs.errors().byField().get("readonly"));
        var order = pc01.orders().stream().filter(o -> o.orderId().equals("PC01-LZP-PRN")).findFirst().orElseThrow();
        var med = new MedicationController(state).save(order, "1", "mg", "PO", "11:00", MedicationResult.GIVEN, "불안",
                "PRN", true, "11:30 재평가");
        assertFalse(med.saved());
        var s = pc01.modelAnswer().sbarReport();
        var sbar = new SbarController(state, new InMemoryAttemptRepository(), () -> 1L)
                .prepare(s.situation(), s.background(), s.assessment(), s.recommendations());
        assertFalse(sbar.ok());
        assertEquals(AppState.REVIEW_LOCKED, sbar.message());
        assertThrows(IllegalStateException.class, () -> state.addObservation(pc01.modelAnswer().observationRecord()));
        // 결과(점수·기록)는 그대로
        assertEquals(1, state.studentObservations().size());
        assertEquals(a, state.resultAttempt());
    }

    @Test
    void 과거_AI_환자_기록을_다시_열면_그때의_AI_환자가_보인다() {
        // 저장된 AI 환자: PC-01을 바탕으로 이름·ID를 바꾼 합성 케이스
        JsonObject o = JsonParser.parseString(DomainJson.toJson(pc01)).getAsJsonObject();
        o.addProperty("id", "GEN-20261126-0900");
        o.addProperty("source", "AI_GENERATED");
        o.getAsJsonObject("patient").addProperty("name", "가상AI환자");
        PatientCase ai = DomainJson.fromJson(o.toString(), PatientCase.class);
        AppState state = new AppState();
        Attempt submitted = submitModel(state, ai);
        assertTrue(submitted.generatedCaseJson().contains("가상AI환자"));
        // 개인 메인으로 갔다가 과거 기록을 다시 연다
        assertTrue(state.go(Screen.HOME));
        assertFalse(state.isReviewing());
        assertTrue(state.showResult(submitted));
        assertTrue(state.isReviewing());
        assertEquals("가상AI환자", state.currentCase().patient().name());
        assertEquals(CaseSource.AI_GENERATED, state.currentCase().source());
        assertTrue(state.notices().contains(AppState.AI_NOTICE));
        PatientInfoPanel info = new PatientInfoPanel(state, x -> { });
        info.reload();
        assertTrue(allText(info).contains("가상AI환자"), allText(info));
    }

    @Test
    void 새_실습을_시작하면_다시_입력할_수_있다() {
        AppState state = new AppState();
        submitModel(state, pc01);
        state.startPractice(pc01);
        assertFalse(state.isReviewing());
        assertTrue(state.studentObservations().isEmpty());
        var obs = new ObservationController(state).save("11:00", Shift.DAY, List.of("기타"), "새 실습 기록", null, Map.of());
        assertTrue(obs.saved());
        assertTrue(state.hasStudentRecords());
    }

    @Test
    void 읽기_전용_화면은_입력칸을_숨기고_제출한_내용을_보인다() {
        AppState state = new AppState();
        Attempt a = submitModel(state, pc01);
        ObservationPanel obs = new ObservationPanel(state, x -> { });
        obs.reload();
        assertFalse(hasVisibleButton(obs, "관찰 기록 저장"));
        assertTrue(allText(obs).contains("데이룸에서 서성이며"), "제출한 관찰이 목록에 보여야 함");
        MedicationPanel med = new MedicationPanel(state, x -> { });
        med.reload();
        assertFalse(hasVisibleButton(med, "투약 기록 저장"));
        SbarPanel sbar = new SbarPanel(new SbarController(state, new InMemoryAttemptRepository(), () -> 1L), x -> { });
        sbar.reload();
        assertFalse(hasVisibleButton(sbar, "SBAR 제출"));
        assertTrue(allText(sbar).contains(a.sbar().situation()));
        assertFalse(sbar.hasDraft());
        for (Component c : all(sbar)) {
            if (c instanceof JTextArea t && t.getText().equals(a.sbar().situation())) assertFalse(t.isEditable());
        }
        // 새 실습이면 SBAR 칸은 비고 다시 입력할 수 있다
        state.startPractice(pc01);
        obs.reload();
        sbar.reload();
        assertTrue(hasVisibleButton(obs, "관찰 기록 저장"));
        assertTrue(hasVisibleButton(sbar, "SBAR 제출"));
        assertFalse(allText(sbar).contains(a.sbar().situation()));
    }

    /** 실제 JFrame: 안내 줄과 "결과로 돌아가기" (화면 없는 환경에서는 건너뛴다) */
    @Test
    void 읽기_전용_화면_위에_안내와_결과로_돌아가기_버튼이_보인다_창() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "화면 없는 환경");
        AppState state = new AppState();
        MainFrame frame = new MainFrame(state);
        frame.register(Screen.RESULT, new ResultPanel(state, frame::show));
        frame.register(Screen.PATIENT, new PatientInfoPanel(state, frame::show));
        submitModel(state, pc01);
        frame.refresh();
        JButton back = (JButton) find(frame.getContentPane(), "backToResult");
        assertFalse(back.getParent().isVisible()); // 결과 화면에서는 안 보임
        assertTrue(frame.show(Screen.PATIENT));
        assertTrue(back.getParent().isVisible());
        assertFalse(frame.hasUnsavedWork());
        back.doClick();
        assertEquals(Screen.RESULT, state.current());
        assertFalse(back.getParent().isVisible());
        frame.dispose();
    }

    private static List<Component> all(Container root) {
        List<Component> out = new java.util.ArrayList<>();
        for (Component c : root.getComponents()) {
            out.add(c);
            if (c instanceof Container k) out.addAll(all(k));
        }
        return out;
    }

    private static Component find(Container root, String name) {
        return all(root).stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

    /** 버튼이 있고, 그 버튼과 조상이 모두 보이는지 */
    private static boolean hasVisibleButton(Container root, String text) {
        for (Component c : all(root)) {
            if (c instanceof JButton b && text.equals(b.getText())) {
                for (Component p = b; p != null && p != root; p = p.getParent()) if (!p.isVisible()) return false;
                return true;
            }
        }
        return false;
    }

    private static String allText(Container root) {
        StringBuilder sb = new StringBuilder();
        for (Component c : all(root)) {
            if (c instanceof javax.swing.text.JTextComponent t) sb.append(t.getText()).append('\n');
            if (c instanceof javax.swing.JLabel l) sb.append(l.getText()).append('\n');
            if (c instanceof javax.swing.JList<?> list) {
                for (int i = 0; i < list.getModel().getSize(); i++) sb.append(list.getModel().getElementAt(i)).append('\n');
            }
        }
        return sb.toString();
    }
}

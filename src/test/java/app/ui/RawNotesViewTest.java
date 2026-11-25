package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import app.domain.PatientCase;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

/** 환자 정보 화면은 브리핑 대신 원자료 메모를 보여 주고, 메모 글은 복사할 수 없다. */
class RawNotesViewTest {

    private static JTextArea find(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextArea a && name.equals(a.getName())) return a;
            if (c instanceof Container k) {
                JTextArea f = find(k, name);
                if (f != null) return f;
            }
        }
        return null;
    }

    @Test
    void 환자_정보_화면은_브리핑이_아니라_원자료_메모를_보여_준다() {
        PatientCase pc01 = new CaseLoader().load("PC-01");
        AppState s = new AppState();
        s.login("테스트");
        s.startPractice(pc01);
        PatientInfoPanel panel = new PatientInfoPanel(s, x -> { });
        JTextArea area = find(panel, "rawNotes");
        assertNotNull(area);
        String shown = area.getText();
        assertTrue(shown.startsWith(pc01.task()));
        assertTrue(shown.contains("아 그니까… 저기, 저기 저 사람이"));
        String briefingOnly = "간호사가 조용한 곳으로 이동해 1:1로 지지했다";
        assertTrue(pc01.briefing().contains(briefingOnly));
        assertFalse(shown.contains(briefingOnly), "정리된 브리핑은 보이지 않아야 함");
        // 관련 없음 표시·알아볼 낱말 목록은 보이지 않는다
        assertFalse(shown.contains("irrelevant") || shown.contains("cues"));
        // 메모 순서는 케이스에 적힌 그대로 (시간순으로 다시 정렬하지 않음)
        assertTrue(shown.indexOf("10:00") < shown.indexOf("09:27"));
    }

    @Test
    void 원자료_메모는_선택_복사_끌기가_되지_않는다() {
        JTextArea a = new JTextArea("메모");
        RawNotesView.protect(a);
        assertNull(a.getTransferHandler());
        assertNull(a.getHighlighter());
        assertFalse(a.isEditable());
        assertFalse(a.isFocusable());
        assertFalse(a.getDragEnabled());
    }

    @Test
    void 원자료가_없는_예전_케이스는_브리핑을_보여_준다() {
        PatientCase demo = new CaseLoader().load("demo");
        assertEquals(List.of(), demo.rawNotes());
        assertEquals(RawNotesView.BRIEFING_TITLE, RawNotesView.title(demo));
        assertEquals(demo.briefing() == null ? "" : demo.briefing(), RawNotesView.text(demo));
    }

    private static boolean hasLabel(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof javax.swing.JLabel l && text.equals(l.getText())) return true;
            if (c instanceof Container k && hasLabel(k, text)) return true;
        }
        return false;
    }

    @Test
    void 관찰_화면에_기존_관찰_로그의_정리된_문장이_보이지_않는다() {
        PatientCase pc01 = new CaseLoader().load("PC-01");
        String logLine = pc01.observationLog().stream().filter(e -> "10:00".equals(e.time())).findFirst().orElseThrow().narrative();
        AppState s = new AppState();
        s.login("테스트");
        s.startPractice(pc01);
        ObservationPanel panel = new ObservationPanel(s, x -> { });
        assertTrue(hasLabel(panel, "관찰 기록이 없습니다."), "케이스 로그가 목록에 없어야 함");
        assertFalse(allText(panel).contains(logLine));
        assertFalse(allText(new PatientInfoPanel(s, x -> { })).contains(logLine));
        // 데이터는 남아 있다 (AI 해설 자료)
        assertFalse(pc01.observationLog().isEmpty());
    }

    private static String allText(Container root) {
        StringBuilder sb = new StringBuilder();
        for (Component c : root.getComponents()) {
            if (c instanceof javax.swing.text.JTextComponent t) sb.append(t.getText()).append('\n');
            if (c instanceof javax.swing.JLabel l) sb.append(l.getText()).append('\n');
            if (c instanceof Container k) sb.append(allText(k));
        }
        return sb.toString();
    }

    @Test
    void 원자료가_없는_예전_기록을_다시_열어도_화면이_깨지지_않는다() {
        // 원자료 도입 전 형식: 저장된 케이스 JSON에 rawNotes·task가 없다
        PatientCase pc01 = new CaseLoader().load("PC-01");
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(app.domain.DomainJson.toJson(pc01)).getAsJsonObject();
        o.remove("rawNotes");
        o.remove("task");
        String oldJson = o.toString();
        app.domain.Attempt old = new app.domain.Attempt(1L, 1L, "GEN-OLD", app.domain.CaseSource.AI_GENERATED, oldJson,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), List.of(pc01.modelAnswer().observationRecord()),
                List.of(pc01.modelAnswer().medicationRecord()), pc01.modelAnswer().sbarReport(), List.of(), 100.0, null);
        PatientCase restored = app.cases.CaseGenerator.restore(old);
        assertFalse(restored.hasRawNotes());
        assertNull(restored.task());
        // 결과 화면
        AppState s = new AppState();
        s.login("테스트");
        assertTrue(s.showResult(old));
        assertTrue(allText(new ResultPanel(s, x -> { })).contains("100"));
        // 같은 케이스로 다시 실습: 환자 정보·관찰 화면은 브리핑 카드로 보인다
        s.startPractice(restored);
        PatientInfoPanel info = new PatientInfoPanel(s, x -> { });
        assertEquals(restored.briefing(), find(info, "rawNotes").getText());
        ObservationPanel obs = new ObservationPanel(s, x -> { });
        assertEquals(restored.briefing(), find(obs, "rawNotes").getText());
        assertTrue(hasLabel(obs, "관찰 기록이 없습니다."));
        // 원자료가 없으면 R10은 아무것도 내지 않는다
        assertEquals(List.of(), app.scoring.RawNotesChecker.check(restored, old.observationEntries(),
                old.medicationRecords(), old.sbar()));
    }
}

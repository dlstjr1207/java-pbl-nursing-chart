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
}

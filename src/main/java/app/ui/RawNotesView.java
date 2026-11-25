package app.ui;

import app.domain.PatientCase;
import app.domain.RawNote;
import javax.swing.JTextArea;

/**
 * 원자료 메모를 학생 화면에 보이는 글로 만든다 (환자 정보·관찰 화면이 함께 쓴다).
 * 메모는 케이스에 적힌 순서 그대로(시간 순서가 섞인 채) 보여 주고, 관련 없음 표시는 보이지 않는다.
 * 원자료 메모가 없는 예전 케이스는 상황 브리핑을 그대로 보여 준다.
 */
public final class RawNotesView {
    public static final String NOTES_TITLE = "근무 중 메모 (원자료) · 필요한 사실만 골라 기록하세요";
    public static final String BRIEFING_TITLE = "상황 브리핑 (과제)";

    private RawNotesView() {
    }

    /** 카드 제목. */
    public static String title(PatientCase c) {
        return c != null && c.hasRawNotes() ? NOTES_TITLE : BRIEFING_TITLE;
    }

    /** 화면에 보일 글: 과제 안내 + 메모 줄들 (원자료가 없으면 브리핑). */
    public static String text(PatientCase c) {
        if (c == null) return "";
        if (!c.hasRawNotes()) return c.briefing() == null ? "" : c.briefing();
        StringBuilder sb = new StringBuilder();
        if (c.task() != null && !c.task().isBlank()) sb.append(c.task().trim()).append("\n\n");
        for (RawNote n : c.rawNotes()) {
            if (n == null) continue;
            sb.append("• ").append(n.time() == null ? "--:--" : n.time()).append("  ").append(n.text() == null ? "" : n.text())
                    .append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /**
     * 메모 글을 복사할 수 없게 한다: 선택 표시·끌어 놓기·Ctrl+C(전송 처리기)를 끄고 포커스를 받지 않게 한다.
     * 학생이 메모를 통째로 붙여 넣지 않고 필요한 사실만 골라 직접 쓰게 하려는 것이다.
     */
    public static void protect(JTextArea area) {
        area.setEditable(false);
        area.setTransferHandler(null);
        area.setDragEnabled(false);
        area.setHighlighter(null);
        area.setFocusable(false);
        area.setComponentPopupMenu(null);
    }
}

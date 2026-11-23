package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import app.storage.InMemoryAttemptRepository;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import javax.swing.AbstractButton;
import javax.swing.DefaultListModel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/**
 * 단계 9 실제 창 검증에서 찾은 문제: 시각이 빠진 R 행을 입력칸에 둔 채 제출하면 그 행이 목록으로 옮겨지고 입력칸이 비었다.
 * 검증 실패 시 쓰다 만 행은 입력칸에 그대로 남고 목록에는 들어가지 않아야 한다 (V7). 값은 합성 값이다.
 */
class SbarPanelPendingRowTest {

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void click(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton b && text.equals(b.getText())) {
                b.doClick();
                return;
            }
            if (c instanceof Container k) click(k, text);
        }
    }

    @Test
    void 검증에_실패하면_쓰다_만_R_행은_입력칸에_남는다() throws Exception {
        AppState state = new AppState();
        state.login("테스터");
        state.startPractice(new CaseLoader().load("demo"));
        state.go(Screen.SBAR);
        SbarPanel panel = new SbarPanel(new SbarController(state, new InMemoryAttemptRepository(), () -> 1L), s -> { });
        panel.reload();
        ((JTextField) field(panel, "interField")).setText("합성 중재");
        click(panel, "SBAR 제출");

        assertTrue(((DefaultListModel<?>) field(panel, "rowModel")).isEmpty(), "목록으로 옮기지 않는다");
        assertEquals("합성 중재", ((JTextField) field(panel, "interField")).getText());
        assertEquals(Screen.SBAR, state.current());
    }
}

package app;

import app.ui.AppState;
import app.ui.MainFrame;
import javax.swing.SwingUtilities;

/** 프로그램 시작점. 테마를 적용하고 메인 창을 띄운다. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame.installTheme();
            new MainFrame(new AppState()).setVisible(true);
        });
    }
}

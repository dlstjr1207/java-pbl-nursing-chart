package app;

import app.auth.AuthService;
import app.cases.CaseLoader;
import app.storage.DbConfig;
import app.storage.MySqlUserRepository;
import app.storage.UserRepository;
import app.ui.AppState;
import app.ui.AuthFormController;
import app.ui.HomeController;
import app.ui.HomePanel;
import app.ui.LoginPanel;
import app.ui.MainFrame;
import app.ui.PatientInfoPanel;
import app.ui.Screen;
import app.ui.SignupPanel;
import app.ui.StartupCheck;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** 프로그램 시작점. DB를 확인하고, 테마를 적용한 뒤 로그인 화면이 있는 메인 창을 띄운다. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame.installTheme();
            AppState state = new AppState();
            MainFrame frame = new MainFrame(state);
            StartupCheck.Result startup = StartupCheck.check(DbConfig.DEFAULT_PATH);
            if (!startup.ok()) {
                // DB를 쓸 수 없으면 로그인 화면 대신 원인 안내를 보여 준다 (비밀번호는 포함되지 않음).
                frame.register(Screen.LOGIN, problemPanel(startup.problem()));
            } else {
                UserRepository users = new MySqlUserRepository(startup.database());
                AuthService auth = new AuthService(users, state);
                AuthFormController form = new AuthFormController(auth, state);
                LoginPanel login = new LoginPanel(form, frame::refresh);
                frame.register(Screen.LOGIN, login);
                frame.register(Screen.SIGNUP, new SignupPanel(form, frame::refresh, login::showInfo));
                frame.setLogoutAction(auth::logout);
            }
            frame.register(Screen.HOME, new HomePanel(new HomeController(state, new CaseLoader()), frame::refresh));
            frame.register(Screen.PATIENT, new PatientInfoPanel(state, frame::show));
            frame.refresh();
            frame.setVisible(true);
        });
    }

    /** DB 설정·연결 실패 안내 패널. */
    private static JPanel problemPanel(String problem) {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.add(new JLabel("DB를 사용할 수 없어 로그인할 수 없습니다."), BorderLayout.NORTH);
        JTextArea text = new JTextArea(problem);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        p.add(text, BorderLayout.CENTER);
        return p;
    }
}

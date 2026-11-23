package app;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.JdkHttpTransport;
import app.auth.AuthService;
import app.cases.AllowedDrugs;
import app.cases.CaseGenerator;
import app.cases.CaseLoader;
import app.cases.CaseValidator;
import app.storage.AttemptRepository;
import app.storage.DbConfig;
import app.storage.FileAttemptRepository;
import app.storage.FileUserRepository;
import app.storage.MySqlAttemptRepository;
import app.storage.MySqlUserRepository;
import app.storage.UserRepository;
import app.ui.AppState;
import app.ui.AuthFormController;
import app.ui.HomeController;
import app.ui.HomePanel;
import app.ui.LoginPanel;
import app.ui.MainFrame;
import app.ui.MedicationPanel;
import app.ui.ObservationPanel;
import app.ui.PatientInfoPanel;
import app.ui.ResultView;
import app.ui.SbarController;
import app.ui.SbarPanel;
import app.ui.Screen;
import app.ui.SignupPanel;
import app.ui.StartupCheck;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * 프로그램 시작점. 저장 방식을 정하고(MySQL, 안 되면 파일 저장 모드), 테마를 적용한 뒤 메인 창을 띄운다.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame.installTheme();
            createFrame(DbConfig.DEFAULT_PATH, defaultDataDir(), AiConfig.DEFAULT_PATH).setVisible(true);
        });
    }

    /** 파일 저장 모드의 저장 폴더: 사용자 홈 아래 .nursing-chart-trainer (D27). */
    public static Path defaultDataDir() {
        return Path.of(System.getProperty("user.home"), ".nursing-chart-trainer");
    }

    /** 저장소 묶음과 저장 방식 안내. */
    record Storage(UserRepository users, AttemptRepository attempts, String fileModeReason) {}

    /**
     * 저장 방식을 고른다. DB 설정이 있고 연결되면 MySQL, 아니면 파일 저장 모드(D27).
     * fileModeReason은 파일 저장 모드가 된 이유(비밀번호 없음), MySQL이면 null.
     */
    static Storage chooseStorage(Path dbConfig, Path dataDir) {
        StartupCheck.Result startup = StartupCheck.check(dbConfig);
        if (startup.ok()) {
            return new Storage(new MySqlUserRepository(startup.database()),
                    new MySqlAttemptRepository(startup.database()), null);
        }
        return new Storage(new FileUserRepository(dataDir.resolve("users.json")),
                new FileAttemptRepository(dataDir.resolve("attempts.json")), startup.problem());
    }

    /**
     * 메인 창을 만든다(화면에 띄우지는 않음). 화면 스레드에서 호출한다.
     * 점검 도구가 실제 창을 띄워 확인할 때도 같은 구성을 쓴다.
     */
    public static MainFrame createFrame(Path dbConfig, Path dataDir, Path aiConfig) {
        AppState state = new AppState();
        MainFrame frame = new MainFrame(state);
        Storage storage = chooseStorage(dbConfig, dataDir);
        if (storage.fileModeReason() != null) state.setStorageNotice(AppState.FILE_MODE_NOTICE);

        AuthService auth = new AuthService(storage.users(), state);
        AuthFormController form = new AuthFormController(auth, state);
        LoginPanel login = new LoginPanel(form, frame::refresh);
        if (storage.fileModeReason() != null) {
            login.showInfo("DB 없이 파일 저장 모드로 실행합니다. 기록은 " + dataDir + "에 저장됩니다.");
        }
        frame.register(Screen.LOGIN, login);
        frame.register(Screen.SIGNUP, new SignupPanel(form, frame::refresh, login::showInfo));
        frame.setLogoutAction(auth::logout);

        AiClient ai = new AiClient(AiConfig.load(aiConfig), new JdkHttpTransport());
        CaseGenerator generator = new CaseGenerator(ai, new CaseValidator(AllowedDrugs.load()),
                new CaseLoader(), CaseGenerator.DEFAULT_MAX_ATTEMPTS);
        // 개인 메인의 평가지표·과거 기록은 로그인한 사용자의 기록만 조회한다.
        frame.register(Screen.HOME, new HomePanel(new HomeController(state, new CaseLoader(), generator), frame::refresh,
                () -> auth.currentUser().map(u -> new ResultView(storage.attempts(), u.id())).orElse(null)));
        frame.register(Screen.PATIENT, new PatientInfoPanel(state, frame::show));
        frame.register(Screen.OBSERVATION, new ObservationPanel(state, frame::show));
        frame.register(Screen.MEDICATION, new MedicationPanel(state, frame::show));
        // SBAR 제출 시 로그인한 사용자의 실습 기록으로 저장한다.
        SbarPanel sbar = new SbarPanel(new SbarController(state, storage.attempts(),
                () -> auth.currentUser().orElseThrow().id(), ai), frame::show);
        frame.register(Screen.SBAR, sbar);
        frame.addDraftCheck(sbar::hasDraft);
        frame.refresh();
        return frame;
    }
}

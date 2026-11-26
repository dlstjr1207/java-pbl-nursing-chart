package app;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.AiConnection;
import app.ai.AiServerSettings;
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
            login.showInfo("DB 없이 파일 저장 모드입니다. 기록은 이 PC의 사용자 폴더에 저장됩니다.");
        }
        frame.register(Screen.LOGIN, login);
        frame.register(Screen.SIGNUP, new SignupPanel(form, frame::refresh, login::showInfo));
        frame.setLogoutAction(auth::logout);

        // AI 설정: 키 파일(기존 방식) → 메뉴에서 저장한 AI 서버 주소(중계 서버) → 꺼짐. 중계 서버는 연결을 확인한 뒤 켠다.
        JdkHttpTransport transport = new JdkHttpTransport();
        AiClient ai = new AiClient(AiConfig.disabled(), transport);
        AiConnection aiConnection = new AiConnection(ai, AiConfig.load(aiConfig),
                new AiServerSettings(dataDir.resolve(AiServerSettings.FILE_NAME)), transport);
        boolean checkRelay = !aiConnection.usesKeyFile() && !aiConnection.serverUrl().isEmpty();
        if (checkRelay) {
            state.setAiNotice(AiConnection.STATUS_CHECKING); // 연결 확인은 창을 띄운 뒤 백그라운드에서
        } else {
            state.setAiNotice(aiConnection.connect()); // 키 파일 또는 꺼짐: 네트워크를 쓰지 않는다
        }
        CaseGenerator generator = new CaseGenerator(ai, new CaseValidator(AllowedDrugs.load()),
                new CaseLoader(), CaseGenerator.DEFAULT_MAX_ATTEMPTS);
        // 개인 메인의 평가지표·과거 기록은 로그인한 사용자의 기록만 조회한다.
        HomePanel home = new HomePanel(new HomeController(state, new CaseLoader(), generator), frame::refresh,
                () -> auth.currentUser().map(u -> new ResultView(storage.attempts(), u.id())).orElse(null));
        // 과거 기록을 누르면 같은 결과 화면으로 연다 (polish B-4)
        home.setOnOpen(a -> {
            if (state.showResult(a)) frame.refresh();
        });
        frame.register(Screen.HOME, home);
        frame.register(Screen.RESULT, new app.ui.ResultPanel(state, frame::show));
        frame.register(Screen.PATIENT, new PatientInfoPanel(state, frame::show));
        frame.register(Screen.OBSERVATION, new ObservationPanel(state, frame::show));
        frame.register(Screen.MEDICATION, new MedicationPanel(state, frame::show));
        // SBAR 제출 시 로그인한 사용자의 실습 기록으로 저장한다.
        SbarPanel sbar = new SbarPanel(new SbarController(state, storage.attempts(),
                () -> auth.currentUser().orElseThrow().id(), ai), frame::show);
        frame.register(Screen.SBAR, sbar);
        frame.addDraftCheck(sbar::hasDraft);
        frame.setAiServerAction(() -> askAiServer(frame, state, aiConnection, home));
        if (checkRelay) {
            connectInBackground(frame, state, aiConnection, home, false);
        }
        frame.refresh();
        return frame;
    }

    /** 중계 서버 연결 확인은 네트워크를 쓰므로 백그라운드에서 하고, 끝나면 상단 배지와 홈 화면 AI 버튼을 고친다. */
    private static void connectInBackground(MainFrame frame, AppState state, AiConnection c, HomePanel home, boolean showResult) {
        new javax.swing.SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return c.connect();
            }

            @Override
            protected void done() {
                state.setAiNotice(c.status());
                home.reload();
                frame.refresh();
                if (showResult) {
                    javax.swing.JOptionPane.showMessageDialog(frame, c.detail(), "AI 서버 주소",
                            c.status().equals(AiConnection.STATUS_RELAY_OK) ? javax.swing.JOptionPane.INFORMATION_MESSAGE
                                    : javax.swing.JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    /** 메뉴 '설정 > AI 서버 주소': 주소를 받아 저장하고 연결을 확인한다. 빈칸이면 주소를 지운다. */
    private static void askAiServer(MainFrame frame, AppState state, AiConnection c, HomePanel home) {
        if (c.usesKeyFile()) {
            javax.swing.JOptionPane.showMessageDialog(frame, "이 PC에는 config/ai.properties 키가 있어 키를 사용합니다.\n"
                    + "AI 서버 주소는 키가 없는 PC에서 씁니다.", "AI 서버 주소", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object in = javax.swing.JOptionPane.showInputDialog(frame,
                "팀에서 받은 AI 서버 주소를 넣으세요 (예: https://….trycloudflare.com).\n"
                        + "비우고 확인을 누르면 주소를 지우고 AI 기능을 끕니다.\n지금 상태: " + c.status(),
                "AI 서버 주소", javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, c.serverUrl());
        if (in == null) return; // 취소
        String url = in.toString().trim();
        String problem = AiServerSettings.problem(url);
        if (problem != null) {
            javax.swing.JOptionPane.showMessageDialog(frame, problem, "AI 서버 주소", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            c.saveUrl(url);
        } catch (java.io.IOException e) {
            javax.swing.JOptionPane.showMessageDialog(frame, "주소를 저장하지 못했습니다: " + e.getMessage(), "AI 서버 주소",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        state.setAiNotice(url.isEmpty() ? AiConnection.STATUS_OFF : AiConnection.STATUS_CHECKING);
        frame.refresh();
        connectInBackground(frame, state, c, home, true);
    }
}

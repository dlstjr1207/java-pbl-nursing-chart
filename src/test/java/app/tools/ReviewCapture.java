package app.tools;

import app.cases.CaseLoader;
import app.domain.Attempt;
import app.domain.ModelAnswer;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.scoring.RawNotesChecker;
import app.storage.InMemoryAttemptRepository;
import app.ui.AppState;
import app.ui.HomeController;
import app.ui.HomePanel;
import app.ui.MainFrame;
import app.ui.MedicationPanel;
import app.ui.ObservationPanel;
import app.ui.PatientInfoPanel;
import app.ui.ResultPanel;
import app.ui.ResultView;
import app.ui.SbarController;
import app.ui.SbarPanel;
import app.ui.Screen;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * 결과 화면에서 제출한 기록을 읽기 전용으로 다시 보는 흐름을 그림 파일로 남기는 점검 도구 (개발용, 프로그램에 포함 안 됨).
 * 프로그램과 같은 MainFrame·화면 패널을 조립하고, PC-01로 실제 제출 경로(SbarController)를 거친다(AI 없음).
 * 창을 화면에 띄우지 않고 컴포넌트만 그리므로 다른 화면 내용은 찍히지 않는다.
 * 흐름: 제출 → 결과 → 환자 정보(읽기 전용) → 결과로 돌아가기, 개인 메인 → 과거 기록 다시 열기 → 결과 → 환자 정보 → 결과로 돌아가기.
 * 사용: ReviewCapture &lt;출력 폴더&gt;
 */
public final class ReviewCapture {
    private static File dir;
    private static int n;

    public static void main(String[] args) throws Exception {
        dir = new File(args[0]);
        dir.mkdirs();
        SwingUtilities.invokeAndWait(ReviewCapture::run);
        System.exit(0);
    }

    private static void run() {
        MainFrame.installTheme();
        AppState state = new AppState();
        MainFrame frame = new MainFrame(state);
        InMemoryAttemptRepository repo = new InMemoryAttemptRepository();
        HomePanel home = new HomePanel(new HomeController(state, new CaseLoader()), frame::refresh,
                () -> new ResultView(repo, 1L));
        home.setOnOpen(a -> {
            if (state.showResult(a)) frame.refresh(); // Main과 같은 과거 기록 열기
        });
        frame.register(Screen.HOME, home);
        frame.register(Screen.RESULT, new ResultPanel(state, frame::show));
        frame.register(Screen.PATIENT, new PatientInfoPanel(state, frame::show));
        frame.register(Screen.OBSERVATION, new ObservationPanel(state, frame::show));
        frame.register(Screen.MEDICATION, new MedicationPanel(state, frame::show));
        SbarController sbarController = new SbarController(state, repo, () -> 1L);
        SbarPanel sbar = new SbarPanel(sbarController, frame::show);
        frame.register(Screen.SBAR, sbar);
        frame.addDraftCheck(sbar::hasDraft);
        frame.pack();
        frame.setSize(1280, 900);

        // 실습: 관련 있는 메모를 관찰 서술에 그대로 붙여 넣은 기록 (결과에 '원자료를 그대로 옮겼습니다'가 나와 메모를 다시 보고 싶어지는 경우)
        PatientCase pc01 = new CaseLoader().load("PC-01");
        ModelAnswer m = pc01.modelAnswer();
        ObservationEntry o = m.observationRecord();
        String pasted = RawNotesChecker.allNotes(pc01.rawNotes().stream().filter(x -> !x.irrelevant()).toList());
        state.login("학생");
        state.startPractice(pc01);
        state.addObservation(new ObservationEntry(o.time(), o.shift(), o.tags(), pasted, o.intervention(), o.vitals()));
        state.addMedication(m.medicationRecord());
        var s = m.sbarReport();
        var r = sbarController.submit(s.situation(), s.background(), s.assessment(), s.recommendations());
        if (!r.submitted()) throw new IllegalStateException(r.message());
        frame.refresh();
        shot(frame, "결과-제출직후");
        frame.show(Screen.PATIENT);
        shot(frame, "환자정보-읽기전용");
        frame.show(Screen.OBSERVATION);
        shot(frame, "관찰-읽기전용");
        frame.show(Screen.SBAR);
        shot(frame, "SBAR-읽기전용");
        back(frame).doClick();
        shot(frame, "결과로-돌아감");

        // 개인 메인 → 과거 기록 다시 열기
        frame.show(Screen.HOME);
        shot(frame, "개인메인-과거기록");
        Attempt saved = repo.findByUser(1L).get(0);
        if (state.showResult(saved)) frame.refresh();
        shot(frame, "과거기록-결과");
        frame.show(Screen.PATIENT);
        shot(frame, "과거기록-환자정보-읽기전용");
        back(frame).doClick();
        shot(frame, "과거기록-결과로-돌아감");
        System.out.println("상태: 보기 중=" + state.isReviewing() + ", 현재 화면=" + state.current());
        frame.dispose();
    }

    private static JButton back(Container root) {
        return (JButton) find(root, "backToResult");
    }

    private static Component find(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container k) {
                Component f = find(k, name);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static void shot(MainFrame frame, String name) {
        frame.validate();
        frame.getRootPane().validate();
        BufferedImage img = new BufferedImage(frame.getRootPane().getWidth(), frame.getRootPane().getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        frame.getRootPane().paint(g);
        g.dispose();
        File out = new File(dir, String.format("%02d-%s.png", ++n, name));
        try {
            ImageIO.write(img, "png", out);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        System.out.println(out.getName());
    }
}

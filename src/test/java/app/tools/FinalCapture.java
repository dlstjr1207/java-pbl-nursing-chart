package app.tools;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.AiTransport;
import app.cases.AllowedDrugs;
import app.cases.CaseGenerator;
import app.cases.CaseLoader;
import app.cases.CaseValidator;
import app.domain.Medication;
import app.domain.ModelAnswer;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.Shift;
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

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * D38(1차 제출 마지막 묶음) 화면을 그림 파일로 남기는 점검 도구 (개발용, 프로그램에 포함 안 됨).
 * 프로그램과 같은 MainFrame·화면 패널을 조립하고 버튼을 코드로 눌러 실제 저장·제출 경로를 거친다(AI 없음).
 * "생성 중 (n/5)"는 가짜 AI 응답(1번째는 깨진 응답, 2번째는 몇 초 기다림)으로 실제 생성 흐름을 돌리는 중에 그린다.
 * 창을 화면에 띄우지 않고 컴포넌트만 그리므로 다른 화면 내용은 찍히지 않는다.
 * 사용: FinalCapture &lt;출력 폴더&gt;
 */
public final class FinalCapture {
    private static File dir;
    private static int n;
    private static MainFrame frame;
    private static AppState state;

    public static void main(String[] args) throws Exception {
        dir = new File(args[0]);
        dir.mkdirs();
        try {
            SwingUtilities.invokeAndWait(FinalCapture::practice);
            generating();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }

    /** 가짜 AI: 1번째는 JSON이 아닌 응답, 2번째부터는 4초 기다린 뒤 깨진 응답 */
    private static final class SlowFake implements AiTransport {
        int calls;

        @Override
        public String post(String url, String apiKey, String body) {
            calls++;
            if (calls >= 2) {
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "{\"choices\":[{\"message\":{\"content\":\"(합성) JSON 아님\"}}]}";
        }
    }

    private static void build(HomeController homeController, InMemoryAttemptRepository repo) {
        MainFrame.installTheme();
        frame = new MainFrame(state);
        HomePanel home = new HomePanel(homeController, frame::refresh, () -> new ResultView(repo, 1L));
        frame.register(Screen.HOME, home);
        frame.register(Screen.RESULT, new ResultPanel(state, frame::show));
        frame.register(Screen.PATIENT, new PatientInfoPanel(state, frame::show));
        frame.register(Screen.OBSERVATION, new ObservationPanel(state, frame::show));
        frame.register(Screen.MEDICATION, new MedicationPanel(state, frame::show));
        SbarPanel sbar = new SbarPanel(new SbarController(state, repo, () -> 1L), frame::show);
        frame.register(Screen.SBAR, sbar);
        frame.pack();
        frame.setSize(1280, 960);
    }

    /** 실습: 관찰(서명 표)·정신상태 평가·투약(5원칙)·처방 표기·제출·결과·SBAR 다시 보기 */
    private static void practice() {
        state = new AppState();
        InMemoryAttemptRepository repo = new InMemoryAttemptRepository();
        build(new HomeController(state, new CaseLoader()), repo);
        PatientCase pc01 = new CaseLoader().load("PC-01");
        ModelAnswer m = pc01.modelAnswer();
        state.login("김간호");
        state.startPractice(pc01);
        frame.refresh();
        shot("환자정보-처방표기");

        ObservationEntry o = m.observationRecord();
        state.addObservation(new ObservationEntry("09:20", Shift.DAY, List.of("기타"),
                "데이룸에서 창밖을 보며 서 있음. 질문에 짧게 대답함.", null, null));
        state.addObservation(o);
        frame.show(Screen.OBSERVATION); // 화면을 열 때 목록을 다시 채운다
        // 정신상태 평가 칸에 입력하고 "정신상태 평가 저장"을 누른다 (값은 화면 예시, 정답 키와 비교하지 않음)
        Container obs = (Container) find(frame.getRootPane(), "mse-consciousness").getParent().getParent();
        ((JComboBox<?>) find(obs, "mse-consciousness")).setSelectedItem("명료(alert)");
        List<JTextField> fields = new ArrayList<>();
        collect(obs, JTextField.class, fields);
        String[] v = {"환자복 단정, 데이룸을 왔다 갔다 함", "\"무서워요\"라고 말함, 불안해 보이는 표정", "", "환청 없다고 말함", "시간·장소·사람 정확히 답함"};
        for (int i = 0; i < fields.size() && i < v.length; i++) fields.get(i).setText(v[i]);
        button(obs, "정신상태 평가 저장").doClick();
        frame.refresh();
        shot("관찰-서명표-정신상태평가");

        frame.show(Screen.MEDICATION);
        Container med = frame.getRootPane();
        @SuppressWarnings("unchecked")
        JComboBox<Medication> box = (JComboBox<Medication>) findType(med, JComboBox.class, "orderBox-candidate");
        for (int i = 0; i < box.getItemCount(); i++) if (box.getItemAt(i).orderId().equals("PC01-LZP-PRN")) box.setSelectedIndex(i);
        var mr = m.medicationRecord();
        fillMedication(med, mr.dose(), mr.unit(), mr.route(), mr.administeredTime(), mr.reason(), mr.orderBasis());
        radio(med, "투여").setSelected(true);
        for (String r : List.of("약물", "용량", "경로")) ((JCheckBox) find(med, "right-" + r)).setSelected(true);
        button(med, "투약 기록 저장").doClick();
        shot("투약-5Rights-미체크-저장막힘");
        for (String r : List.of("대상자", "시간")) ((JCheckBox) find(med, "right-" + r)).setSelected(true);
        button(med, "투약 기록 저장").doClick();
        shot("투약-5Rights-저장-서명");

        var s = m.sbarReport();
        var r = new SbarController(state, repo, () -> 1L).submit(s.situation(), s.background(), s.assessment(), s.recommendations());
        if (!r.submitted()) throw new IllegalStateException(r.message());
        state.showResult(r.attempt());
        frame.setSize(1280, 3000); // 결과 화면은 길어서 아래 "내가 제출한 기록"까지 보이게 키워 그린다
        frame.refresh();
        shot("결과-서명-5Rights-정신상태-SBAR한문단");
        frame.setSize(1280, 960);
        frame.show(Screen.SBAR);
        shot("SBAR-다시보기-실제인계기록형태");
        frame.dispose();
    }

    /** "생성 중 (n/5)": 실제 생성 흐름(SwingWorker)을 돌리는 중 2번째 시도에서 그린다 */
    private static void generating() throws Exception {
        AtomicReference<JButton> ai = new AtomicReference<>();
        AtomicReference<JLabel> label = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            state = new AppState();
            AllowedDrugs drugs = AllowedDrugs.load();
            CaseGenerator gen = new CaseGenerator(new AiClient(new AiConfig("test", "fake-key", "fake-model", "http://fake.local/v1"),
                    new SlowFake()), new CaseValidator(drugs), new CaseLoader(), CaseGenerator.DEFAULT_MAX_ATTEMPTS);
            build(new HomeController(state, new CaseLoader(), gen), new InMemoryAttemptRepository());
            state.login("김간호");
            frame.refresh();
            ai.set(button(frame.getRootPane(), "AI로 새 케이스 생성"));
            ai.get().doClick();
        });
        for (int i = 0; i < 100; i++) {
            Thread.sleep(100);
            AtomicReference<String> t = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> t.set(text(frame.getRootPane(), "생성 중 (")));
            if (t.get() != null && t.get().contains("(2/")) break;
        }
        SwingUtilities.invokeAndWait(() -> shot("개인메인-생성중"));
        System.out.println("생성 중 문구: " + text(frame.getRootPane(), "생성 중 ("));
    }

    private static void fillMedication(Container root, String dose, String unit, String route, String time, String reason, String basis) {
        // 투약 입력 칸 순서: 용량·단위·경로·시각·사유·처방 근거 (MedicationPanel 배치 순서)
        List<JTextField> f = new ArrayList<>();
        collect(root, JTextField.class, f);
        f.removeIf(x -> !x.isShowing() && !x.isVisible());
        String[] v = {dose, unit, route, time, reason, basis};
        for (int i = 0; i < v.length && i < f.size(); i++) f.get(i).setText(v[i]);
    }

    private static <T> void collect(Container root, Class<T> type, List<T> out) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && c.isVisible() && inVisibleTree(c)) out.add(type.cast(c));
            if (c instanceof Container k) collect(k, type, out);
        }
    }

    private static boolean inVisibleTree(Component c) {
        // 창은 화면에 띄우지 않으므로 루트 패널까지만 본다
        for (Component p = c; p != null && !(p instanceof javax.swing.JRootPane); p = p.getParent()) if (!p.isVisible()) return false;
        return true;
    }

    private static Component findType(Container root, Class<?> type, String ignored) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && inVisibleTree(c) && !"mse-consciousness".equals(c.getName())) return c;
            if (c instanceof Container k) {
                Component f = findType(k, type, ignored);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static javax.swing.JRadioButton radio(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof javax.swing.JRadioButton b && text.equals(b.getText()) && inVisibleTree(b)) return b;
            if (c instanceof Container k) {
                javax.swing.JRadioButton f = radio(k, text);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static JButton button(Container root, String text) {
        JButton b = findButton(root, text);
        if (b == null) throw new IllegalStateException("버튼 없음: " + text);
        return b;
    }

    private static JButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText()) && inVisibleTree(b)) return b;
            if (c instanceof Container k) {
                JButton f = findButton(k, text);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static String text(Container root, String part) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l && l.getText() != null && l.getText().contains(part)) return l.getText();
            if (c instanceof AbstractButton) continue;
            if (c instanceof Container k) {
                String f = text(k, part);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static Component find(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName()) && inVisibleTree(c)) return c;
            if (c instanceof Container k) {
                Component f = find(k, name);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static void invalidateAll(Component c) {
        c.invalidate();
        if (c instanceof Container k) for (Component x : k.getComponents()) invalidateAll(x);
    }

    private static void shot(String name) {
        // 줄바꿈 글칸은 폭이 정해진 뒤 높이가 바뀌므로 두 번 배치한다
        // (실제 창에서는 글칸이 스스로 다시 배치를 요청하지만, 여기서는 한 작업 안에서 바로 그리므로 직접 무효화한다)
        for (int i = 0; i < 2; i++) {
            invalidateAll(frame.getRootPane());
            frame.validate();
            frame.getRootPane().validate();
        }
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

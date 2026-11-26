package app.tools;

import app.Main;
import app.domain.MedicationRecord;
import app.domain.ModelAnswer;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import app.ui.AppState;
import app.ui.LoginPanel;
import app.ui.MainFrame;
import app.ui.MedicationPanel;
import app.ui.ObservationPanel;
import app.ui.SbarPanel;
import app.ui.Screen;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 시연 계정 실습 도구 (개발용, 프로그램에 포함 안 됨). 프로그램과 같은 창(Main.createFrame)을 실제 DB·AI 설정으로 만들고,
 * 로그인 → 실습 → 제출을 화면 입력칸과 버튼으로 한다. 입력 값은 케이스의 모범 기록을 그대로 쓴다(오타 없음).
 * 창을 화면에 띄우지 않고 컴포넌트만 그린다. 비밀번호는 파일에서 읽고 출력하지 않는다. 키·DB 접속 정보도 출력하지 않는다.
 * 사용: DemoSession &lt;출력 폴더&gt; &lt;비밀번호 파일&gt; [ai] (ai를 주면 AI 환자 생성 실습만 한다)
 */
public final class DemoSession {
    private static File dir;
    private static MainFrame frame;
    private static AppState state;
    private static int n;

    public static void main(String[] args) throws Exception {
        dir = new File(args[0]);
        dir.mkdirs();
        String pw = Files.readString(Path.of(args[1])).strip();
        boolean aiOnly = args.length > 2 && args[2].equals("ai");
        // show <번호> <시작 번호>: 새 실습 없이 저장된 기록을 열어 결과·다시 보기만 다시 그린다
        boolean show = args.length > 4 && args[2].equals("show");
        n = aiOnly ? 20 : 0;
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame.installTheme();
                frame = Main.createFrame(Path.of("config", "db.properties"),
                        Path.of(System.getProperty("user.home"), ".nursing-chart-trainer"), Path.of("config", "ai.properties"));
                frame.pack();
                frame.setSize(1280, 960);
                state = (AppState) get(frame, "state");
            });
            System.out.println("저장 방식 안내: " + onEdt(() -> String.valueOf(state.notices())));
            // DB에 연결되지 않으면 사용자 폴더의 파일에 저장되므로 멈춘다 (시연 기록은 DB에 남겨야 한다)
            if (onEdt(() -> state.notices().contains(AppState.FILE_MODE_NOTICE))) throw new IllegalStateException("DB에 연결되지 않아 멈춤");
            login(pw);
            if (show) {
                n = Integer.parseInt(args[4]);
                showRecord(Long.parseLong(args[3]));
            } else if (aiOnly) aiPractice();
            else pc01Practice();
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            System.exit(0);
        }
    }

    // ---------- 흐름 ----------

    private static void login(String pw) throws Exception {
        edt(() -> {
            LoginPanel lp = first(frame.getRootPane(), LoginPanel.class);
            ((JTextField) get(lp, "idField")).setText("demo01");
            ((JPasswordField) get(lp, "pwField")).setText(pw);
            button(lp, "로그인").doClick();
        });
        String now = onEdt(() -> state.current().name());
        System.out.println("로그인 후 화면: " + now + ", 사용자: " + onEdt(state::userName));
        if (!"HOME".equals(now)) throw new IllegalStateException("로그인 실패");
    }

    private static void pc01Practice() throws Exception {
        edt(() -> button(frame.getRootPane(), "실습 시작").doClick());
        practice("PC01");
        shotTall("결과-AI해설");
        edt(() -> frame.show(Screen.PATIENT));
        shot("다시보기-환자정보");
        edt(() -> frame.show(Screen.OBSERVATION));
        shot("다시보기-관찰-정신상태평가");
        edt(() -> frame.show(Screen.MEDICATION));
        shot("다시보기-투약");
        edt(() -> frame.show(Screen.SBAR));
        shot("다시보기-SBAR");
        edt(() -> button(frame.getRootPane(), "결과로 돌아가기").doClick());
        shot("결과로-돌아감");
    }

    /** 개인 메인 과거 기록 목록에서 그 번호를 눌러 결과를 연다 (Main과 같은 경로) */
    private static void showRecord(long id) throws Exception {
        edt(() -> {
            app.ui.HomePanel home = first(frame.getRootPane(), app.ui.HomePanel.class);
            home.reload();
            @SuppressWarnings("unchecked")
            var list = (javax.swing.JList<app.domain.Attempt>) get(home, "historyList");
            for (int i = 0; i < list.getModel().getSize(); i++) {
                if (list.getModel().getElementAt(i).attemptId() == id) list.setSelectedIndex(i);
            }
        });
        var a = onEdt(state::resultAttempt);
        if (a == null || a.attemptId() != id) throw new IllegalStateException("기록을 열지 못함: " + id);
        System.out.println("열림: 번호 " + a.attemptId() + ", 케이스 " + a.caseId() + ", 점수 " + a.score());
        boolean ai = a.caseSource() == app.domain.CaseSource.AI_GENERATED;
        shotTall(ai ? "AI환자-결과-AI해설" : "결과-AI해설");
        edt(() -> frame.show(Screen.PATIENT));
        shot(ai ? "AI환자-다시보기-환자정보" : "다시보기-환자정보");
        if (ai) {
            edt(() -> frame.show(Screen.SBAR));
            shot("AI환자-다시보기-SBAR");
            return;
        }
        edt(() -> frame.show(Screen.OBSERVATION));
        shot("다시보기-관찰-정신상태평가");
        edt(() -> frame.show(Screen.MEDICATION));
        shot("다시보기-투약");
        edt(() -> frame.show(Screen.SBAR));
        shot("다시보기-SBAR");
        edt(() -> button(frame.getRootPane(), "결과로 돌아가기").doClick());
        shot("결과로-돌아감");
    }

    private static void aiPractice() throws Exception {
        edt(() -> button(frame.getRootPane(), "AI로 새 케이스 생성").doClick());
        // 생성은 시도마다 수십 초 걸릴 수 있다 (최대 5번)
        for (int i = 0; i < 600; i++) {
            Thread.sleep(1000);
            if (onEdt(() -> state.current() != Screen.HOME)) break;
        }
        PatientCase c = onEdt(state::currentCase);
        if (c == null) throw new IllegalStateException("AI 케이스로 실습을 시작하지 못함");
        System.out.println("케이스: " + c.id() + " (" + c.source() + "), 과제: " + c.task());
        // 생성에 실패해 기본 케이스로 돌아갔으면 실습하지 않는다 (AI 환자 기록만 남긴다)
        if (c.source() != app.domain.CaseSource.AI_GENERATED) {
            System.out.println("AI 생성 실패로 기본 케이스가 열림: " + onEdt(() -> text(frame.getRootPane())));
            return;
        }
        practice("AI");
        shotTall("AI환자-결과-AI해설");
        edt(() -> frame.show(Screen.PATIENT));
        shot("AI환자-다시보기-환자정보");
    }

    /** 현재 케이스의 모범 기록으로 관찰·정신상태 평가·투약·SBAR를 입력하고 제출한다. */
    private static void practice(String label) throws Exception {
        PatientCase c = onEdt(state::currentCase);
        ModelAnswer m = c.modelAnswer();
        shot(label + "-환자정보");

        // 관찰
        ObservationEntry o = m.observationRecord();
        edt(() -> {
            frame.show(Screen.OBSERVATION);
            ObservationPanel p = first(frame.getRootPane(), ObservationPanel.class);
            ((JTextField) get(p, "timeField")).setText(o.time());
            @SuppressWarnings("unchecked")
            var shifts = (Map<app.domain.Shift, JRadioButton>) get(p, "shiftButtons");
            shifts.get(o.shift()).setSelected(true);
            @SuppressWarnings("unchecked")
            var tags = (List<JCheckBox>) get(p, "tagBoxes");
            for (JCheckBox b : tags) b.setSelected(o.tags().contains(b.getText()));
            ((JTextArea) get(p, "narrativeArea")).setText(o.narrative());
            ((JTextField) get(p, "interventionField")).setText(o.intervention() == null ? "" : o.intervention());
            button(p, "관찰 기록 저장").doClick();
            // 정신상태 평가: 원자료 메모에 있는 관찰만 옮긴다. 의식 수준은 학생이 관찰해 고르는 칸이다
            // (환자가 말에 대답하고 걸어 다니므로 명료). 메모에 없는 지각·지남력은 비워 둔다
            @SuppressWarnings("unchecked")
            JComboBox<String> level = (JComboBox<String>) get(p, "consciousnessBox");
            level.setSelectedItem("명료(alert)");
            if ("PC01".equals(label)) {
                ((JTextField) get(p, "appearanceField")).setText("데이룸 창가 쪽을 왔다 갔다 서성거림, 손톱을 물어뜯음");
                ((JTextField) get(p, "thoughtField")).setText("\"누가 나를 감시하는 것 같아요… 계속 봐요\"라고 말함");
                ((JTextField) get(p, "moodField")).setText("재평가 때 \"이제… 좀 괜찮아요\"라고 말함");
            } else {
                // AI 환자: 메모의 관찰을 맞는 칸에 옮긴다. 외모·행동 = 사건 관찰 메모,
                // 환자 말 = 들리거나 보인다는 말이면 지각(환청·환시), 아니면 사고 내용, 기분·정동 = 재평가 메모 중 불안·차분 문장
                ((JTextField) get(p, "appearanceField")).setText(memo(c, "EVENT"));
                String speech = memo(c, "SPEECH");
                boolean perceptual = speech.matches(".*(들려|들린|소리|보여|보인|목소리).*");
                ((JTextField) get(p, perceptual ? "perceptionField" : "thoughtField")).setText(speech);
                String mood = java.util.Arrays.stream(memo(c, "REASSESS").split("(?<=[.])\\s*"))
                        .filter(x -> x.matches(".*(불안|차분|안정|초조|편안|괜찮).*")).findFirst().orElse("");
                ((JTextField) get(p, "moodField")).setText(mood.strip());
            }
            button(p, "정신상태 평가 저장").doClick();
        });
        System.out.println("관찰 저장: " + onEdt(() -> state.studentObservations().size()) + "건, 정신상태 평가: "
                + onEdt(() -> String.valueOf(state.mentalStatus())));
        shot(label + "-관찰");

        // 투약 (5원칙 모두 체크)
        MedicationRecord r = m.medicationRecord();
        edt(() -> {
            frame.show(Screen.MEDICATION);
            MedicationPanel p = first(frame.getRootPane(), MedicationPanel.class);
            @SuppressWarnings("unchecked")
            JComboBox<app.domain.Medication> box = (JComboBox<app.domain.Medication>) get(p, "orderBox");
            for (int i = 0; i < box.getItemCount(); i++) if (box.getItemAt(i).orderId().equals(r.orderId())) box.setSelectedIndex(i);
            @SuppressWarnings("unchecked")
            var rights = (List<JCheckBox>) get(p, "rightsBoxes");
            rights.forEach(b -> b.setSelected(true));
            ((JTextField) get(p, "doseField")).setText(r.dose());
            ((JTextField) get(p, "unitField")).setText(r.unit());
            ((JTextField) get(p, "routeField")).setText(r.route());
            ((JTextField) get(p, "timeField")).setText(r.administeredTime());
            @SuppressWarnings("unchecked")
            var results = (Map<app.domain.MedicationResult, JRadioButton>) get(p, "resultButtons");
            results.get(r.result()).setSelected(true);
            ((JTextField) get(p, "reasonField")).setText(r.reason() == null ? "" : r.reason());
            ((JTextField) get(p, "basisField")).setText(r.orderBasis());
            ((JCheckBox) get(p, "oralBox")).setSelected(Boolean.TRUE.equals(r.oralCheck()));
            ((JTextArea) get(p, "planArea")).setText(r.reassessmentPlan() == null ? "" : r.reassessmentPlan());
            button(p, "투약 기록 저장").doClick();
        });
        System.out.println("투약 저장: " + onEdt(() -> state.studentMedications().size()) + "건");
        shot(label + "-투약");

        // SBAR
        SbarReport s = m.sbarReport();
        edt(() -> {
            frame.show(Screen.SBAR);
            SbarPanel p = first(frame.getRootPane(), SbarPanel.class);
            ((JTextArea) get(p, "sArea")).setText(s.situation());
            ((JTextArea) get(p, "bArea")).setText(s.background());
            ((JTextArea) get(p, "aArea")).setText(s.assessment());
            for (SbarRecommendation x : s.recommendations()) {
                ((JTextField) get(p, "timeField")).setText(x.time());
                ((JTextField) get(p, "interField")).setText(x.intervention());
                button(p, "행 추가").doClick();
            }
        });
        shot(label + "-SBAR");
        edt(() -> button(first(frame.getRootPane(), SbarPanel.class), "SBAR 제출").doClick());
        for (int i = 0; i < 300; i++) { // AI 해설을 받는 동안 기다린다
            Thread.sleep(1000);
            if (onEdt(() -> state.current() == Screen.RESULT)) break;
        }
        var a = onEdt(state::resultAttempt);
        if (a == null) throw new IllegalStateException("제출 결과가 없음: " + onEdt(() -> text(frame.getRootPane())));
        System.out.println("제출: 번호 " + a.attemptId() + ", 케이스 " + a.caseId() + ", 점수 " + a.score()
                + ", AI 해설 " + (a.aiFeedback() == null ? "NONE" : a.aiFeedback().status()));
        a.checks().stream().filter(x -> x.status() != app.domain.CheckStatus.PASS)
                .forEach(x -> System.out.println("  - " + x.ruleId() + " " + x.status() + " " + x.message()));
    }

    /** 그 종류의 가장 이른 메모 글 (없으면 빈 글) */
    private static String memo(PatientCase c, String kind) {
        return c.rawNotes().stream().filter(x -> kind.equals(x.kind())).min((x, y) -> x.time().compareTo(y.time()))
                .map(app.domain.RawNote::text).orElse("");
    }

    // ---------- 도우미 ----------

    interface Body {
        void run() throws Exception;
    }

    private static void edt(Body b) throws Exception {
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                b.run();
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) throw err.get();
    }

    private static <T> T onEdt(Callable<T> c) throws Exception {
        AtomicReference<T> v = new AtomicReference<>();
        edt(() -> v.set(c.call()));
        return v.get();
    }

    private static Object get(Object o, String name) {
        try {
            for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
                try {
                    Field f = k.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(o);
                } catch (NoSuchFieldException ignored) {
                    // 부모 클래스에서 찾는다
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("필드 없음: " + name);
    }

    private static <T> T first(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && visible(c)) return type.cast(c);
            if (c instanceof Container k) {
                T f = first(k, type);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static boolean visible(Component c) {
        for (Component p = c; p != null && !(p instanceof javax.swing.JRootPane); p = p.getParent()) if (!p.isVisible()) return false;
        return true;
    }

    private static JButton button(Container root, String text) {
        JButton b = findButton(root, text);
        if (b == null) throw new IllegalStateException("버튼 없음: " + text);
        return b;
    }

    private static JButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText()) && visible(b)) return b;
            if (c instanceof Container k) {
                JButton f = findButton(k, text);
                if (f != null) return f;
            }
        }
        return null;
    }

    /** 화면 글 (오류 확인용, 입력칸 글은 빼고 라벨만) */
    private static String text(Container root) {
        List<String> out = new ArrayList<>();
        collectLabels(root, out);
        return String.join(" / ", out);
    }

    private static void collectLabels(Container root, List<String> out) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l && visible(l) && l.getText() != null && !l.getText().isBlank()) out.add(l.getText());
            if (c instanceof AbstractButton) continue;
            if (c instanceof Container k) collectLabels(k, out);
        }
    }

    private static void invalidateAll(Component c) {
        c.invalidate();
        if (c instanceof Container k) for (Component x : k.getComponents()) invalidateAll(x);
    }

    private static void shot(String name) throws Exception {
        edt(() -> paint(name));
    }

    /** 결과 화면처럼 긴 화면은 창을 길게 늘려 그린 뒤 되돌린다 */
    private static void shotTall(String name) throws Exception {
        edt(() -> {
            frame.setSize(1280, 3400);
            frame.refresh();
            paint(name);
            frame.setSize(1280, 960);
            frame.refresh();
        });
    }

    private static void paint(String name) throws Exception {
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
        ImageIO.write(img, "png", out);
        System.out.println(out.getName());
    }
}

package app.tools;

import app.Main;
import app.ui.MainFrame;
import app.ui.Screen;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * 실제 메인 창(Main.createFrame)을 띄워 버튼을 코드로 누르며 실습 한 바퀴를 돌고, 단계마다 창을 캡처하는
 * 점검 도구 (개발용, 프로그램에는 포함되지 않음). macOS의 screencapture를 쓴다.
 *
 * <p>실행: ./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=app.tools.UiWalkthrough
 * -Dexec.args="출력폴더 [db설정파일]" — db설정파일을 주지 않으면 임시 폴더의 파일 저장 모드로 실행한다.
 */
public final class UiWalkthrough {
    private static MainFrame frame;
    private static Path out;
    private static final List<String> log = new ArrayList<>();
    private static boolean aiOn;

    public static void main(String[] args) throws Exception {
        out = Path.of(args.length > 0 ? args[0] : "target/walkthrough");
        Files.createDirectories(out);
        Path db = args.length > 1 ? Path.of(args[1]) : Path.of("does-not-exist.properties");
        // 세 번째 인자로 AI 설정 파일을 주면 AI 해설·AI 케이스 생성까지 확인한다 (키는 출력하지 않음)
        Path ai = args.length > 2 ? Path.of(args[2]) : Path.of("does-not-exist-ai.properties");
        aiOn = app.ai.AiConfig.load(ai).isEnabled();
        Path data = Files.createTempDirectory("walkthrough-data");
        SwingUtilities.invokeAndWait(() -> {
            MainFrame.installTheme();
            frame = Main.createFrame(db, data, ai);
            frame.setBounds(20, 40, 1280, 820);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
        });
        try {
            run();
        } finally {
            Files.write(out.resolve("walkthrough.txt"), log);
            log.forEach(System.out::println);
            SwingUtilities.invokeAndWait(frame::dispose);
            System.exit(0);
        }
    }

    private static void run() throws Exception {
        shot("00-login");
        // 회원가입 → 로그인 (합성 계정)
        edt(() -> clickText(panel(Screen.LOGIN), "회원가입"));
        edt(() -> {
            Object p = panel(Screen.SIGNUP);
            set(p, "nameField", "점검 사용자");
            set(p, "idField", "check01");
            set(p, "pwField", "check-pass-01");
            set(p, "confirmField", "check-pass-01");
        });
        shot("00b-signup");
        edt(() -> clickText(panel(Screen.SIGNUP), "가입하기"));
        edt(() -> {
            Object p = panel(Screen.LOGIN);
            set(p, "idField", "check01");
            set(p, "pwField", "check-pass-01");
            clickText((Container) p, "로그인");
        });
        shot("01-home");
        // 실습 시작 → 환자 정보
        edt(() -> clickText(panel(Screen.HOME), "실습 시작"));
        String info = call(() -> allText(panel(Screen.PATIENT)));
        check("환자 정보 화면에 케이스 내용이 보인다", !info.contains("실습 중인 케이스가 없습니다") && info.length() > 50);
        shot("02-patient");
        // 관찰 기록 저장
        edt(() -> frame.show(Screen.OBSERVATION));
        edt(() -> {
            Object p = panel(Screen.OBSERVATION);
            set(p, "timeField", "09:25");
            ((Map<?, ? extends AbstractButton>) get(p, "shiftButtons")).values().iterator().next().doClick();
            ((List<? extends AbstractButton>) get(p, "tagBoxes")).get(0).doClick();
            set(p, "narrativeArea", "데이룸에서 서성이며 손톱을 물어뜯는 모습이 관찰됨. 환자는 \"누가 나를 감시하는 것 같아요\"라고 말함. 조용한 곳으로 이동해 1:1로 지지적 대화를 함. 09:30 PRN 처방에 따라 로라제팜 1mg PO 투여함. 10:00(투여 30분 후) 불안 정도 재평가 예정.");
            set(p, "interventionField", "조용한 곳으로 이동, 1:1 지지, 로라제팜 1mg PO 투여(09:30)");
            clickText((Container) p, "관찰 기록 저장");
        });
        String obs = call(() -> allText(panel(Screen.OBSERVATION)));
        check("관찰 기록이 목록에 [학생]으로 들어간다", obs.contains("[학생]"));
        shot("03-observation");
        // 투약: 처방 선택지 확인 후 저장
        edt(() -> frame.show(Screen.MEDICATION));
        int orders = call(() -> ((JComboBox<?>) get(panel(Screen.MEDICATION), "orderBox")).getItemCount());
        check("투약 화면에 처방 선택지가 있다 (" + orders + "개)", orders > 0);
        edt(() -> {
            Object p = panel(Screen.MEDICATION);
            JComboBox<?> box = (JComboBox<?>) get(p, "orderBox");
            for (int i = 0; i < box.getItemCount(); i++) {
                if (String.valueOf(box.getItemAt(i)).contains("PRN")) box.setSelectedIndex(i);
            }
            set(p, "doseField", "1");
            set(p, "unitField", "mg");
            set(p, "routeField", "PO");
            set(p, "timeField", "09:30");
            ((Map<?, ? extends AbstractButton>) get(p, "resultButtons")).values().iterator().next().doClick();
            set(p, "reasonField", "불안·초조: 데이룸에서 서성임, 손톱 물어뜯기, \"누가 나를 감시하는 것 같아요\" 발언");
            set(p, "basisField", "PRN 처방(불안·초조 시 Lorazepam 1mg PO, 1일 최대 4mg)");
            ((AbstractButton) get(p, "oralBox")).setSelected(true);
            set(p, "planArea", "10:00 불안 VAS·RASS 재평가");
            clickText((Container) p, "투약 기록 저장");
        });
        String med = call(() -> allText(panel(Screen.MEDICATION)));
        check("투약 기록이 목록에 [학생]으로 들어간다", med.contains("[학생]"));
        shot("04-medication");
        // SBAR 제출 (백그라운드 저장) → 개인 메인
        edt(() -> frame.show(Screen.SBAR));
        edt(() -> {
            Object p = panel(Screen.SBAR);
            set(p, "sArea", "09:25 데이룸에서 불안·초조(서성임, 손톱 물어뜯기, \"누가 나를 감시하는 것 같아요\" 발언)가 있어 1:1 지지 후 09:30 PRN 로라제팜 1mg PO 투여함.");
            set(p, "bArea", "28세 여성, 조현정동장애(양극형)로 동의입원 14일차. 현재 처방 아리피프라졸 10mg PO 1일 1회, 탄산리튬 300mg PO 1일 3회, PRN 로라제팜 1mg PO.");
            set(p, "aArea", "10:00 재평가에서 불안 VAS 8→2, RASS 0, 자리에 앉아 휴식함.");
            set(p, "timeField", "16:00");
            set(p, "interField", "불안·초조 재발 여부 관찰");
            clickText((Container) p, "행 추가");
            set(p, "timeField", "18:30");
            set(p, "interField", "탄산리튬 300mg PO 투약");
        });
        shot("05-sbar");
        edt(() -> clickText(panel(Screen.SBAR), "SBAR 제출"));
        // AI 해설을 기다리는 동안 제출 문구가 보이는지 캡처하고, 저장이 끝날 때까지 기다린다 (최대 3분)
        if (aiOn) shot("05b-sbar-submitting");
        for (int i = 0; i < 900 && call(() -> frame.getTitle()).contains("SBAR"); i++) Thread.sleep(200);
        String home = call(() -> allText(panel(Screen.HOME)));
        check("제출 후 개인 메인으로 돌아와 기록이 보인다", call(() -> frame.getTitle()).contains("개인 메인")
                && !home.contains("아직 저장된 실습 기록이 없습니다"));
        shot("06-home-after-submit");
        // 결과: 방금 제출한 기록을 골라 상세(점검 결과·점수·모범 기록)를 본다
        edt(() -> ((javax.swing.JList<?>) get(panel(Screen.HOME), "historyList")).setSelectedIndex(0));
        String detail = call(() -> ((JTextComponent) get(panel(Screen.HOME), "detailArea")).getText());
        check("결과 상세에 점수와 모범 기록이 보인다 (" + detail.lines().findFirst().orElse("") + ")",
                detail.contains("점") && detail.contains("[모범 기록]"));
        check("모범 기록대로 입력하면 만점이다", detail.lines().findFirst().orElse("").contains("100점"));
        shot("07-result-detail");
        if (aiOn) runAi(detail);
    }

    /** AI 해설이 결과에 나오는지, AI 생성 케이스로 실습·채점이 되는지 확인한다. */
    private static void runAi(String pc01Detail) throws Exception {
        check("SBAR 제출 후 AI 해설이 결과에 나온다", pc01Detail.contains("AI 해설 (점수에 반영되지 않음)"));
        edt(() -> clickText(panel(Screen.HOME), "AI로 새 케이스 생성"));
        shot("08-ai-generating");
        for (int i = 0; i < 300 && !call(() -> frame.getTitle()).contains("환자 정보"); i++) Thread.sleep(1000);
        app.ui.AppState st = (app.ui.AppState) get(frame, "state");
        app.domain.PatientCase c = call(st::currentCase);
        check("AI로 만든 케이스로 실습이 시작된다 (" + (c == null ? "없음" : c.id() + ", " + c.source()) + ")",
                c != null && c.source() == app.domain.CaseSource.AI_GENERATED);
        check("상단에 'AI 생성 · 임상 검수 전'이 보인다", call(() -> ((JLabel) get(frame, "noticeLabel")).getText()).contains("AI 생성"));
        shot("09-ai-patient");
        if (c == null || c.source() != app.domain.CaseSource.AI_GENERATED) return;
        // 브리핑의 사건을 관찰 기록으로 (발언 인용 포함), 정답 키의 시각으로 PRN 투약 기록
        String medTime = c.answerKey().medicationElements().stream().filter(e -> e.startsWith("시각="))
                .map(e -> e.substring(3).split("\\|")[0]).findFirst().orElse("09:30");
        edt(() -> frame.show(Screen.OBSERVATION));
        edt(() -> {
            Object p = panel(Screen.OBSERVATION);
            set(p, "timeField", medTime);
            ((Map<?, ? extends AbstractButton>) get(p, "shiftButtons")).values().iterator().next().doClick();
            ((List<? extends AbstractButton>) get(p, "tagBoxes")).get(1).doClick();
            set(p, "narrativeArea", c.briefing() + " 30분 후 재평가 예정.");
            set(p, "interventionField", "1:1 지지");
            clickText((Container) p, "관찰 기록 저장");
        });
        edt(() -> frame.show(Screen.MEDICATION));
        app.domain.Medication prn = c.orders().stream().filter(m -> m.type() == app.domain.OrderType.PRN).findFirst().orElseThrow();
        edt(() -> {
            Object p = panel(Screen.MEDICATION);
            JComboBox<?> box = (JComboBox<?>) get(p, "orderBox");
            for (int i = 0; i < box.getItemCount(); i++) {
                if (String.valueOf(box.getItemAt(i)).contains("PRN")) box.setSelectedIndex(i);
            }
            set(p, "doseField", prn.dose());
            set(p, "unitField", prn.unit());
            set(p, "routeField", prn.route());
            set(p, "timeField", medTime);
            ((Map<?, ? extends AbstractButton>) get(p, "resultButtons")).values().iterator().next().doClick();
            set(p, "reasonField", "불안·초조");
            set(p, "basisField", "PRN 처방");
            set(p, "planArea", "30분 후 재평가");
            clickText((Container) p, "투약 기록 저장");
        });
        edt(() -> frame.show(Screen.SBAR));
        edt(() -> {
            Object p = panel(Screen.SBAR);
            set(p, "sArea", "불안·초조로 PRN 투여함.");
            set(p, "bArea", c.patient().diagnosis() + " 입원 중.");
            set(p, "aArea", "재평가 예정.");
            set(p, "timeField", "16:00");
            set(p, "interField", "불안 재발 여부 관찰");
            clickText((Container) p, "SBAR 제출");
        });
        for (int i = 0; i < 120 && call(() -> frame.getTitle()).contains("SBAR"); i++) Thread.sleep(500);
        edt(() -> ((javax.swing.JList<?>) get(panel(Screen.HOME), "historyList")).setSelectedIndex(0));
        String d = call(() -> ((JTextComponent) get(panel(Screen.HOME), "detailArea")).getText());
        String first = d.lines().findFirst().orElse("");
        check("AI 생성 케이스 기록이 저장되고 점수가 나온다 (" + first + ")", first.contains(c.id()) && first.contains("점"));
        check("AI 생성 케이스도 R7·R2로 채점된다", d.contains("R7 ") && d.contains("R2 "));
        shot("10-ai-result");
    }

    // ---- 도우미 ----

    private static void check(String what, boolean ok) {
        log.add((ok ? "통과  " : "실패  ") + what);
    }

    /** 창 영역만 screencapture로 저장한다. */
    private static void shot(String name) throws Exception {
        Thread.sleep(700);
        AtomicReference<Point> p = new AtomicReference<>();
        AtomicReference<java.awt.Dimension> d = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            frame.toFront();
            p.set(frame.getLocationOnScreen());
            d.set(frame.getSize());
        });
        Thread.sleep(300);
        File f = out.resolve(name + ".png").toFile();
        Process pr = new ProcessBuilder("screencapture", "-x", "-R",
                p.get().x + "," + p.get().y + "," + d.get().width + "," + d.get().height, f.getPath())
                .inheritIO().start();
        pr.waitFor();
        log.add("캡처  " + f.getPath());
    }

    private static Container panel(Screen s) {
        try {
            Field f = MainFrame.class.getDeclaredField("panels");
            f.setAccessible(true);
            return (Container) ((Map<?, ?>) f.get(frame)).get(s);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object get(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(field, e);
        }
    }

    private static void set(Object target, String field, String text) {
        ((JTextComponent) get(target, field)).setText(text);
    }

    private static void clickText(Container root, String text) {
        for (Component c : all(root)) {
            if (c instanceof AbstractButton b && text.equals(b.getText()) && b.isShowing()) {
                b.doClick();
                return;
            }
        }
        throw new IllegalStateException("버튼을 찾지 못함: " + text);
    }

    private static String allText(Container root) {
        StringBuilder sb = new StringBuilder();
        for (Component c : all(root)) {
            if (c instanceof JLabel l) sb.append(l.getText()).append('\n');
            else if (c instanceof JTextComponent t) sb.append(t.getText()).append('\n');
            else if (c instanceof javax.swing.JList<?> list) {
                for (int i = 0; i < list.getModel().getSize(); i++) sb.append(list.getModel().getElementAt(i)).append('\n');
            }
        }
        return sb.toString();
    }

    private static List<Component> all(Container root) {
        List<Component> list = new ArrayList<>();
        for (Component c : root.getComponents()) {
            list.add(c);
            if (c instanceof Container k) list.addAll(all(k));
        }
        return list;
    }

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
        Thread.sleep(250);
    }

    private static <T> T call(Callable<T> c) throws Exception {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                ref.set(c.call());
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) throw err.get();
        return ref.get();
    }
}

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

    public static void main(String[] args) throws Exception {
        out = Path.of(args.length > 0 ? args[0] : "target/walkthrough");
        Files.createDirectories(out);
        Path db = args.length > 1 ? Path.of(args[1]) : Path.of("does-not-exist.properties");
        Path data = Files.createTempDirectory("walkthrough-data");
        SwingUtilities.invokeAndWait(() -> {
            MainFrame.installTheme();
            frame = Main.createFrame(db, data, Path.of("does-not-exist-ai.properties"));
            frame.setBounds(40, 60, 1200, 820);
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
            clickText((Container) p, "가입하기");
        });
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
            set(p, "narrativeArea", "합성 서술: 데이룸에서 서성임. \"합성 발언\"이라고 말함.");
            set(p, "interventionField", "합성 처치");
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
            ((JComboBox<?>) get(p, "orderBox")).setSelectedIndex(0);
            set(p, "doseField", "1");
            set(p, "unitField", "mg");
            set(p, "routeField", "PO");
            set(p, "timeField", "09:30");
            ((Map<?, ? extends AbstractButton>) get(p, "resultButtons")).values().iterator().next().doClick();
            set(p, "reasonField", "합성 사유");
            set(p, "basisField", "합성 근거");
            set(p, "planArea", "10:00 재평가");
            clickText((Container) p, "투약 기록 저장");
        });
        String med = call(() -> allText(panel(Screen.MEDICATION)));
        check("투약 기록이 목록에 [학생]으로 들어간다", med.contains("[학생]"));
        shot("04-medication");
        // SBAR 제출 (백그라운드 저장) → 개인 메인
        edt(() -> frame.show(Screen.SBAR));
        edt(() -> {
            Object p = panel(Screen.SBAR);
            set(p, "sArea", "합성 상황");
            set(p, "bArea", "합성 배경");
            set(p, "aArea", "합성 평가");
            set(p, "timeField", "18:30");
            set(p, "interField", "합성 중재");
        });
        shot("05-sbar");
        edt(() -> clickText(panel(Screen.SBAR), "SBAR 제출"));
        for (int i = 0; i < 50 && call(() -> frame.getTitle()).contains("SBAR"); i++) Thread.sleep(200);
        String home = call(() -> allText(panel(Screen.HOME)));
        check("제출 후 개인 메인으로 돌아와 기록이 보인다", call(() -> frame.getTitle()).contains("개인 메인")
                && !home.contains("아직 저장된 실습 기록이 없습니다"));
        shot("06-home-after-submit");
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

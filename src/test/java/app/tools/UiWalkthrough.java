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
        dataDir = data;
        fileMode = !Files.exists(db);
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
            List<String> md = new ArrayList<>(List.of("| 시나리오 | 기대 | 실제 | 결과 |", "|---|---|---|---|"));
            for (String[] r : rows) md.add("| " + String.join(" | ", java.util.Arrays.stream(r).map(x -> x.replace("|", "/").replace("\n", " ")).toList()) + " |");
            Files.write(out.resolve("result-table.md"), md);
            log.forEach(System.out::println);
            SwingUtilities.invokeAndWait(frame::dispose);
            System.exit(0);
        }
    }

    /** 결과표 한 줄: 시나리오, 기대, 실제, 통과. */
    private static final List<String[]> rows = new ArrayList<>();
    private static Path dataDir;
    private static boolean fileMode;

    private static void row(String id, String expected, String actual, boolean ok) {
        rows.add(new String[] {id, expected, actual, ok ? "통과" : "실패"});
        check(id + " " + expected + " → " + actual, ok);
    }

    private static String title() throws Exception {
        return call(() -> frame.getTitle());
    }

    private static String label(Object panel, String field) throws Exception {
        return call(() -> ((JLabel) get(panel, field)).getText()).trim();
    }

    private static void signup(String name, String id, String pw) throws Exception {
        edt(() -> clickText(panel(Screen.LOGIN), "회원가입"));
        edt(() -> {
            Object p = panel(Screen.SIGNUP);
            set(p, "nameField", name);
            set(p, "idField", id);
            set(p, "pwField", pw);
            set(p, "confirmField", pw);
            clickText((Container) p, "가입하기");
        });
    }

    private static void login(String id, String pw) throws Exception {
        edt(() -> {
            Object p = panel(Screen.LOGIN);
            set(p, "idField", id);
            set(p, "pwField", pw);
            clickText((Container) p, "로그인");
        });
    }

    private static void logout() throws Exception {
        edt(() -> clickText(frame.getContentPane(), "로그아웃"));
    }

    private static String detailOf(int index) throws Exception {
        edt(() -> ((javax.swing.JList<?>) get(panel(Screen.HOME), "historyList")).setSelectedIndex(index));
        return call(() -> ((JTextComponent) get(panel(Screen.HOME), "detailArea")).getText());
    }

    private static int historySize() throws Exception {
        return call(() -> ((javax.swing.JList<?>) get(panel(Screen.HOME), "historyList")).getModel().getSize());
    }

    private static void waitSubmit() throws Exception {
        for (int i = 0; i < 900 && title().contains("SBAR"); i++) Thread.sleep(200);
    }

    private static void run() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis() % 1_000_000);
        String idA = "chka" + suffix, idB = "chkb" + suffix, pwA = "check-pass-A1", pwB = "check-pass-B1";
        String loginText = call(() -> allText(panel(Screen.LOGIN)));
        row("저장 방식", fileMode ? "DB 설정이 없으면 파일 저장 모드 안내" : "MySQL에 저장(파일 모드 안내 없음)",
                loginText.contains("파일 저장 모드") ? "파일 저장 모드 안내 표시" : "파일 모드 안내 없음",
                fileMode == loginText.contains("파일 저장 모드"));
        shot("00-login");
        // S1 가입 → 중복 가입 → 틀린 비밀번호 → 정상 로그인
        signup("점검 사용자A", idA, pwA);
        row("S1", "가입 후 로그인 화면으로", title(), title().contains("로그인"));
        signup("점검 사용자A2", idA, pwA);
        String dup = label(panel(Screen.SIGNUP), "idError");
        row("S1", "중복 아이디 가입 거부", dup.isEmpty() ? "오류 없음" : dup, dup.contains("이미"));
        shot("01-signup-duplicate");
        edt(() -> clickText(panel(Screen.SIGNUP), "로그인으로 돌아가기"));
        login(idA, "wrong-pass-00");
        String wrong = label(panel(Screen.LOGIN), "message");
        String unknown;
        login("nouser" + suffix, "wrong-pass-00");
        unknown = label(panel(Screen.LOGIN), "message");
        row("S1", "틀린 비밀번호·없는 아이디의 문구가 같음", "'" + wrong + "' / '" + unknown + "'",
                !wrong.isEmpty() && wrong.equals(unknown));
        shot("02-login-failed");
        if (fileMode) {
            String users = Files.readString(dataDir.resolve("users.json"));
            row("S1", "비밀번호 평문 미저장", users.contains(pwA) ? "평문 발견" : "users.json에 평문 없음", !users.contains(pwA));
        }
        login(idA, pwA);
        row("S1", "정상 로그인 → 개인 메인", title(), title().contains("개인 메인"));
        shot("03-home");
        // S2 실습 시작 → 환자 정보
        edt(() -> clickText(panel(Screen.HOME), "실습 시작"));
        String info = call(() -> allText(panel(Screen.PATIENT)));
        row("S2", "PC-01 환자 정보 표시, 정답 비공개", (info.contains("김하늘") ? "김하늘 표시" : "환자 없음")
                + (info.contains("모범") ? ", 모범 기록 노출" : ", 모범 기록 없음"), info.contains("김하늘") && !info.contains("모범"));
        shot("04-patient");
        // S3 관찰: 빈 서술 → 안내, 정상 저장
        edt(() -> frame.show(Screen.OBSERVATION));
        boolean chart = call(() -> all(panel(Screen.OBSERVATION)).stream().anyMatch(c -> c instanceof app.ui.VitalChartPanel));
        edt(() -> {
            Object p = panel(Screen.OBSERVATION);
            set(p, "timeField", "09:25");
            ((Map<?, ? extends AbstractButton>) get(p, "shiftButtons")).values().iterator().next().doClick();
            ((List<? extends AbstractButton>) get(p, "tagBoxes")).get(0).doClick();
            set(p, "narrativeArea", "");
            clickText((Container) p, "관찰 기록 저장");
        });
        String obsErr = call(() -> ((JLabel) get(panel(Screen.OBSERVATION), "errorLabel")).getText());
        boolean noStudent = !call(() -> allText(panel(Screen.OBSERVATION))).contains("[학생]");
        row("S3", "빈 서술 저장 보류·안내", obsErr.replaceAll("<[^>]+>", " ").trim(), noStudent && !obsErr.isBlank() && !obsErr.contains("저장했습니다"));
        shot("05-observation-empty");
        edt(() -> {
            Object p = panel(Screen.OBSERVATION);
            set(p, "narrativeArea", "데이룸에서 서성이며 손톱을 물어뜯는 모습이 관찰됨. 환자는 \"누가 나를 감시하는 것 같아요\"라고 말함. 조용한 곳으로 이동해 1:1로 지지적 대화를 함. 09:30 PRN 처방에 따라 로라제팜 1mg PO 투여함. 10:00(투여 30분 후) 불안 정도 재평가 예정.");
            set(p, "interventionField", "조용한 곳으로 이동, 1:1 지지, 로라제팜 1mg PO 투여(09:30)");
            clickText((Container) p, "관찰 기록 저장");
        });
        String obs = call(() -> allText(panel(Screen.OBSERVATION)));
        row("S3", "정상 관찰 저장, 기존 기록과 구분, 활력징후 추이", (obs.contains("[학생]") ? "[학생] 기록 추가" : "추가 안 됨")
                + (chart ? ", 추이 차트 있음" : ", 차트 없음"), obs.contains("[학생]") && chart);
        shot("06-observation");
        // S4 투약: 약 정보, 거부 사유 누락(V6), PRN 사유 누락, 정상 저장
        edt(() -> frame.show(Screen.MEDICATION));
        Container mp = panel(Screen.MEDICATION);
        int orders = call(() -> ((JComboBox<?>) get(mp, "orderBox")).getItemCount());
        edt(() -> {
            ((JComboBox<?>) get(mp, "orderBox")).setSelectedIndex(0);
            set(mp, "timeField", "08:30");
            ((Map<?, ? extends AbstractButton>) get(mp, "resultButtons")).get(app.domain.MedicationResult.REFUSED).doClick();
            set(mp, "reasonField", "");
            set(mp, "basisField", "정규 처방");
            clickText((Container) mp, "투약 기록 저장");
        });
        String refusedErr = call(() -> ((JLabel) get(mp, "errorLabel")).getText()).replaceAll("<[^>]+>", " ").trim();
        row("V6", "거부 + 사유 누락 저장 보류", refusedErr, refusedErr.contains("사유") && !call(() -> allText(mp)).contains("[학생]"));
        shot("07-medication-refused");
        edt(() -> {
            JComboBox<?> box = (JComboBox<?>) get(mp, "orderBox");
            for (int i = 0; i < box.getItemCount(); i++) {
                if (String.valueOf(box.getItemAt(i)).contains("PRN")) box.setSelectedIndex(i);
            }
        });
        String drug = call(() -> ((JTextComponent) get(mp, "drugInfoArea")).getText());
        row("약 정보", "PRN 로라제팜 선택 시 식약처 허가정보 표시", drug.lines().findFirst().orElse(""),
                drug.contains("스리반정1밀리그램") && drug.contains("[용법용량]"));
        edt(() -> {
            set(mp, "doseField", "1");
            set(mp, "unitField", "mg");
            set(mp, "routeField", "PO");
            set(mp, "timeField", "09:30");
            ((Map<?, ? extends AbstractButton>) get(mp, "resultButtons")).get(app.domain.MedicationResult.GIVEN).doClick();
            set(mp, "reasonField", "");
            set(mp, "basisField", "PRN 처방(불안·초조 시 Lorazepam 1mg PO, 1일 최대 4mg)");
            clickText((Container) mp, "투약 기록 저장");
        });
        String prnErr = call(() -> ((JLabel) get(mp, "errorLabel")).getText()).replaceAll("<[^>]+>", " ").trim();
        row("S4", "PRN 사유 누락 저장 보류", prnErr, prnErr.contains("사유") && !call(() -> allText(mp)).contains("[학생]"));
        edt(() -> {
            set(mp, "reasonField", "불안·초조: 데이룸에서 서성임, 손톱 물어뜯기, \"누가 나를 감시하는 것 같아요\" 발언");
            ((AbstractButton) get(mp, "oralBox")).setSelected(true);
            set(mp, "planArea", "10:00 불안 VAS·RASS 재평가");
            clickText((Container) mp, "투약 기록 저장");
        });
        row("S4", "처방 목록에서만 선택(" + orders + "개), 사유 채우면 저장", call(() -> allText(mp)).contains("[학생]") ? "저장됨" : "저장 안 됨",
                orders == 3 && call(() -> allText(mp)).contains("[학생]"));
        shot("08-medication");
        // S5 SBAR: S만 → 안내·S 유지(V5), R 행 시각 누락(V7), 완성 제출
        edt(() -> frame.show(Screen.SBAR));
        Container sp = panel(Screen.SBAR);
        String sText = "09:25 데이룸에서 불안·초조(서성임, 손톱 물어뜯기, \"누가 나를 감시하는 것 같아요\" 발언)가 있어 1:1 지지 후 09:30 PRN 로라제팜 1mg PO 투여함.";
        edt(() -> {
            set(sp, "sArea", sText);
            clickText((Container) sp, "SBAR 제출");
        });
        String bErr = label(sp, "bErr");
        boolean sKept = call(() -> ((JTextComponent) get(sp, "sArea")).getText()).equals(sText);
        row("S5·V5", "B 누락 시 제출 보류·안내, S 유지", bErr + (sKept ? " / S 유지" : " / S 사라짐"),
                !bErr.isEmpty() && sKept && title().contains("SBAR"));
        shot("09-sbar-missing");
        edt(() -> {
            set(sp, "bArea", "28세 여성, 조현정동장애(양극형)로 동의입원 14일차. 현재 처방 아리피프라졸 10mg PO 1일 1회, 탄산리튬 300mg PO 1일 3회, PRN 로라제팜 1mg PO.");
            set(sp, "aArea", "10:00 재평가에서 불안 VAS 8→2, RASS 0, 자리에 앉아 휴식함.");
            set(sp, "timeField", "");
            set(sp, "interField", "불안·초조 재발 여부 관찰");
            clickText((Container) sp, "SBAR 제출");
        });
        String rErr = label(sp, "rErr");
        row("V7", "R 행 시각 누락 시 안내", rErr, !rErr.isEmpty() && title().contains("SBAR"));
        edt(() -> {
            set(sp, "timeField", "16:00");
            set(sp, "interField", "불안·초조 재발 여부 관찰");
            clickText((Container) sp, "행 추가");
            set(sp, "timeField", "18:30");
            set(sp, "interField", "탄산리튬 300mg PO 투약");
        });
        shot("10-sbar");
        edt(() -> clickText(sp, "SBAR 제출"));
        if (aiOn) shot("10b-sbar-submitting");
        waitSubmit();
        row("S5", "제출 성공 → 개인 메인", title(), title().contains("개인 메인") && historySize() == 1);
        String detail = detailOf(0);
        String first = detail.lines().findFirst().orElse("");
        row("S6", "모범 기록대로 입력하면 100점·누락/불일치 없음", first,
                first.contains("100점") && !detail.contains("■ 누락") && !detail.contains("■ 불일치"));
        row("S6", "제출 후 모범 기록 공개", detail.contains("[모범 기록]") ? "모범 기록 표시" : "없음", detail.contains("[모범 기록]"));
        shot("11-result-model");
        if (aiOn) {
            row("S7", "SBAR 제출 후 AI 해설 표시(점수 불변)", detail.contains("AI 해설 (점수에 반영되지 않음)") ? "해설 표시" : aiLine(detail),
                    detail.contains("AI 해설 (점수에 반영되지 않음)") && first.contains("100점"));
        } else {
            row("S7", "AI 키 없음 → 해설 없음 안내, 점수 유지", aiLine(detail), detail.contains("AI 해설: 없음"));
        }
        // 내보내기 한글
        Path exported = out.resolve("export-sample.txt");
        app.domain.Attempt sel = call(() -> (app.domain.Attempt) ((javax.swing.JList<?>) get(panel(Screen.HOME), "historyList")).getSelectedValue());
        String exportErr = new app.export.ResultExporter().export(sel, exported);
        String exportedText = exportErr == null ? Files.readString(exported, java.nio.charset.StandardCharsets.UTF_8) : "";
        row("내보내기", "UTF-8 파일에 한글이 깨지지 않음", exportErr != null ? exportErr
                : exportedText.contains("김하늘") || exportedText.contains("데이룸") ? "한글 정상(데이룸·로라제팜)" : "한글 확인 안 됨",
                exportErr == null && exportedText.contains("데이룸") && exportedText.contains("로라제팜"));
        // S6·V1~V4: 틀린 기록으로 두 번째 실습
        edt(() -> clickText(panel(Screen.HOME), "실습 시작"));
        edt(() -> frame.show(Screen.OBSERVATION));
        edt(() -> {
            Object p = panel(Screen.OBSERVATION);
            set(p, "timeField", "09:25");
            ((Map<?, ? extends AbstractButton>) get(p, "shiftButtons")).values().iterator().next().doClick();
            ((List<? extends AbstractButton>) get(p, "tagBoxes")).get(0).doClick();
            set(p, "narrativeArea", "환자가 불안해 보였으나 협조적임. 약 먹고 괜찮아짐.");
            set(p, "interventionField", "약 투여");
            clickText((Container) p, "관찰 기록 저장");
        });
        edt(() -> frame.show(Screen.MEDICATION));
        edt(() -> {
            JComboBox<?> box = (JComboBox<?>) get(mp, "orderBox");
            for (int i = 0; i < box.getItemCount(); i++) {
                if (String.valueOf(box.getItemAt(i)).contains("PRN")) box.setSelectedIndex(i);
            }
            set(mp, "doseField", "2");
            set(mp, "unitField", "mg");
            set(mp, "routeField", "PO");
            set(mp, "timeField", "09:30");
            ((Map<?, ? extends AbstractButton>) get(mp, "resultButtons")).get(app.domain.MedicationResult.GIVEN).doClick();
            set(mp, "reasonField", "불안");
            set(mp, "basisField", "PRN 처방");
            set(mp, "planArea", "");
            clickText((Container) mp, "투약 기록 저장");
        });
        edt(() -> frame.show(Screen.SBAR));
        edt(() -> {
            set(sp, "sArea", "불안해서 약 줌.");
            set(sp, "bArea", "조현정동장애 환자.");
            set(sp, "aArea", "괜찮아 보임.");
            set(sp, "timeField", "16:00");
            set(sp, "interField", "관찰");
            clickText((Container) sp, "SBAR 제출");
        });
        waitSubmit();
        String bad = detailOf(0);
        String badFirst = bad.lines().findFirst().orElse("");
        row("S6", "틀린 기록은 감점·항목별 지적", badFirst, historySize() == 2 && !badFirst.contains("100점"));
        row("V1", "판단 표현 '협조적' 지적(R1)", lineWith(bad, "[R1]", "협조적"), bad.contains("협조적") && bad.contains("[R1]"));
        row("V2", "환자 발언 인용 누락 지적(R1·R2)", lineWith(bad, "[R", "인용"), bad.contains("인용"));
        row("V3", "다른 용량 2mg → R7 불일치(입력·기준)", lineWith(bad, "[R7]", "입력 '2'"), bad.contains("[R7]") && bad.contains("입력 '2'"));
        row("V4", "재평가 계획 누락 → R4 지적", lineWith(bad, "[R4]", "재평가"), bad.contains("[R4]") && bad.contains("재평가 계획(언제"));
        shot("12-result-wrong");
        // S8 로그아웃 → 다른 사용자 → 재로그인
        logout();
        boolean cleared = title().contains("로그인") && call(() -> ((app.ui.AppState) get(frame, "state")).currentCase()) == null;
        row("S1", "로그아웃 후 이전 사용자 상태 제거", title() + (cleared ? ", 케이스 비움" : ", 케이스 남음"), cleared);
        signup("점검 사용자B", idB, pwB);
        login(idB, pwB);
        String homeB = call(() -> allText(panel(Screen.HOME)));
        row("S8", "다른 사용자에게 A의 기록 미표시", "B 기록 " + historySize() + "건", historySize() == 0 && !homeB.contains("데이룸"));
        shot("13-home-userB");
        logout();
        login(idA, pwA);
        int back = historySize();
        String again = detailOf(1);
        String metrics = call(() -> ((JTextComponent) get(panel(Screen.HOME), "metricsArea")).getText());
        row("S8", "재로그인 시 A의 기록·입력·평가지표 복원", "기록 " + back + "건, " + metrics.lines().filter(l -> l.startsWith("총")).findFirst().orElse(""),
                back == 2 && again.contains("100점") && again.contains("데이룸") && metrics.contains("총 실습 횟수: 2회"));
        shot("14-home-relogin");
        if (aiOn) runAi(detail);
        else {
            String home = call(() -> allText(panel(Screen.HOME)));
            row("S9", "AI 키 없음 → 생성 버튼 꺼짐·이유 표시", home.contains("AI 키가 설정되지 않아") ? "이유 표시" : "안내 없음",
                    home.contains("AI 키가 설정되지 않아"));
        }
    }

    private static String aiLine(String detail) {
        return detail.lines().filter(l -> l.startsWith("AI 해설")).findFirst().orElse("AI 해설 줄 없음");
    }

    /** 상세 글에서 두 조각이 모두 든 첫 줄. */
    private static String lineWith(String text, String a, String b) {
        return text.lines().filter(l -> l.contains(a) && l.contains(b)).findFirst().map(String::trim)
                .map(l -> l.length() > 90 ? l.substring(0, 90) + "…" : l).orElse("해당 지적 없음");
    }

    /** AI 해설이 결과에 나오는지, AI 생성 케이스로 실습·채점이 되는지 확인한다. */
    private static void runAi(String pc01Detail) throws Exception {
        edt(() -> clickText(panel(Screen.HOME), "AI로 새 케이스 생성"));
        shot("15-ai-generating");
        for (int i = 0; i < 300 && !call(() -> frame.getTitle()).contains("환자 정보"); i++) Thread.sleep(1000);
        app.ui.AppState st = (app.ui.AppState) get(frame, "state");
        app.domain.PatientCase c = call(st::currentCase);
        row("S9", "AI 생성 케이스로 실습 시작(허용 약물 검사 통과)", c == null ? "없음" : c.id() + ", " + c.source(),
                c != null && c.source() == app.domain.CaseSource.AI_GENERATED);
        row("S9", "상단에 'AI 생성 · 임상 검수 전' 표시", call(() -> ((JLabel) get(frame, "noticeLabel")).getText()),
                call(() -> ((JLabel) get(frame, "noticeLabel")).getText()).contains("AI 생성"));
        shot("16-ai-patient");
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
        row("S9", "AI 생성 케이스 기록 저장·채점(R7·R2)", first, first.contains(c.id()) && first.contains("점")
                && d.contains("R7 ") && d.contains("R2 "));
        shot("17-ai-result");
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

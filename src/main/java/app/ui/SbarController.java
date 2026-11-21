package app.ui;

import app.domain.Attempt;
import app.domain.CheckResult;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import app.storage.AttemptRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * SBAR 인계 화면의 로직(Swing 없음). S·B·A 텍스트와 R(시각·중재) 행의 필수값을 검증하고,
 * 통과하면 이번 실습 전체(관찰·투약·SBAR)를 저장한 뒤 개인 메인으로 이동한다 (S5, V5, V7).
 * 점수·점검 결과는 후속 작업(R 점검) 전이라 비워 둔다.
 */
public class SbarController {
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final AppState state;
    private final AttemptRepository attempts;
    private final LongSupplier userId;

    /** @param userId 로그인한 사용자 번호를 돌려주는 함수 (제출 시점에 호출) */
    public SbarController(AppState state, AttemptRepository attempts, LongSupplier userId) {
        this.state = state;
        this.attempts = attempts;
        this.userId = userId;
    }

    /** 제출 결과. 성공이면 errors가 비어 있고 attempt에 저장된 기록이 들어 있다. */
    public record SubmitResult(boolean submitted, Map<String, String> errors, Attempt attempt, String message) {}

    /** 검증 오류를 칸 이름(situation·background·assessment·rec)별로 모은다. 행 오류는 "rec1.time"처럼 번호를 붙인다. */
    public static Map<String, String> validate(String s, String b, String a, List<SbarRecommendation> rows) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (blank(s)) errors.put("situation", "S(상황)를 입력하세요.");
        if (blank(b)) errors.put("background", "B(배경)를 입력하세요.");
        if (blank(a)) errors.put("assessment", "A(평가)를 입력하세요.");
        if (rows == null || rows.isEmpty()) {
            errors.put("rec", "R(권고)에 시각과 중재를 한 줄 이상 입력하세요.");
        } else {
            for (int i = 0; i < rows.size(); i++) {
                SbarRecommendation r = rows.get(i);
                int n = i + 1;
                String t = r.time() == null ? "" : r.time().trim();
                if (t.isEmpty()) errors.put("rec" + n + ".time", "R " + n + "행: 시각을 입력하세요. (예: 14:30)");
                else if (!TIME.matcher(t).matches()) errors.put("rec" + n + ".time", "R " + n + "행: 시각은 HH:mm 형식으로 입력하세요.");
                if (blank(r.intervention())) errors.put("rec" + n + ".intervention", "R " + n + "행: 중재를 입력하세요.");
            }
        }
        return errors;
    }

    /**
     * 제출한다. 검증 실패면 저장하지 않고 오류만 돌려주며 입력은 화면에 남는다.
     * 성공하면 사용자별로 저장하고 개인 메인으로 이동한다. 입력값은 그대로 보존한다.
     */
    public SubmitResult submit(String s, String b, String a, List<SbarRecommendation> rows) {
        Map<String, String> errors = validate(s, b, a, rows);
        if (!errors.isEmpty()) {
            return new SubmitResult(false, errors, null, "비어 있는 항목을 확인하세요. 입력한 내용은 유지됩니다.");
        }
        if (state.currentCase() == null) {
            return new SubmitResult(false, Map.of(), null, "실습 중인 케이스가 없습니다.");
        }
        List<SbarRecommendation> kept = new ArrayList<>();
        for (SbarRecommendation r : rows) kept.add(new SbarRecommendation(r.time().trim(), r.intervention()));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime started = state.practiceStartedAt() == null ? now : state.practiceStartedAt();
        Attempt attempt = new Attempt(null, userId.getAsLong(), state.currentCase().id(), state.currentCase().source(),
                null, started, now, state.studentObservations(), state.studentMedications(),
                new SbarReport(s, b, a, kept), List.<CheckResult>of(), null, null);
        Attempt saved;
        try {
            saved = attempts.save(attempt);
        } catch (RuntimeException e) {
            return new SubmitResult(false, Map.of(), null, "저장하지 못했습니다. 입력은 유지됩니다. (" + e.getMessage() + ")");
        }
        state.go(Screen.HOME);
        return new SubmitResult(true, Map.of(), saved, "제출했습니다.");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

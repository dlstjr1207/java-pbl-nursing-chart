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
 * 통과하면 이번 실습 전체(관찰·투약·SBAR)를 저장한 뒤 결과 화면으로 이동한다 (S5, V5, V7).
 * 제출 당시 규칙 점검 결과와 점수(D30)를 함께 저장한다. AI 해설은 점수에 반영하지 않는다.
 */
public class SbarController {
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final AppState state;
    private final AttemptRepository attempts;
    private final LongSupplier userId;
    /** AI 해설 클라이언트 (없으면 해설 없이 규칙 점검만). 해설은 점수에 반영하지 않는다. */
    private final app.ai.AiClient ai;

    /** @param userId 로그인한 사용자 번호를 돌려주는 함수 (제출 시점에 호출) */
    public SbarController(AppState state, AttemptRepository attempts, LongSupplier userId) {
        this(state, attempts, userId, null);
    }

    /** AI 해설을 함께 저장하는 생성자. ai가 null이거나 키가 없으면 해설은 "없음"이다. */
    public SbarController(AppState state, AttemptRepository attempts, LongSupplier userId, app.ai.AiClient ai) {
        this.state = state;
        this.attempts = attempts;
        this.userId = userId;
        this.ai = ai;
    }

    /** 제출 당시 입력과 점검 결과를 요약해 AI 해설을 받는다. 실패해도 점검 결과는 유지된다 (S7). */
    private app.domain.AiFeedback explain(SbarReport report, List<CheckResult> checks) {
        if (ai == null) return new app.domain.AiFeedback(app.ai.AiClient.STATUS_NONE, null, null, null);
        StringBuilder p = new StringBuilder("학생의 SBAR 인계 기록과 규칙 점검 결과입니다. 점수 없이 해설만 해 주세요.\n");
        p.append("S: ").append(report.situation()).append("\nB: ").append(report.background())
                .append("\nA: ").append(report.assessment()).append('\n');
        for (SbarRecommendation r : report.recommendations()) {
            p.append("R: ").append(r.time()).append(' ').append(r.intervention()).append('\n');
        }
        // 요청을 짧게: 점수와 고칠 점(누락·불일치·피드백)만 보낸다. 통과 항목은 개수만.
        long passed = checks.stream().filter(c -> c.status() == app.domain.CheckStatus.PASS).count();
        p.append("점수: ").append(app.scoring.Scorer.score(checks).score()).append(", 통과 ").append(passed).append("건\n");
        for (CheckResult c : checks) {
            if (c.status() == app.domain.CheckStatus.PASS || c.status() == app.domain.CheckStatus.NOT_SCORED) continue;
            p.append('[').append(c.ruleId()).append("] ").append(c.status()).append(' ').append(c.message()).append('\n');
        }
        return ai.explain(p.toString());
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
     * 제출 준비(화면 스레드에서 빠르게 실행). 필수값을 검증하고, 통과하면 제출 당시 입력과 규칙 점검 결과를
     * 묶어 둔다. AI 호출과 저장은 complete()가 백그라운드에서 한다.
     */
    public Prepared prepare(String s, String b, String a, List<SbarRecommendation> rows) {
        Map<String, String> errors = validate(s, b, a, rows);
        if (!errors.isEmpty()) {
            return Prepared.failed(errors, "비어 있는 항목을 확인하세요. 입력한 내용은 유지됩니다.");
        }
        if (state.currentCase() == null) {
            return Prepared.failed(Map.of(), "실습 중인 케이스가 없습니다.");
        }
        List<SbarRecommendation> kept = new ArrayList<>();
        for (SbarRecommendation r : rows) kept.add(new SbarRecommendation(r.time().trim(), r.intervention()));
        SbarReport report = new SbarReport(s, b, a, kept);
        // 제출 당시 규칙 점검(R1·R2·R4·R6·R7·R8·R9)과 점수(D30)를 함께 저장한다.
        app.scoring.Scorer.Result scored = app.scoring.Scorer.evaluate(state.currentCase(),
                state.studentObservations(), state.studentMedications(), report);
        List<CheckResult> checks = scored.checks();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime started = state.practiceStartedAt() == null ? now : state.practiceStartedAt();
        app.domain.PatientCase c = state.currentCase();
        Attempt draft = new Attempt(null, userId.getAsLong(), c.id(), c.source(),
                c.source() == app.domain.CaseSource.AI_GENERATED ? app.domain.DomainJson.toJson(c) : null,
                started, now, List.copyOf(state.studentObservations()), List.copyOf(state.studentMedications()),
                report, checks, scored.score(), null);
        return new Prepared(Map.of(), null, draft);
    }

    /**
     * AI 해설을 받고 저장한다(백그라운드 스레드에서 실행 가능, 화면 상태는 바꾸지 않음).
     * AI가 실패하거나 예외가 나도 해설 상태만 FAILED가 되고 저장·점검 결과는 그대로 남는다 (S7).
     */
    public SubmitResult complete(Prepared prepared) {
        Attempt draft = prepared.draft();
        app.domain.AiFeedback feedback;
        try {
            feedback = explain(draft.sbar(), draft.checks());
        } catch (RuntimeException e) {
            feedback = new app.domain.AiFeedback(app.ai.AiClient.STATUS_FAILED, null, null, null);
        }
        Attempt attempt = new Attempt(null, draft.userId(), draft.caseId(), draft.caseSource(), draft.generatedCaseJson(),
                draft.startedAt(), draft.submittedAt(), draft.observationEntries(), draft.medicationRecords(),
                draft.sbar(), draft.checks(), draft.score(), feedback);
        try {
            return new SubmitResult(true, Map.of(), attempts.save(attempt), "제출했습니다.");
        } catch (RuntimeException e) {
            return new SubmitResult(false, Map.of(), null, "저장하지 못했습니다. 입력은 유지됩니다. (" + e.getMessage() + ")");
        }
    }

    /** 제출을 마치고 방금 제출한 실습의 결과 화면으로 간다 (polish B-3). */
    public void finish(Attempt submitted) { state.showResult(submitted); }

    /** AI 해설을 요청할 수 있는지(키가 있는지). 화면 안내 문구용. */
    public boolean aiEnabled() { return ai != null && ai.isEnabled(); }

    /** 지금 실습을 구분하는 값(실습 시작 시각). 실습이 바뀌면 SBAR 입력칸을 비우는 데 쓴다. */
    public Object practiceKey() { return state.practiceStartedAt(); }

    /**
     * 한 번에 제출한다(테스트·동기 실행용). 검증 실패면 저장하지 않고 오류만 돌려주며 입력은 화면에 남는다.
     * 성공하면 사용자별로 저장하고 결과 화면으로 이동한다.
     */
    public SubmitResult submit(String s, String b, String a, List<SbarRecommendation> rows) {
        Prepared p = prepare(s, b, a, rows);
        if (!p.ok()) return new SubmitResult(false, p.errors(), null, p.message());
        SubmitResult r = complete(p);
        if (r.submitted()) finish(r.attempt());
        return r;
    }

    /** 제출 준비 결과. ok()면 draft에 저장할 기록(AI 해설 전)이 들어 있다. */
    public record Prepared(Map<String, String> errors, String message, Attempt draft) {
        static Prepared failed(Map<String, String> errors, String message) {
            return new Prepared(errors, message, null);
        }

        public boolean ok() { return draft != null; }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

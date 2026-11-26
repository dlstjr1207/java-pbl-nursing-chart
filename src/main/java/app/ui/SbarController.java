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
    private app.domain.AiFeedback explain(app.domain.PatientCase c, Attempt draft, SbarReport report, List<CheckResult> checks) {
        if (ai == null) return new app.domain.AiFeedback(app.ai.AiClient.STATUS_NONE, null, null, null);
        return ai.explain(explainPrompt(c, draft, report, checks));
    }

    /**
     * AI 해설 요청 글. 케이스의 실제 사실(브리핑)과 학생의 관찰 기록을 함께 보내, 예시 문장이 실제 발언만 인용하게 한다.
     * 정답 키·모범 기록은 보내지 않는다.
     */
    static String explainPrompt(app.domain.PatientCase pc, Attempt draft, SbarReport report, List<CheckResult> checks) {
        StringBuilder p = new StringBuilder("학생의 SBAR 인계 기록과 규칙 점검 결과입니다. 점수 없이 해설만 해 주세요.\n");
        p.append(app.ai.AiClient.NO_FABRICATION).append('\n');
        if (pc != null && pc.briefing() != null) {
            p.append("[케이스 사실(이 안의 발언·수치·사건만 사실로 쓸 것)]\n").append(pc.briefing()).append('\n');
        }
        // 학생은 원자료 메모를 보고 기록한다. 메모의 발언·행동도 사실이다(브리핑에 없는 메모를 "지어냈다"고 지적하지 않게).
        // 관련 없는 메모는 따로 표시해, 학생이 그 내용을 기록에 넣었으면 해설에서 짚을 수 있게 한다.
        if (pc != null && pc.hasRawNotes()) {
            for (app.domain.RawNote n : pc.rawNotes()) {
                if (n == null) continue;
                p.append(n.irrelevant() ? "[원자료 메모·이번 사건과 관련 없음] " : "[원자료 메모] ")
                        .append(n.time()).append(' ').append(n.text()).append('\n');
            }
        }
        // 환자 기본 정보·알레르기·처방도 사실이다. 빠지면 학생이 B에 쓴 진단·약을 "케이스에 없다"고 잘못 지적한다.
        if (pc != null) {
            for (String line : caseFactLines(pc)) p.append(line).append('\n');
        }
        // 활력징후와 케이스 관찰 로그(학생 화면에는 숨김, 같은 내용이 원자료 메모에 있음)도 사실이다. 빠지면 AI가 실제 수치를 "지어냈다"고 잘못 지적한다.
        if (pc != null) {
            for (app.domain.VitalSign v : pc.vitals()) {
                p.append("[케이스 활력징후] ").append(v.time()).append(' ').append(vitalText(v)).append('\n');
            }
            for (app.domain.ObservationEntry o : pc.observationLog()) {
                p.append("[케이스 관찰 로그] ").append(o.time()).append(' ').append(o.narrative()).append('\n');
            }
        }
        if (draft != null) {
            for (app.domain.ObservationEntry o : draft.observationEntries()) {
                p.append("[학생 관찰 기록] ").append(o.time()).append(' ').append(o.narrative()).append('\n');
            }
        }
        if (draft != null) {
            for (app.domain.MedicationRecord m : draft.medicationRecords()) {
                p.append("[학생 투약 기록] ").append(m.administeredTime()).append(' ').append(m.drugName()).append(' ')
                        .append(m.dose()).append(m.unit() == null ? "" : m.unit()).append(' ').append(m.route())
                        .append(m.reassessmentPlan() == null || m.reassessmentPlan().isBlank() ? ", 재평가 계획 없음"
                                : ", 재평가 계획: " + m.reassessmentPlan()).append('\n');
            }
        }
        p.append("[학생 SBAR] (R은 인계를 받은 다음 근무자가 앞으로 할 일과 그 예정 시각이다. 학생이 쓴 R의 시각이 인계 시각보다 "
                + "뒤인 것은 올바르므로 지적하지 말 것. 예시 문장의 R에는 학생이 쓴 R의 시각이나 그보다 뒤의 처방 시각만 쓸 것. "
                + "학생이 쓰지 않은 내용을 학생의 실수로 지적하지 말 것)\n");
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
        return p.toString();
    }

    /** 해설 요청에 보내는 환자 기본 정보·알레르기·처방 줄 (학생이 환자 정보 화면에서 보는 내용, 값이 없는 칸은 뺌). */
    static List<String> caseFactLines(app.domain.PatientCase pc) {
        List<String> out = new ArrayList<>();
        app.domain.Patient pt = pc.patient();
        if (pt != null) {
            List<String> parts = new ArrayList<>();
            if (pt.age() != null) parts.add(pt.age() + "세");
            if (pt.sex() != null) parts.add("F".equals(pt.sex()) ? "여성" : "M".equals(pt.sex()) ? "남성" : pt.sex());
            if (shown(pt.diagnosis())) parts.add("진단 " + StudentText.value(pt.diagnosis()));
            if (shown(pt.admissionType())) parts.add(StudentText.value(pt.admissionType()));
            if (pt.hospitalDay() != null) parts.add("입원 " + pt.hospitalDay() + "일차");
            if (!parts.isEmpty()) out.add("[케이스 환자] " + String.join(", ", parts));
        }
        app.domain.Adr adr = pc.adr();
        out.add("[케이스 알레르기] " + shownList(adr == null ? List.of() : adr.allergies())
                + " / 약물 부작용 이력: " + shownList(adr == null ? List.of() : adr.adverseReactions()));
        for (app.domain.Medication m : pc.orders()) {
            String kind = switch (m.type()) {
                case PRN -> "PRN";
                case PAST -> "과거";
                default -> "정규";
            };
            out.add("[케이스 처방 " + kind + "] " + MedicationController.orderSummary(m));
        }
        return out;
    }

    /** 학생 화면에 보이는 값인지 (빈 값·개발용 표기만 있는 값은 보내지 않는다). */
    private static boolean shown(String v) {
        return v != null && !StudentText.value(v).equals(PatientInfoView.EMPTY);
    }

    /** 목록을 학생 화면에 보이는 글로 (개발용 표기 제거). 비면 "없음". */
    private static String shownList(List<String> list) {
        List<String> shown = list.stream().filter(SbarController::shown).map(StudentText::value).toList();
        return shown.isEmpty() ? "없음" : String.join(", ", shown);
    }

    /** 활력징후 한 줄. 값이 없는 항목은 빼고, 모두 없으면 "기록 없음" (빈 값을 "null"로 보내 AI가 오해하지 않게). */
    static String vitalText(app.domain.VitalSign v) {
        List<String> parts = new ArrayList<>();
        if (v.systolic() != null && v.diastolic() != null) parts.add("BP " + v.systolic() + "/" + v.diastolic());
        else if (v.systolic() != null) parts.add("수축기 " + v.systolic());
        if (v.pulse() != null) parts.add("HR " + v.pulse());
        if (v.respiration() != null) parts.add("RR " + v.respiration());
        if (v.temperature() != null) parts.add("BT " + v.temperature());
        if (v.spo2() != null) parts.add("SpO2 " + v.spo2());
        return parts.isEmpty() ? "기록 없음" : String.join(", ", parts);
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
        if (state.isReviewing()) return Prepared.failed(Map.of(), AppState.REVIEW_LOCKED); // 제출 후 고쳐 점수를 바꿀 수 없다
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
        // 제출 당시 규칙 점검(R1·R2·R4·R6~R10)과 점수(D33)를 함께 저장한다.
        app.scoring.Scorer.Result scored = app.scoring.Scorer.evaluate(state.currentCase(),
                state.studentObservations(), state.studentMedications(), report);
        List<CheckResult> checks = new ArrayList<>(scored.checks());
        // 정신상태 평가는 점수에 넣지 않고 피드백만 준다 (D38). 점수는 위에서 이미 계산했다
        checks.addAll(app.scoring.MentalStatusChecker.check(state.mentalStatus()));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime started = state.practiceStartedAt() == null ? now : state.practiceStartedAt();
        app.domain.PatientCase c = state.currentCase();
        Attempt draft = new Attempt(null, userId.getAsLong(), c.id(), c.source(),
                c.source() == app.domain.CaseSource.AI_GENERATED ? app.domain.DomainJson.toJson(c) : null,
                started, now, List.copyOf(state.studentObservations()), List.copyOf(state.studentMedications()),
                report, checks, scored.score(), null, state.mentalStatus());
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
            feedback = explain(ResultView.caseOf(draft), draft, draft.sbar(), draft.checks());
        } catch (RuntimeException e) {
            feedback = new app.domain.AiFeedback(app.ai.AiClient.STATUS_FAILED, null, null, null);
        }
        Attempt attempt = new Attempt(null, draft.userId(), draft.caseId(), draft.caseSource(), draft.generatedCaseJson(),
                draft.startedAt(), draft.submittedAt(), draft.observationEntries(), draft.medicationRecords(),
                draft.sbar(), draft.checks(), draft.score(), feedback, draft.mentalStatus());
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

    /** 기록 서명 (로그인한 사용자 이름, D38) */
    public String signature() {
        return state.signature();
    }

    /** 제출한 기록 보기 중이면 그 기록의 SBAR, 아니면 null (SBAR 화면을 읽기 전용으로 채울 때 쓴다). */
    public SbarReport reviewedSbar() {
        return state.isReviewing() && state.resultAttempt() != null ? state.resultAttempt().sbar() : null;
    }

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

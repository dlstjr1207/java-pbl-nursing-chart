package app.ui;

import app.domain.AiFeedback;
import app.domain.Attempt;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.MedicationRecord;
import app.domain.ObservationEntry;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import app.storage.AttemptRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 평가지표·과거 기록·결과 패널의 로직(Swing 없음, S6~S8).
 * 저장된 Attempt에서 화면에 보일 문장을 만든다. 점수는 제출 당시 계산한 값(D30)이며, 없으면 "미산출"이다.
 * 모범 기록은 제출한 기록의 상세에서만 보여 준다(제출 전에는 보이지 않음). 다른 사용자의 기록은 조회하지 않는다.
 */
public class ResultView {
    public static final String NOT_SCORED = "미산출";
    public static final String MODEL_PENDING = "모범 기록: 준비 중 (임상 검토 전)";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AttemptRepository attempts;
    private final long userId;

    public ResultView(AttemptRepository attempts, long userId) {
        this.attempts = attempts;
        this.userId = userId;
    }

    /** 사용자 본인의 기록(최신순). */
    public List<Attempt> history() { return attempts.findByUser(userId); }

    /** 평가지표: 총 실습 횟수, 최근 점수, 항목별 통과 수, 최근 5회 요약. 점수가 없으면 미산출. */
    public List<String> metrics() {
        List<Attempt> all = history();
        List<String> out = new ArrayList<>();
        out.add("총 실습 횟수: " + all.size() + "회");
        out.add("최근 점수: " + (all.isEmpty() ? "기록 없음" : scoreText(all.get(0))));
        Map<String, int[]> perRule = new LinkedHashMap<>(); // 규칙 ID → {통과, 전체(미산출 제외)}
        for (Attempt a : all) {
            for (CheckResult c : a.checks()) {
                if (c.status() == CheckStatus.NOT_SCORED) continue;
                // 지금 배점(D33)에 있는 항목만 보인다. R8(필수 입력)은 점수에 넣지 않으므로 숨긴다 (D38)
                if (!app.scoring.Scorer.WEIGHTS.containsKey(c.ruleId())) continue;
                int[] v = perRule.computeIfAbsent(c.ruleId(), k -> new int[2]);
                if (c.status() == CheckStatus.PASS) v[0]++;
                v[1]++;
            }
        }
        if (perRule.isEmpty()) out.add("항목별 결과: 기록 없음");
        else perRule.forEach((k, v) -> out.add("항목별 " + k + ": " + v[0] + "/" + v[1] + " 통과"));
        int n = Math.min(5, all.size());
        List<String> recent = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) recent.add(scoreText(all.get(i)));
        out.add("최근 " + n + "회 점수(오래된 순): " + (n == 0 ? "기록 없음" : String.join(" → ", recent)));
        return out;
    }

    /** 과거 기록 목록의 한 줄 (제출 시각·케이스·점수). */
    public static String historyLine(Attempt a) {
        String when = a.submittedAt() == null ? "미제출" : a.submittedAt().format(FMT);
        return when + " · " + a.caseId() + " · 점수 " + scoreText(a);
    }

    /** 점수 문구. 값이 없으면 미산출. */
    public static String scoreText(Attempt a) {
        if (a.score() == null) return NOT_SCORED;
        double v = a.score();
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v)) + "점";
    }

    /** 기록 1건의 상세. 본인의 기록이 아니면 빈 값. */
    public Optional<String> detail(long attemptId) {
        return attempts.findById(userId, attemptId).map(ResultView::detailText);
    }

    /** 제출 당시 입력과 점검 결과를 그대로 재현하는 상세 문장 (서명은 "학생"). */
    public static String detailText(Attempt a) {
        return detailText(a, "학생");
    }

    /** 제출 당시 입력과 점검 결과를 그대로 재현하는 상세 문장. signer는 기록 서명(로그인한 사용자 이름). */
    public static String detailText(Attempt a, String signer) {
        StringBuilder sb = new StringBuilder();
        sb.append(historyLine(a)).append("\n\n");
        sb.append(ruleScoreText(a.checks()));
        sb.append("[점검 결과]\n");
        sb.append(checksText(a.checks())).append('\n');
        sb.append(submittedText(a, signer));
        sb.append('\n').append(modelAnswerText(a));
        sb.append(aiText(a.aiFeedback()));
        return sb.toString();
    }

    /** 실제 인계 기록 형태 안내 (D38, B5) */
    public static final String HANDOFF_NOTICE = "실제 인계 기록은 칸을 나누지 않고 이 순서로 한 문단으로 씁니다.";

    /** SBAR를 S→B→A→R 순서로 이어 붙인 한 문단 (D38, B5). 채점과 무관한 보기용. */
    public static String handoffParagraph(SbarReport r) {
        if (r == null) return "";
        List<String> parts = new ArrayList<>();
        for (String s : new String[] {r.situation(), r.background(), r.assessment()}) {
            if (s != null && !s.isBlank()) parts.add(s.strip().replaceAll("\\s*\\R\\s*", " "));
        }
        List<String> recs = new ArrayList<>();
        for (SbarRecommendation x : r.recommendations()) {
            String t = ((x.time() == null ? "" : x.time().strip()) + " " + (x.intervention() == null ? "" : x.intervention().strip())).strip();
            if (!t.isEmpty()) recs.add(t);
        }
        if (!recs.isEmpty()) parts.add(String.join(", ", recs));
        return String.join(" ", parts);
    }

    /** 제출 당시 학생이 쓴 관찰·투약·SBAR (서명은 "학생"). */
    public static String submittedText(Attempt a) {
        return submittedText(a, "학생");
    }

    /**
     * 제출 당시 학생이 쓴 관찰·투약·SBAR을 그대로 재현한다 (결과 화면 "내가 제출한 기록", S8).
     * 관찰은 "날짜·시간 | 내용 | 서명", 투약은 5원칙 체크와 서명, SBAR는 서명과 실제 인계 기록 형태(한 문단)를 붙인다 (D38).
     */
    public static String submittedText(Attempt a, String signer) {
        String sign = signer == null || signer.isBlank() ? "학생" : signer;
        app.domain.PatientCase pc = caseOf(a);
        StringBuilder sb = new StringBuilder();
        sb.append("[관찰 기록] 날짜·시간 | 내용 | 서명\n");
        int i = 1;
        for (ObservationEntry o : a.observationEntries()) {
            sb.append(i++).append(". ").append(ObservationController.dateTime(pc, o.time())).append(" | ").append(o.tags()).append(' ')
                    .append(o.narrative()).append(" | ").append(sign).append('\n');
        }
        if (a.observationEntries().isEmpty()) sb.append("(없음)\n");
        sb.append("\n[정신상태 평가] (배점 없음)\n");
        if (app.domain.MentalStatus.blank(a.mentalStatus())) sb.append("(없음)\n");
        else a.mentalStatus().lines().forEach(l -> sb.append(l).append('\n'));
        sb.append("\n[투약 기록]\n");
        i = 1;
        for (MedicationRecord m : a.medicationRecords()) {
            sb.append(i++).append(". ").append(m.drugName()).append(' ').append(m.dose()).append(' ')
                    .append(m.route()).append(' ').append(m.administeredTime()).append(' ')
                    .append(m.result() == null ? "" : MedicationController.resultLabel(m.result()))
                    .append(" | ").append(app.domain.FiveRights.text(m.rights())).append(" | 서명 ").append(sign).append('\n');
        }
        if (a.medicationRecords().isEmpty()) sb.append("(없음)\n");
        sb.append("\n[SBAR]\n");
        SbarReport r = a.sbar();
        if (r == null) {
            sb.append("(없음)\n");
        } else {
            sb.append("S: ").append(r.situation()).append("\nB: ").append(r.background())
                    .append("\nA: ").append(r.assessment()).append('\n');
            for (SbarRecommendation x : r.recommendations()) {
                sb.append("R: ").append(x.time()).append(' ').append(x.intervention()).append('\n');
            }
            sb.append("서명: ").append(sign).append('\n');
            sb.append("\n[실제 인계 기록 형태]\n").append(handoffParagraph(r)).append(" (서명: ").append(sign).append(")\n")
                    .append("※ ").append(HANDOFF_NOTICE).append('\n');
        }
        return sb.toString();
    }

    /**
     * 결과 화면의 묶음 (polish B-3). 고칠 점(누락·불일치)이 맨 위, 그다음 피드백(R1 판단 표현·미산출),
     * 마지막에 잘 작성한 내용. R9·R10도 배점이 있으므로(D33) 누락·불일치는 고칠 점으로 보낸다.
     */
    public record Groups(List<CheckResult> missing, List<CheckResult> mismatch, List<CheckResult> feedback,
                         List<CheckResult> passed) {
    }

    public static Groups groups(List<CheckResult> checks) {
        List<CheckResult> missing = new ArrayList<>();
        List<CheckResult> mismatch = new ArrayList<>();
        List<CheckResult> feedback = new ArrayList<>();
        List<CheckResult> passed = new ArrayList<>();
        // D30 기록의 R9는 배점이 없던 때라 누락이어도 피드백으로 둔다 (저장 당시 기준)
        boolean d30 = app.scoring.Scorer.scoredUnderD30(checks);
        for (CheckResult c : checks == null ? List.<CheckResult>of() : checks) {
            boolean feedbackOnly = d30 && "R9".equals(c.ruleId());
            switch (c.status()) {
                case PASS -> passed.add(c);
                case MISSING -> (feedbackOnly ? feedback : missing).add(c);
                case MISMATCH -> (feedbackOnly ? feedback : mismatch).add(c);
                default -> feedback.add(c); // FEEDBACK, NOT_SCORED
            }
        }
        return new Groups(missing, mismatch, feedback, passed);
    }

    /** 점검 결과를 고칠 점·피드백·잘한 점 순서로, 위치는 사람 말로 적는다 (내보내기 파일과 같은 내용). */
    public static String checksText(List<CheckResult> checks) {
        if (checks == null || checks.isEmpty()) return "(점검 결과 없음)\n";
        Groups g = groups(checks);
        StringBuilder sb = new StringBuilder();
        section(sb, "누락", g.missing());
        section(sb, "불일치", g.mismatch());
        section(sb, "피드백 (배점 없음)", g.feedback());
        section(sb, "잘 작성한 내용", g.passed());
        return sb.toString();
    }

    /** 묶음 한 개의 줄들: "- 관찰 기록 · 장소 — 이유 (R2 핵심 사실)" */
    public static String lines(List<CheckResult> list) {
        StringBuilder sb = new StringBuilder();
        for (CheckResult c : list) sb.append("- ").append(CheckLabels.line(c)).append('\n');
        return sb.toString();
    }

    /** 규칙별 점수 요약 (D33 배점). 제출 당시 점검 결과로 다시 계산해 보여 준다. */
    public static String ruleScoreText(List<CheckResult> checks) {
        if (checks == null || checks.isEmpty()) return "";
        var r = app.scoring.Scorer.score(checks);
        if (r.ruleScores().isEmpty()) return "[항목별 점수] 미산출\n\n";
        StringBuilder sb = new StringBuilder("[항목별 점수]\n");
        var weights = app.scoring.Scorer.weightsFor(checks);
        r.ruleScores().forEach((rule, v) -> sb.append("  ").append(rule).append(' ')
                .append(v).append(" / ").append(weights.get(rule)).append('\n'));
        if (app.scoring.Scorer.scoredUnderD30(checks)) {
            sb.append("  ").append(app.scoring.Scorer.D30_NOTICE).append(" (R8 20점 포함, R9·R10 피드백만)\n\n");
        } else {
            sb.append("  R8 필수 입력은 점수에 넣지 않음 (빈칸은 화면에서 막음)\n\n");
        }
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, List<CheckResult> list) {
        if (list.isEmpty()) return;
        sb.append("■ ").append(title).append(" (").append(list.size()).append(")\n");
        for (CheckResult c : list) sb.append("  - ").append(CheckLabels.line(c)).append('\n');
    }

    /** 기록의 케이스. AI 생성 케이스는 저장된 원문에서, 고정 케이스는 케이스 파일에서 읽는다. 못 읽으면 null. */
    public static app.domain.PatientCase caseOf(Attempt a) {
        if (a == null) return null;
        try {
            if (a.generatedCaseJson() != null) {
                return app.domain.DomainJson.fromJson(a.generatedCaseJson(), app.domain.PatientCase.class);
            }
            if (a.caseId() != null) return new app.cases.CaseLoader().load(a.caseId());
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /**
     * 제출한 기록의 케이스에 있는 모범 기록. 제출 후에만 호출되므로 정답이 제출 전에 보이지 않는다.
     * AI 생성 케이스는 저장된 원문에서, 고정 케이스는 케이스 파일에서 읽는다. 없으면 "준비 중".
     */
    public static String modelAnswerText(Attempt a) {
        app.domain.PatientCase c = caseOf(a);
        app.domain.ModelAnswer m = c == null ? null : c.modelAnswer();
        if (m == null || (m.observation() == null && m.medication() == null && m.sbar() == null)) {
            return MODEL_PENDING + "\n";
        }
        StringBuilder sb = new StringBuilder("[모범 기록] (제출 후 공개, 참고용)\n");
        if (m.observation() != null) sb.append("관찰: ").append(m.observation()).append('\n');
        if (m.medication() != null) sb.append("투약: ").append(m.medication()).append('\n');
        if (m.sbar() != null) sb.append("SBAR:\n").append(m.sbar()).append('\n');
        return sb.toString();
    }

    /** AI 해설 상태 문구. 없거나 실패해도 점검 결과는 그대로 유지된다. */
    public static String aiText(AiFeedback f) {
        if (f == null || "NONE".equals(f.status())) return "AI 해설: 없음 (규칙 점검 결과만 표시)\n";
        if (!"SUCCESS".equals(f.status())) return "AI 해설: 불러오지 못했습니다. 점검 결과는 그대로입니다.\n";
        StringBuilder sb = new StringBuilder("AI 해설 (점수에 반영되지 않음)\n");
        f.strengths().forEach(s -> sb.append("  + ").append(s).append('\n'));
        f.improvements().forEach(s -> sb.append("  - ").append(s).append('\n'));
        if (f.exampleRewrite() != null && !f.exampleRewrite().isBlank()) {
            sb.append("  예시: ").append(f.exampleRewrite()).append('\n');
        }
        return sb.toString();
    }
}

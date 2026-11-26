package app.scoring;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.MedicationRecord;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.RawNote;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * R10 원자료 정리 (배점 10, D33). 학생이 원자료 메모를 보고 필요한 사실만 골라 정리했는지 점검 2개로 본다.
 * <ol>
 *   <li>그대로 옮김: 관찰 기록이 원자료 메모와 거의 같으면(메모 문장을 길게 그대로 이어 붙이면) 불일치.</li>
 *   <li>관련 없는 정보: 관련 없는 메모의 낱말(cues)이 관찰·투약·SBAR 기록에 들어가면 불일치.</li>
 * </ol>
 * 점검마다 통과·불일치 하나만 내고, 어느 기록에서 걸렸는지는 메시지에 적는다.
 * 환자 발언은 따옴표로 그대로 인용하는 것이 맞으므로(R1) 따옴표 안의 말은 비교에서 뺀다.
 * 원자료 메모가 없는 케이스(예전 케이스)는 아무것도 내지 않는다(미산출, 배점에서 빠진다).
 */
public final class RawNotesChecker {
    public static final String RULE = "R10";

    public static final String COPIED = "원자료를 그대로 옮겼습니다";
    public static final String IRRELEVANT = "관련 없는 정보가 들어갔습니다";
    /** 점검 위치 표기 (결과 화면에서 "원자료 정리 · …"로 보인다) */
    public static final String REF_COPIED = "rawNotes.copied";
    public static final String REF_IRRELEVANT = "rawNotes.irrelevant";

    /** 메모와 같은 글자가 이만큼 이어지면 '공통 구간'으로 센다 (띄어쓰기·문장부호 제외 글자 수). */
    static final int RUN = 10;
    /** 관찰 기록에서 공통 구간이 차지하는 비율이 이 이상이면 그대로 옮긴 것으로 본다. */
    static final double COVERAGE = 0.6;
    /** 한 번에 이만큼 길게 같으면 비율과 상관없이 그대로 옮긴 것으로 본다. */
    static final int LONG_RUN = 40;
    /** 너무 짧은 기록은 비율 판정을 하지 않는다. */
    static final int MIN_LENGTH = 30;

    private static final Pattern QUOTE = Pattern.compile("\"[^\"]*\"|“[^”]*”|'[^']*'|‘[^’]*’|「[^」]*」");

    private RawNotesChecker() {
    }

    /** 제출한 기록을 원자료 메모와 비교해 점검 2개(그대로 옮김·관련 없는 정보)의 결과를 돌려준다. */
    public static List<CheckResult> check(PatientCase c, List<ObservationEntry> observations,
                                          List<MedicationRecord> medications, SbarReport sbar) {
        List<CheckResult> out = new ArrayList<>();
        if (c == null || !c.hasRawNotes()) return out;
        String notes = compact(withoutQuotes(allNotes(c.rawNotes())));
        List<ObservationEntry> obs = observations == null ? List.of() : observations;
        List<String> copiedAt = new ArrayList<>();
        for (int i = 0; i < obs.size(); i++) {
            ObservationEntry o = obs.get(i);
            if (o == null) continue;
            String text = nz(o.narrative()) + " " + nz(o.intervention());
            if (copied(compact(withoutQuotes(text)), notes)) copiedAt.add("관찰 기록 " + (i + 1));
        }
        out.add(copiedAt.isEmpty()
                ? new CheckResult(RULE, CheckTarget.OBSERVATION, REF_COPIED, CheckStatus.PASS,
                        "원자료를 그대로 옮기지 않고 정리했습니다.", null)
                : new CheckResult(RULE, CheckTarget.OBSERVATION, REF_COPIED, CheckStatus.MISMATCH,
                        COPIED + " (" + String.join(", ", copiedAt) + "). 필요한 사실만 골라 정리하세요.", null));

        List<RawNote> irrelevant = c.rawNotes().stream().filter(RawNote::irrelevant).toList();
        if (irrelevant.isEmpty()) return out; // 관련 없는 메모가 없는 케이스는 이 점검을 하지 않는다
        List<String> hits = new ArrayList<>();
        for (int i = 0; i < obs.size(); i++) {
            ObservationEntry o = obs.get(i);
            if (o == null) continue;
            hit(hits, "관찰 기록 " + (i + 1), nz(o.narrative()) + " " + nz(o.intervention()), irrelevant);
        }
        List<MedicationRecord> meds = medications == null ? List.of() : medications;
        for (int i = 0; i < meds.size(); i++) {
            MedicationRecord m = meds.get(i);
            if (m == null) continue;
            hit(hits, "투약 기록 " + (i + 1),
                    String.join(" ", nz(m.reason()), nz(m.orderBasis()), nz(m.reassessmentPlan())), irrelevant);
        }
        if (sbar != null) {
            hit(hits, "SBAR S", sbar.situation(), irrelevant);
            hit(hits, "SBAR B", sbar.background(), irrelevant);
            hit(hits, "SBAR A", sbar.assessment(), irrelevant);
            StringBuilder r = new StringBuilder();
            for (SbarRecommendation x : sbar.recommendations()) {
                if (x != null) r.append(nz(x.intervention())).append(' ');
            }
            hit(hits, "SBAR R", r.toString(), irrelevant);
        }
        out.add(hits.isEmpty()
                ? new CheckResult(RULE, CheckTarget.OBSERVATION, REF_IRRELEVANT, CheckStatus.PASS,
                        "관련 없는 정보를 넣지 않았습니다.", null)
                : new CheckResult(RULE, CheckTarget.OBSERVATION, REF_IRRELEVANT, CheckStatus.MISMATCH,
                        IRRELEVANT + " (" + String.join(", ", hits) + "). 이번 사건과 관련된 사실만 기록하세요.", null));
        return out;
    }

    /** 관련 없는 메모의 낱말이 글에 있으면 "어디('낱말', …)"를 모은다. */
    private static void hit(List<String> hits, String where, String text, List<RawNote> irrelevant) {
        List<String> cues = irrelevantCues(text, irrelevant);
        if (!cues.isEmpty()) hits.add(where + " '" + String.join("', '", cues) + "'");
    }

    /** 글에 들어 있는 관련 없는 메모의 낱말 목록. */
    public static List<String> irrelevantCues(String text, List<RawNote> irrelevant) {
        List<String> hits = new ArrayList<>();
        String t = squash(text);
        if (t.isEmpty()) return hits;
        for (RawNote n : irrelevant) {
            for (String cue : n.cues()) {
                String q = squash(cue);
                if (!q.isEmpty() && t.contains(q) && !hits.contains(cue)) hits.add(cue);
            }
        }
        return hits;
    }

    /**
     * 학생 글(정리한 글자열)이 메모와 거의 같은지: 메모와 같은 구간(RUN 글자 이상)이 글의 COVERAGE 이상을 덮거나,
     * 한 구간이 LONG_RUN 글자 이상이면 그대로 옮긴 것으로 본다.
     */
    static boolean copied(String student, String notes) {
        if (student.isEmpty() || notes.isEmpty()) return false;
        boolean[] covered = new boolean[student.length()];
        int longest = 0;
        int i = 0;
        while (i < student.length()) {
            int best = longestMatchAt(student, i, notes);
            if (best >= RUN) {
                for (int k = i; k < i + best; k++) covered[k] = true;
                longest = Math.max(longest, best);
                i += best;
            } else {
                i++;
            }
        }
        if (longest >= LONG_RUN) return true;
        if (student.length() < MIN_LENGTH) return false;
        int n = 0;
        for (boolean b : covered) if (b) n++;
        return n >= COVERAGE * student.length();
    }

    /** student의 from 위치에서 시작해 notes 안에 그대로 있는 가장 긴 길이. */
    private static int longestMatchAt(String student, int from, String notes) {
        int lo = 0;
        int hi = student.length() - from;
        // 길이가 길수록 포함되기 어렵다(단조). 이분 탐색으로 가장 긴 길이를 찾는다.
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (notes.contains(student.substring(from, from + mid))) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    /** 메모 전체를 한 글로 (시각 포함). */
    public static String allNotes(List<RawNote> notes) {
        StringBuilder sb = new StringBuilder();
        for (RawNote n : notes) {
            if (n != null) sb.append(nz(n.time())).append(' ').append(nz(n.text())).append('\n');
        }
        return sb.toString();
    }

    /** 따옴표 안의 말(환자 발언)을 뺀다. */
    static String withoutQuotes(String s) {
        return QUOTE.matcher(nz(s)).replaceAll(" ");
    }

    /** 비교용: 글자·숫자만 남기고 소문자로 (띄어쓰기·문장부호·말줄임표 제거). */
    static String compact(String s) {
        return nz(s).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String squash(String s) {
        return nz(s).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

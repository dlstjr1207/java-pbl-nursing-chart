package app.scoring;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.MedicationRecord;
import app.domain.ObservationEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * R4 재평가 연결 (D33 보강). 관찰 기록(서술·처치) 또는 투약 기록(재평가 계획) 중 한 곳에
 * "재평가·재사정·재측정·재확인"과 시각(HH:mm 또는 "30분 후" 같은 표현)이 같은 문장에 함께 있어야 통과한다.
 * 낱말 바로 뒤에 "안 함·하지 않음·없음·생략" 같은 부정 표현이 붙으면 그 문장은 계획으로 보지 않는다 (V4).
 */
public final class ReassessmentChecker {
    public static final String RULE = "R4";
    static final Pattern PLAN = Pattern.compile("재평가|재사정|재측정|재확인");
    /** 시각: 10:00, 10시, 10시 30분, 30분 후, 1시간 뒤, 30분 이내 */
    static final Pattern WHEN = Pattern.compile(
            "([01]?\\d|2[0-3]):[0-5]\\d|\\d{1,2}\\s*시(\\s*\\d{1,2}\\s*분)?|\\d+\\s*(분|시간)\\s*(후|뒤|이내|내|경과)");
    /** 재평가 낱말 바로 뒤(4글자 안)에 붙은 부정 표현. 가운데 글은 2번 묶음 */
    static final Pattern NEGATED = Pattern.compile(
            "(재평가|재사정|재측정|재확인)([^.\\n;。]{0,4}?)(안\\s*함|안\\s*했|안\\s*하|하지\\s*않|않음|않았|없음|없었|생략|미시행|못\\s*함|못\\s*했)");
    /** 문장 나누기: 마침표·줄바꿈·세미콜론·슬래시 (쉼표는 "30분 후, 재평가"처럼 한 문장 안에 쓰여 나누지 않는다) */
    private static final Pattern SENTENCE = Pattern.compile("[.\\n;。/]");

    private ReassessmentChecker() {
    }

    public static List<CheckResult> check(List<ObservationEntry> observations, List<MedicationRecord> medications) {
        List<String> texts = new ArrayList<>();
        if (medications != null) {
            for (MedicationRecord m : medications) {
                if (m != null && m.reassessmentPlan() != null) texts.add(m.reassessmentPlan());
            }
        }
        if (observations != null) {
            for (ObservationEntry o : observations) {
                if (o == null) continue;
                texts.add(o.narrative() == null ? "" : o.narrative());
                texts.add(o.intervention() == null ? "" : o.intervention());
            }
        }
        boolean found = texts.stream().anyMatch(ReassessmentChecker::hasPlan);
        return List.of(new CheckResult(RULE, CheckTarget.MEDICATION, "reassessment",
                found ? CheckStatus.PASS : CheckStatus.MISSING,
                found ? "재평가 계획(시각 포함)이 있습니다."
                      : "재평가 계획을 언제 다시 확인할지(예: 10:00 재평가, 30분 후 재평가) 관찰 기록이나 투약 기록에 적으세요.",
                null));
    }

    /** "재평가 안 함"처럼 부정됐는지. "재평가 이상 없음"·"재확인 특이사항 없음"은 결과를 적은 것이라 부정으로 보지 않는다. */
    static boolean negated(String sentence) {
        var m = NEGATED.matcher(sentence);
        while (m.find()) {
            if (!m.group(2).matches(".*(이상|특이|문제|변화|증상).*")) return true;
        }
        return false;
    }

    /** 한 글 안에 "재평가 낱말 + 시각"이 같은 문장에 있고, 그 문장의 재평가가 부정되지 않았는지. */
    static boolean hasPlan(String text) {
        if (text == null || text.isBlank()) return false;
        for (String sentence : SENTENCE.split(text)) {
            if (!PLAN.matcher(sentence).find()) continue;
            if (negated(sentence)) continue;
            if (WHEN.matcher(sentence).find()) return true;
        }
        return false;
    }
}

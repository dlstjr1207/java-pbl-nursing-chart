package app.scoring;

import app.domain.AnswerKey;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.MedicationRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * R7 값 일치. 정답 키의 투약 값(약명·용량·단위·경로·시각)과 학생 투약 기록을 칸별로 비교한다.
 * 정답 약과 같은 약의 기록을 찾아 비교하고, 없으면 누락으로 본다. 정답 키가 없으면 미산출이다.
 */
public final class ValueMatchChecker {
    public static final String RULE = "R7";

    /** 정답 키 요소 이름 → 투약 기록의 칸. */
    private static final Map<String, Function<MedicationRecord, String>> FIELDS = Map.of(
            "약명", MedicationRecord::drugName,
            "용량", MedicationRecord::dose,
            "단위", MedicationRecord::unit,
            "경로", MedicationRecord::route,
            "시각", MedicationRecord::administeredTime);

    private ValueMatchChecker() {
    }

    public static List<CheckResult> check(List<MedicationRecord> records, AnswerKey key) {
        List<ElementPattern> expected = key == null ? List.of() : ElementPattern.parseAll(key.medicationElements());
        if (expected.isEmpty()) {
            return List.of(new CheckResult(RULE, CheckTarget.MEDICATION, "medication", CheckStatus.NOT_SCORED,
                    "이 케이스에는 투약 정답 값이 없어 미산출입니다.", null));
        }
        ElementPattern drug = expected.stream().filter(e -> e.label().equals("약명")).findFirst().orElse(null);
        MedicationRecord target = null;
        int index = 0;
        if (records != null) {
            for (int i = 0; i < records.size(); i++) {
                MedicationRecord r = records.get(i);
                if (drug == null || drug.foundIn(r.drugName())) {
                    target = r;
                    index = i + 1;
                    break;
                }
            }
        }
        List<CheckResult> out = new ArrayList<>();
        if (target == null) {
            out.add(new CheckResult(RULE, CheckTarget.MEDICATION, "medication", CheckStatus.MISSING,
                    "정답 처방(" + (drug == null ? "해당 약" : drug.expected()) + ")의 투약 기록이 없습니다.", null));
            return out;
        }
        for (ElementPattern e : expected) {
            Function<MedicationRecord, String> field = FIELDS.get(e.label());
            if (field == null) continue;
            String value = field.apply(target);
            boolean ok = e.label().equals("약명") ? e.foundIn(value) : e.equalsValue(value);
            String ref = "medication[" + index + "]." + e.label();
            out.add(ok
                    ? new CheckResult(RULE, CheckTarget.MEDICATION, ref, CheckStatus.PASS,
                            "투약 " + e.label() + "이(가) 기준과 같습니다.", null)
                    : new CheckResult(RULE, CheckTarget.MEDICATION, ref, CheckStatus.MISMATCH,
                            "투약 " + e.label() + ": 입력 '" + (value == null ? "" : value) + "', 기준 '" + e.expected() + "'", null));
        }
        return out;
    }
}

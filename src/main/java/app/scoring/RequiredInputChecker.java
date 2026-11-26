package app.scoring;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.MedicationRecord;
import app.domain.ObservationEntry;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import java.util.ArrayList;
import java.util.List;

/**
 * R8 필수 입력 점검. 관찰 분류·서술, 투약 기록 유무, SBAR S·B·A·R이 비어 있지 않은지 본다.
 * 값이 맞는지는 보지 않는다(R7 범위 밖). 결과만 보이고 점수에는 넣지 않는다(D33).
 */
public final class RequiredInputChecker {
    public static final String RULE = "R8";

    private RequiredInputChecker() {}

    /** 세 영역을 모두 점검해 항목별 결과(통과·누락)를 돌려준다. null 목록·null SBAR은 비어 있는 것으로 본다. */
    public static List<CheckResult> check(List<ObservationEntry> observations,
                                          List<MedicationRecord> medications, SbarReport sbar) {
        List<CheckResult> out = new ArrayList<>();
        out.addAll(checkObservations(observations));
        out.addAll(checkMedications(medications));
        out.addAll(checkSbar(sbar));
        return out;
    }

    /** 관찰 기록: 1건 이상, 각 기록에 분류 태그 1개 이상과 서술. 입력 위치는 "observation[n]". */
    public static List<CheckResult> checkObservations(List<ObservationEntry> entries) {
        List<CheckResult> out = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            out.add(missing(CheckTarget.OBSERVATION, "observation", "관찰 기록이 없습니다. 1건 이상 작성하세요."));
            return out;
        }
        for (int i = 0; i < entries.size(); i++) {
            ObservationEntry e = entries.get(i);
            int n = i + 1;
            out.add(item(CheckTarget.OBSERVATION, "observation[" + n + "].tags",
                    e.tags() != null && e.tags().stream().anyMatch(t -> !blank(t)),
                    "관찰 " + n + ": 분류 태그를 1개 이상 선택하세요.", "관찰 " + n + ": 분류 태그가 있습니다."));
            out.add(item(CheckTarget.OBSERVATION, "observation[" + n + "].narrative", !blank(e.narrative()),
                    "관찰 " + n + ": 서술을 입력하세요.", "관찰 " + n + ": 서술이 있습니다."));
        }
        return out;
    }

    /** 투약 기록: 1건 이상 있어야 한다. 세부 필수값은 R6에서 점검한다. */
    public static List<CheckResult> checkMedications(List<MedicationRecord> records) {
        boolean has = records != null && !records.isEmpty();
        return List.of(item(CheckTarget.MEDICATION, "medication", has,
                "투약 기록이 없습니다. 1건 이상 작성하세요.", "투약 기록이 있습니다."));
    }

    /** SBAR: S·B·A 텍스트와 R 행(1행 이상, 각 행의 시각·중재). 입력 위치는 situation·background·assessment·rec. */
    public static List<CheckResult> checkSbar(SbarReport sbar) {
        List<CheckResult> out = new ArrayList<>();
        out.add(item(CheckTarget.SBAR, "situation", sbar != null && !blank(sbar.situation()),
                "SBAR S(상황)를 입력하세요.", "SBAR S(상황)가 있습니다."));
        out.add(item(CheckTarget.SBAR, "background", sbar != null && !blank(sbar.background()),
                "SBAR B(배경)를 입력하세요.", "SBAR B(배경)가 있습니다."));
        out.add(item(CheckTarget.SBAR, "assessment", sbar != null && !blank(sbar.assessment()),
                "SBAR A(평가)를 입력하세요.", "SBAR A(평가)가 있습니다."));
        List<SbarRecommendation> rows = sbar == null ? List.of() : sbar.recommendations();
        if (rows.isEmpty()) {
            out.add(missing(CheckTarget.SBAR, "rec", "SBAR R(권고)에 시각과 중재를 한 줄 이상 입력하세요."));
        } else {
            for (int i = 0; i < rows.size(); i++) {
                SbarRecommendation r = rows.get(i);
                int n = i + 1;
                out.add(item(CheckTarget.SBAR, "rec" + n + ".time", !blank(r.time()),
                        "R " + n + "행: 시각을 입력하세요.", "R " + n + "행: 시각이 있습니다."));
                out.add(item(CheckTarget.SBAR, "rec" + n + ".intervention", !blank(r.intervention()),
                        "R " + n + "행: 중재를 입력하세요.", "R " + n + "행: 중재가 있습니다."));
            }
        }
        return out;
    }

    private static CheckResult item(CheckTarget t, String ref, boolean ok, String missingMsg, String passMsg) {
        return ok ? new CheckResult(RULE, t, ref, CheckStatus.PASS, passMsg, null) : missing(t, ref, missingMsg);
    }

    private static CheckResult missing(CheckTarget t, String ref, String msg) {
        return new CheckResult(RULE, t, ref, CheckStatus.MISSING, msg, null);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

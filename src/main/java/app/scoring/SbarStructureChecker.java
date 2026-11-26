package app.scoring;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.SbarElements;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import java.util.ArrayList;
import java.util.List;

/**
 * R9 SBAR 구성 점검(배점 10, D33). 케이스가 정한 S·B·A·R 영역별 필수 요소가 글에 들어 있는지(동의어 포함 여부)를
 * 요소 하나당 점검 하나로 센다. 문장의 의미는 판정하지 않는다. R 행의 시각·중재 빈칸은 R8이 본다.
 * 필수 요소 목록이 없거나 비어 있는 영역은 NOT_SCORED(미산출)로 둔다.
 */
public final class SbarStructureChecker {
    public static final String RULE = "R9";

    private SbarStructureChecker() {}

    /** 영역별 필수 요소 결과를 돌려준다. elements는 null 허용(전 영역 미산출). */
    public static List<CheckResult> check(SbarReport sbar, SbarElements elements) {
        List<CheckResult> out = new ArrayList<>();
        out.addAll(area("situation", "S(상황)", sbar == null ? null : sbar.situation(),
                elements == null ? null : elements.situation()));
        out.addAll(area("background", "B(배경)", sbar == null ? null : sbar.background(),
                elements == null ? null : elements.background()));
        out.addAll(area("assessment", "A(평가)", sbar == null ? null : sbar.assessment(),
                elements == null ? null : elements.assessment()));
        out.addAll(recommendation(sbar, elements == null ? null : elements.recommendation()));
        return out;
    }

    /** 영역 하나: 필수 요소마다 글에 포함되는지 확인. 입력 위치는 "영역.element[n]". */
    private static List<CheckResult> area(String ref, String label, String text, List<String> required) {
        List<CheckResult> out = new ArrayList<>();
        if (required == null || required.isEmpty()) {
            out.add(new CheckResult(RULE, CheckTarget.SBAR, ref, CheckStatus.NOT_SCORED,
                    "SBAR " + label + " 필수 요소 기준이 없어 미산출입니다.", null));
            return out;
        }
        for (int i = 0; i < required.size(); i++) {
            ElementPattern el = ElementPattern.parse(required.get(i));
            boolean ok = el.foundIn(text);
            out.add(new CheckResult(RULE, CheckTarget.SBAR, ref + ".element[" + (i + 1) + "]",
                    ok ? CheckStatus.PASS : CheckStatus.MISSING,
                    ok ? "SBAR " + label + ": '" + el.label() + "' 요소가 있습니다."
                       : "SBAR " + label + "에 '" + el.label() + "' 요소를 포함하세요. (예: " + el.example() + ")", null));
        }
        return out;
    }

    /**
     * R: 케이스 필수 요소를 R 행 전체(시각+중재) 글에서 찾는다. 행마다 시각·중재가 있는지는 화면이 제출 전에 막고
     * R8이 점검하므로 여기서는 세지 않는다 (R9 점수는 S·B·A·R 필수 요소 점검만으로 낸다, D33).
     */
    private static List<CheckResult> recommendation(SbarReport sbar, List<String> required) {
        List<CheckResult> out = new ArrayList<>();
        List<SbarRecommendation> rows = sbar == null ? List.of() : sbar.recommendations();
        StringBuilder all = new StringBuilder();
        for (SbarRecommendation r : rows) {
            if (r == null) continue;
            all.append(r.time() == null ? "" : r.time()).append(' ')
                    .append(r.intervention() == null ? "" : r.intervention()).append('\n');
        }
        if (required == null || required.isEmpty()) {
            out.add(new CheckResult(RULE, CheckTarget.SBAR, "rec.element", CheckStatus.NOT_SCORED,
                    "SBAR R(권고) 필수 요소 기준이 없어 미산출입니다.", null));
        } else {
            for (int i = 0; i < required.size(); i++) {
                ElementPattern el = ElementPattern.parse(required.get(i));
                boolean ok = el.foundIn(all.toString());
                out.add(new CheckResult(RULE, CheckTarget.SBAR, "rec.element[" + (i + 1) + "]",
                        ok ? CheckStatus.PASS : CheckStatus.MISSING,
                        ok ? "SBAR R(권고): '" + el.label() + "' 요소가 있습니다."
                           : "SBAR R(권고)에 '" + el.label() + "' 요소를 포함하세요. (예: " + el.example() + ")", null));
            }
        }
        return out;
    }
}

package app.scoring;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.SbarElements;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * R9 SBAR 구성 점검(구조만). 케이스가 정한 영역별 필수 요소가 글에 들어 있는지(문자열 포함 여부),
 * R의 각 행에 HH:mm 시각과 중재가 있는지 본다. 문장의 의미는 판정하지 않는다.
 * 필수 요소 목록이 없거나 비어 있는 영역은 NOT_SCORED(미산출)로 둔다. 점수는 가중치 미정이라 null이다.
 */
public final class SbarStructureChecker {
    public static final String RULE = "R9";
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private SbarStructureChecker() {}

    /** 영역별 요소 결과와 R 행 결과를 돌려준다. elements는 null 허용(전 영역 미산출). */
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
            String el = required.get(i);
            boolean ok = contains(text, el);
            out.add(new CheckResult(RULE, CheckTarget.SBAR, ref + ".element[" + (i + 1) + "]",
                    ok ? CheckStatus.PASS : CheckStatus.MISSING,
                    ok ? "SBAR " + label + ": '" + el + "' 요소가 있습니다."
                       : "SBAR " + label + "에 '" + el + "' 요소를 포함하세요.", null));
        }
        return out;
    }

    /** R: 행마다 시각(HH:mm)·중재 확인, 케이스 필수 요소는 중재 전체 글에서 찾는다. */
    private static List<CheckResult> recommendation(SbarReport sbar, List<String> required) {
        List<CheckResult> out = new ArrayList<>();
        List<SbarRecommendation> rows = sbar == null ? List.of() : sbar.recommendations();
        if (rows.isEmpty()) {
            out.add(new CheckResult(RULE, CheckTarget.SBAR, "rec", CheckStatus.MISSING,
                    "SBAR R(권고)에 시각과 중재를 한 줄 이상 입력하세요.", null));
        }
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            SbarRecommendation r = rows.get(i);
            int n = i + 1;
            boolean timeOk = r.time() != null && TIME.matcher(r.time().trim()).matches();
            boolean actOk = r.intervention() != null && !r.intervention().isBlank();
            out.add(new CheckResult(RULE, CheckTarget.SBAR, "rec" + n + ".time",
                    timeOk ? CheckStatus.PASS : CheckStatus.MISSING,
                    timeOk ? "R " + n + "행: 시각이 있습니다." : "R " + n + "행: 시각을 HH:mm 형식으로 입력하세요.", null));
            out.add(new CheckResult(RULE, CheckTarget.SBAR, "rec" + n + ".intervention",
                    actOk ? CheckStatus.PASS : CheckStatus.MISSING,
                    actOk ? "R " + n + "행: 중재가 있습니다." : "R " + n + "행: 중재를 입력하세요.", null));
            if (actOk) all.append(r.intervention()).append('\n');
        }
        if (required == null || required.isEmpty()) {
            out.add(new CheckResult(RULE, CheckTarget.SBAR, "rec.element", CheckStatus.NOT_SCORED,
                    "SBAR R(권고) 필수 요소 기준이 없어 미산출입니다.", null));
        } else {
            for (int i = 0; i < required.size(); i++) {
                boolean ok = contains(all.toString(), required.get(i));
                out.add(new CheckResult(RULE, CheckTarget.SBAR, "rec.element[" + (i + 1) + "]",
                        ok ? CheckStatus.PASS : CheckStatus.MISSING,
                        ok ? "SBAR R(권고): '" + required.get(i) + "' 요소가 있습니다."
                           : "SBAR R(권고)에 '" + required.get(i) + "' 요소를 포함하세요.", null));
            }
        }
        return out;
    }

    private static boolean contains(String text, String element) {
        if (text == null || element == null || element.isBlank()) return false;
        return text.toLowerCase().contains(element.trim().toLowerCase());
    }
}

package app.ui;

import app.domain.CheckResult;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 점검 결과의 위치 표기(observation.fact[장소], medication[1].용량, rec1.time 등)를 학생이 읽는 말로 바꾼다 (polish A-2).
 * 예: "관찰 기록 · 장소", "투약 기록 1 · 용량", "SBAR R 1행 · 시각". 규칙 ID는 뒤에 괄호로 붙인다.
 */
public final class CheckLabels {

    /** 규칙 ID → 이름 (기획안 점검 기준) */
    public static final Map<String, String> RULE_NAMES = Map.of(
            "R1", "관찰 표현", "R2", "핵심 사실", "R4", "재평가 연결", "R6", "투약 기록 완결성",
            "R7", "투약 값 일치", "R8", "필수 입력", "R9", "SBAR 구성", "R10", "원자료 정리",
            "MSE", "정신상태 평가, 배점 없음");

    /** 필드 이름 → 한국어 */
    private static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("narrative", "서술"), Map.entry("tags", "분류 태그"), Map.entry("time", "시각"),
            Map.entry("shift", "근무"), Map.entry("intervention", "중재"), Map.entry("drugName", "약명"),
            Map.entry("dose", "용량"), Map.entry("unit", "단위"), Map.entry("route", "경로"),
            Map.entry("administeredTime", "투약 시각"), Map.entry("orderBasis", "처방 근거"),
            Map.entry("reason", "사유"), Map.entry("result", "투약 결과"), Map.entry("oralCheck", "구강 확인"),
            Map.entry("reassessmentPlan", "재평가 계획"), Map.entry("element", "필수 요소"));

    private static final Map<String, String> AREAS = Map.of(
            "observation", "관찰 기록", "medication", "투약 기록", "situation", "SBAR S(상황)",
            "background", "SBAR B(배경)", "assessment", "SBAR A(평가)", "rec", "SBAR R(권고)",
            "recommendation", "SBAR R(권고)", "reassessment", "재평가 계획",
            "rawNotes.copied", "원자료 정리 · 그대로 옮김", "rawNotes.irrelevant", "원자료 정리 · 관련 없는 정보");

    /** 영역[번호].필드 / 영역.fact[이름] / rec번호.필드 / 영역.element[번호] */
    private static final Pattern INDEXED = Pattern.compile("^([a-z]+)\\[(\\d+)\\](?:\\.(.+))?$");
    private static final Pattern FACT = Pattern.compile("^([a-z]+)\\.fact\\[(.+)\\]$");
    private static final Pattern REC_ROW = Pattern.compile("^rec(\\d+)\\.([a-zA-Z]+)$");
    private static final Pattern ELEMENT = Pattern.compile("^([a-z]+)\\.element\\[(\\d+)\\]$");

    private CheckLabels() {
    }

    /** 위치 표기를 사람 말로. 모르는 형식이면 그대로 돌려준다. */
    public static String where(String ref) {
        if (ref == null || ref.isBlank()) return "";
        Matcher m = REC_ROW.matcher(ref);
        if (m.matches()) return "SBAR R " + m.group(1) + "행 · " + field(m.group(2));
        m = FACT.matcher(ref);
        if (m.matches()) return area(m.group(1)) + " · " + m.group(2);
        m = ELEMENT.matcher(ref);
        if (m.matches()) return area(m.group(1)) + " · 필수 요소 " + m.group(2);
        m = INDEXED.matcher(ref);
        if (m.matches()) {
            String base = area(m.group(1)) + " " + m.group(2);
            return m.group(3) == null ? base : base + " · " + field(m.group(3));
        }
        if ("mse".equals(ref)) return "관찰 기록 · 정신상태 평가";
        return AREAS.getOrDefault(ref, ref);
    }

    /** 결과 한 줄: "관찰 기록 · 장소 — 이유 (R2 핵심 사실)" */
    public static String line(CheckResult c) {
        String where = where(c.targetRef());
        String msg = StudentText.message(c.message());
        return (where.isEmpty() ? "" : where + " — ") + msg + " (" + rule(c.ruleId()) + ")";
    }

    /** "R7 투약 값 일치" */
    public static String rule(String id) {
        String name = RULE_NAMES.get(id);
        return name == null ? String.valueOf(id) : id + " " + name;
    }

    private static String area(String a) {
        return AREAS.getOrDefault(a, a);
    }

    private static String field(String f) {
        return FIELDS.getOrDefault(f, f);
    }
}

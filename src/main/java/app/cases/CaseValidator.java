package app.cases;

import app.domain.Medication;
import app.domain.OrderType;
import app.domain.PatientCase;
import app.domain.VitalSign;
import java.util.ArrayList;
import java.util.List;

/**
 * 생성된 케이스를 사용하기 전에 검사한다 (F-14, T-30).
 * 필수 항목·형식(시각 HH:mm, 용량 숫자)·허용 약물·용량 범위·정답 키를 보고, 위반을 원인과 함께 모두 모아 돌려준다.
 * 생성 케이스도 PC-01과 같은 규칙으로 채점하므로 정답 키가 있어야 하고, 정답 키의 약·용량은 처방과 같아야 한다.
 * 허용 약물 목록이 비어 있으면 AI 생성을 쓸 수 없다({@link #generationAvailable()}).
 */
public class CaseValidator {
    /** 기존 관찰 로그 최소 건수 */
    public static final int MIN_LOGS = 2;
    /** 활력징후 최소 측정 횟수 */
    public static final int MIN_VITALS = 2;

    private static final String TIME = "([01]\\d|2[0-3]):[0-5]\\d";

    private final AllowedDrugs allowed;

    public CaseValidator(AllowedDrugs allowed) {
        this.allowed = allowed;
    }

    /** 검사에 쓰는 허용 약물 목록 (생성 요청에 함께 보낸다). */
    public AllowedDrugs allowedDrugs() {
        return allowed;
    }

    /** 허용 약물 목록이 채워져 있어 AI 생성을 쓸 수 있는지 (D26). */
    public boolean generationAvailable() {
        return !allowed.isEmpty();
    }

    /** 문제 목록을 돌려준다. 비어 있으면 통과. */
    public List<String> check(PatientCase c) {
        if (c == null) {
            return List.of("케이스 내용이 비어 있음");
        }
        List<String> p = new ArrayList<>(CaseLoader.validate(c));
        if (blank(c.briefing())) {
            p.add("briefing(상황 브리핑) 누락");
        }
        // 학생이 읽고 기록할 재료가 충분해야 한다 (polish 12): 기존 관찰 로그·활력징후 2건 이상
        if (c.observationLog().size() < MIN_LOGS) {
            p.add("observationLog(기존 관찰 로그) " + MIN_LOGS + "건 이상 필요 (현재 " + c.observationLog().size() + "건)");
        }
        if (c.vitals().size() < MIN_VITALS) {
            p.add("vitals(활력징후) " + MIN_VITALS + "회 이상 필요 (현재 " + c.vitals().size() + "회)");
        }
        for (int i = 0; i < c.orders().size(); i++) {
            Medication m = c.orders().get(i);
            if (m != null && !blank(m.drugName())) {
                checkOrder(p, i, m);
            }
        }
        checkAnswerKey(p, c);
        checkKeyLanguages(p, c);
        checkBriefingMatchesKey(p, c);
        for (int i = 0; i < c.vitals().size(); i++) {
            VitalSign v = c.vitals().get(i);
            if (v == null || v.time() == null || !v.time().matches(TIME)) {
                p.add("vitals[" + i + "].time 형식 오류 (HH:mm)");
            }
            if (v != null) checkVitalRanges(p, i, v);
        }
        return p;
    }

    /**
     * 활력징후가 생리적으로 가능한 범위인지 (생성 케이스 거부 기준, 정상 범위가 아니라 '있을 수 있는 값'의 바깥 경계).
     * 사용자 지정 범위: 수축기 70~200, 이완기 40~130, 맥박 40~160, 호흡 8~40, 체온 34~41, SpO2 80~100.
     */
    static final double[][] VITAL_LIMITS = {{70, 200}, {40, 130}, {40, 160}, {8, 40}, {34, 41}, {80, 100}};
    static final String[] VITAL_NAMES = {"systolic(수축기)", "diastolic(이완기)", "pulse(맥박)", "respiration(호흡)",
            "temperature(체온)", "spo2(SpO2)"};

    private static void checkVitalRanges(List<String> p, int i, VitalSign v) {
        Number[] values = {v.systolic(), v.diastolic(), v.pulse(), v.respiration(), v.temperature(), v.spo2()};
        for (int k = 0; k < values.length; k++) {
            if (values[k] == null) continue; // 값이 비어 있는 것은 형식 검사(CaseLoader)가 본다
            double x = values[k].doubleValue();
            if (x < VITAL_LIMITS[k][0] || x > VITAL_LIMITS[k][1]) {
                p.add("vitals[" + i + "]." + VITAL_NAMES[k] + " " + values[k] + "이(가) 가능한 범위("
                        + fmt(VITAL_LIMITS[k][0]) + "~" + fmt(VITAL_LIMITS[k][1]) + ") 밖");
            }
        }
    }

    /**
     * 정답 키와 브리핑이 서로 맞는지: 학생은 브리핑을 보고 쓰므로, 브리핑에 없는 사실을 정답으로 요구하면 안 된다.
     * 투약 정답 시각이 브리핑에 있는지, '장소' 동의어 중 하나가 브리핑에 있는지, '발언 인용' 동의어가 브리핑의 따옴표 안에 있는지 본다.
     */
    private static void checkBriefingMatchesKey(List<String> p, PatientCase c) {
        var key = c.answerKey();
        if (key == null || blank(c.briefing())) return;
        String briefing = c.briefing();
        app.scoring.ElementPattern.parseAll(key.medicationElements()).stream()
                .filter(e -> e.label().equals("시각")).findFirst()
                .ifPresent(t -> {
                    if (!t.foundIn(briefing)) {
                        p.add("answerKey 투약 시각(" + t.expected() + ")이 briefing에 없음");
                    }
                });
        for (var e : app.scoring.ElementPattern.parseAll(key.observationElements())) {
            if (e.label().equals("장소") && !e.foundIn(briefing)) {
                p.add("answerKey '장소'(" + e.example() + ") 동의어가 briefing에 없음");
            }
            if (e.label().equals("발언 인용") && !e.foundIn(quoted(briefing))) {
                p.add("answerKey '발언 인용'(" + e.example() + ") 동의어가 briefing의 따옴표 안 환자 발언에 없음");
            }
        }
    }

    /** 글에서 따옴표(" " “ ” ' ' ‘ ’) 안의 말만 모은다. */
    static String quoted(String text) {
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[\"“”]([^\"“”]+)[\"“”]|['‘’]([^'‘’]+)['‘’]").matcher(text);
        while (m.find()) sb.append(m.group(1) != null ? m.group(1) : m.group(2)).append(' ');
        return sb.toString();
    }

    /**
     * 학생이 한글로 써도 맞게 채점되도록, 정답 키의 약명은 한글·영어를 모두, 경로는 PO와 경구를 모두 갖게 한다.
     * 투약 정답(medicationElements)의 약명·경로와 관찰 핵심 사실의 '투약' 요소를 본다.
     */
    private static void checkKeyLanguages(List<String> p, PatientCase c) {
        var key = c.answerKey();
        if (key == null) return;
        boolean hasRoute = false;
        for (var e : app.scoring.ElementPattern.parseAll(key.medicationElements())) {
            if (e.groups().isEmpty()) continue;
            if (e.label().equals("약명")) requireBoth(p, "answerKey.medicationElements 약명", e.groups().get(0));
            if (e.label().equals("경로")) {
                hasRoute = true;
                requireRoute(p, "answerKey.medicationElements 경로", e.groups().get(0));
            }
        }
        if (!key.medicationElements().isEmpty() && !hasRoute) {
            p.add("answerKey.medicationElements에 경로(PO|경구)가 없음");
        }
        for (var e : app.scoring.ElementPattern.parseAll(key.observationElements())) {
            if (!e.label().equals("투약") || e.groups().isEmpty()) continue;
            // 견본 형식: 투약=한글약명|영문약명&용량&PO|경구 — 첫 묶음이 약명이다
            requireBoth(p, "answerKey.observationElements 투약 약명", e.groups().get(0));
            for (List<String> group : e.groups()) {
                if (group.stream().anyMatch(a -> a.equalsIgnoreCase("po") || a.equals("경구"))) {
                    requireRoute(p, "answerKey.observationElements 투약 경로", group);
                }
            }
        }
    }

    private static void requireBoth(List<String> p, String where, List<String> alts) {
        boolean ko = alts.stream().anyMatch(CaseValidator::hasHangul);
        boolean en = alts.stream().anyMatch(a -> a.matches("(?i).*[a-z]{3,}.*"));
        if (!ko || !en) p.add(where + "에 한글·영어 약명이 모두 있어야 함 (현재: " + String.join("|", alts) + ")");
    }

    private static void requireRoute(List<String> p, String where, List<String> alts) {
        boolean po = alts.stream().anyMatch(a -> a.equalsIgnoreCase("po"));
        boolean ko = alts.stream().anyMatch(a -> a.equals("경구"));
        if (!po || !ko) p.add(where + "에 PO와 경구가 모두 있어야 함 (예: PO|경구, 현재: " + String.join("|", alts) + ")");
    }

    private static boolean hasHangul(String s) {
        return s != null && s.codePoints().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** 정답 키 필수, 투약 정답(약명·용량·경로)이 현재 처방(정규·PRN) 중 하나와 같은지 확인한다. */
    private static void checkAnswerKey(List<String> p, PatientCase c) {
        var key = c.answerKey();
        if (key == null) {
            p.add("answerKey(정답 키) 누락");
            return;
        }
        if (key.observationElements().isEmpty()) p.add("answerKey.observationElements(관찰 핵심 사실) 누락");
        if (key.medicationElements().isEmpty()) {
            p.add("answerKey.medicationElements(투약 정답 값) 누락");
            return;
        }
        var elements = app.scoring.ElementPattern.parseAll(key.medicationElements());
        var drug = elements.stream().filter(e -> e.label().equals("약명")).findFirst();
        var dose = elements.stream().filter(e -> e.label().equals("용량")).findFirst();
        var route = elements.stream().filter(e -> e.label().equals("경로")).findFirst();
        if (drug.isEmpty() || dose.isEmpty()) {
            p.add("answerKey.medicationElements에 약명·용량이 없음");
            return;
        }
        boolean matched = c.orders().stream().anyMatch(m -> m != null && m.type() != OrderType.PAST
                && drug.get().foundIn(m.drugName()) && dose.get().equalsValue(m.dose())
                && (route.isEmpty() || route.get().equalsValue(m.route())));
        if (!matched) {
            p.add("answerKey 투약 정답(" + drug.get().expected() + " " + dose.get().expected()
                    + ")이 현재 처방(정규·PRN)의 약·용량과 다름");
        }
    }

    /** 통과하지 못하면 원인과 함께 {@link CaseLoadException}으로 거부한다. */
    public void validate(PatientCase c) {
        List<String> problems = check(c);
        if (!problems.isEmpty()) {
            throw new CaseLoadException("생성 케이스 검사 실패", problems);
        }
    }

    /** 처방 1건의 허용 약물·용량·형식 검사. */
    private void checkOrder(List<String> p, int i, Medication m) {
        String at = "orders[" + i + "]";
        for (String t : m.scheduleTimes()) {
            if (t == null || !t.matches(TIME)) {
                p.add(at + ".scheduleTimes 형식 오류 (HH:mm): " + t);
            }
        }
        var found = allowed.find(m.drugName());
        if (found.isEmpty()) {
            p.add(at + ".drugName 허용 약물 목록에 없음: " + m.drugName());
            return;
        }
        AllowedDrug d = found.get();
        if (m.type() == OrderType.PAST) {
            return; // 과거 처방은 약 이름만 검사한다 (경로·용량은 현재 처방이 아님)
        }
        if (!blank(d.route()) && !d.route().trim().equalsIgnoreCase(nz(m.route()).trim())) {
            p.add(at + ".route 허용 경로(" + d.route() + ")와 다름: " + nz(m.route()));
        }
        // 용량이 없는 PRN 처방은 용량·단위 검사를 건너뛴다. 과거 처방도 같은 목록으로 본다.
        if (m.type() == OrderType.PRN && blank(m.dose())) {
            return;
        }
        if (!blank(d.unit()) && !d.unit().trim().equalsIgnoreCase(nz(m.unit()).trim())) {
            p.add(at + ".unit 허용 단위(" + d.unit() + ")와 다름: " + nz(m.unit()));
        }
        Double dose = parseDose(m.dose());
        if (dose == null) {
            p.add(at + ".dose 숫자가 아님 또는 누락: " + nz(m.dose()));
            return;
        }
        if (d.minDose() != null && dose < d.minDose()) {
            p.add(at + ".dose " + m.dose() + "이(가) 허용 하한 " + d.minDose() + " 미만");
        }
        if (d.maxDose() != null && dose > d.maxDose()) {
            p.add(at + ".dose " + m.dose() + "이(가) 허용 상한 " + d.maxDose() + " 초과");
        }
        if (m.type() == OrderType.PRN) {
            if (!d.prnDoses().isEmpty() && d.prnDoses().stream().noneMatch(v -> Math.abs(v - dose) < 1e-9)) {
                p.add(at + ".dose PRN 1회 용량 " + m.dose() + "이(가) 허용 값 " + d.prnDoses() + "에 없음");
            }
            if (d.maxDailyDose() != null && dose > d.maxDailyDose()) {
                p.add(at + ".dose PRN 1회 용량 " + m.dose() + "이(가) 1일 상한 " + d.maxDailyDose() + " 초과");
            }
            return;
        }
        Integer times = timesPerDay(m);
        if (times == null) {
            if (d.minDailyDose() != null || d.maxDailyDose() != null || d.timesPerDay() != null) {
                p.add(at + " 1일 투여 횟수를 알 수 없음 (scheduleTimes 또는 frequency '1일 N회' 필요)");
            }
            return;
        }
        if (d.timesPerDay() != null && !d.timesPerDay().equals(times)) {
            p.add(at + " 1일 " + times + "회가 허용 분할 횟수(" + d.timesPerDay() + "회)와 다름");
        }
        double daily = dose * times;
        if (d.minDailyDose() != null && daily < d.minDailyDose()) {
            p.add(at + " 1일 총량 " + daily + "이(가) 허용 하한 " + d.minDailyDose() + " 미만");
        }
        if (d.maxDailyDose() != null && daily > d.maxDailyDose()) {
            p.add(at + " 1일 총량 " + daily + "이(가) 허용 상한 " + d.maxDailyDose() + " 초과");
        }
    }

    /** 정규 처방의 1일 횟수: 투여 시각 수, 없으면 frequency의 '1일 N회'. 알 수 없으면 null. */
    static Integer timesPerDay(Medication m) {
        if (!m.scheduleTimes().isEmpty()) return m.scheduleTimes().size();
        if (m.frequency() == null) return null;
        java.util.regex.Matcher x = java.util.regex.Pattern.compile("1\\s*일\\s*(\\d+)\\s*회").matcher(m.frequency());
        return x.find() ? Integer.valueOf(x.group(1)) : null;
    }

    private static Double parseDose(String s) {
        if (blank(s)) {
            return null;
        }
        try {
            return Double.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

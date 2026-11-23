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
        for (int i = 0; i < c.vitals().size(); i++) {
            VitalSign v = c.vitals().get(i);
            if (v == null || v.time() == null || !v.time().matches(TIME)) {
                p.add("vitals[" + i + "].time 형식 오류 (HH:mm)");
            }
        }
        return p;
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

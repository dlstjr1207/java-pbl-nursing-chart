package app.cases;

import app.domain.Medication;
import app.domain.OrderType;
import app.domain.PatientCase;
import app.domain.VitalSign;
import java.util.ArrayList;
import java.util.List;

/**
 * 생성된 케이스를 사용하기 전에 검사한다 (F-14, T-30).
 * 필수 항목·형식(시각 HH:mm, 용량 숫자)·허용 약물·용량 범위를 보고, 위반을 원인과 함께 모두 모아 돌려준다.
 * 허용 약물 목록이 비어 있으면 AI 생성을 쓸 수 없다({@link #generationAvailable()}).
 */
public class CaseValidator {

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
        for (int i = 0; i < c.orders().size(); i++) {
            Medication m = c.orders().get(i);
            if (m != null && !blank(m.drugName())) {
                checkOrder(p, i, m);
            }
        }
        for (int i = 0; i < c.vitals().size(); i++) {
            VitalSign v = c.vitals().get(i);
            if (v == null || v.time() == null || !v.time().matches(TIME)) {
                p.add("vitals[" + i + "].time 형식 오류 (HH:mm)");
            }
        }
        return p;
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

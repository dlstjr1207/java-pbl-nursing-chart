package app.ui;

import app.cases.AllowedDrug;
import app.cases.AllowedDrugs;
import app.domain.Medication;
import app.domain.OrderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 처방을 실제 EMR 형식 한 줄로 쓴다 (D38, B3). 예: "Aripiprazole 10mg 1T PO QD 08:30",
 * "Lithium carbonate 300mg 1T PO TID 08:30·12:30·18:30", "Lorazepam 1mg 1T PO PRN (불안·초조 시, 1일 최대 4mg)".
 * 정제 개수(1T)는 1회 용량이 근거표에서 확인한 허가 제품 함량(allowed-drugs.json의 tabletStrengths)과 같을 때만 붙인다.
 * 맞지 않으면 개수를 빼고 쓴다. 환자 정보·투약 점검·근거·출처 창이 모두 이 표기를 쓴다. AI 케이스도 같다.
 */
public final class OrderNotation {

    /** 1일 횟수 → 약어 */
    private static final Map<Integer, String> PER_DAY = Map.of(1, "QD", 2, "BID", 3, "TID", 4, "QID");
    private static final Pattern TIMES_A_DAY = Pattern.compile("1일\\s*(\\d)\\s*회");
    /** 약명 뒤의 한글 괄호 이름: "Lithium carbonate (탄산리튬)" → "Lithium carbonate" */
    private static final Pattern KOREAN_PAREN = Pattern.compile("\\s*\\([^()]*[가-힣][^()]*\\)\\s*$");

    private static AllowedDrugs drugs;

    private OrderNotation() {
    }

    /** 허가 제품 함량을 읽을 허용 약물 목록 (처음 쓸 때 한 번 읽는다) */
    private static synchronized AllowedDrugs drugs() {
        if (drugs == null) {
            try {
                drugs = AllowedDrugs.load();
            } catch (RuntimeException e) {
                drugs = new AllowedDrugs(List.of()); // 목록이 없으면 정제 개수만 빼고 쓴다
            }
        }
        return drugs;
    }

    /** 처방 1건의 EMR 표기. 개발용 표기는 숨긴다. */
    public static String format(Medication o) {
        if (o == null) return "";
        List<String> parts = new ArrayList<>();
        parts.add(displayName(o.drugName()));
        String dose = StudentText.value(o.dose());
        parts.add(dose + (o.unit() == null ? "" : o.unit()));
        tabletCount(o).ifPresent(parts::add);
        String route = StudentText.value(o.route());
        if (!route.equals(PatientInfoView.EMPTY)) parts.add(route);
        List<String> extra = new ArrayList<>();
        if (o.type() == OrderType.PRN) {
            parts.add("PRN");
            String ind = StudentText.value(o.prnIndication());
            if (!ind.equals(PatientInfoView.EMPTY)) extra.add(ind);
        } else {
            String f = frequencyCode(o.frequency());
            if (!f.isEmpty()) parts.add(f);
            if (!o.scheduleTimes().isEmpty()) parts.add(String.join("·", o.scheduleTimes()));
        }
        String note = StudentText.note(o.note());
        if (!note.isBlank()) extra.add(note);
        String line = String.join(" ", parts);
        return extra.isEmpty() ? line : line + " (" + String.join(", ", extra) + ")";
    }

    /** 약명에서 뒤의 한글 괄호 이름을 뺀다 (EMR 처방 줄은 영문 약명) */
    static String displayName(String name) {
        if (name == null) return "";
        return KOREAN_PAREN.matcher(name).replaceAll("").strip();
    }

    /** "1일 3회 (1일 900mg)" → "TID". 횟수를 못 읽으면 개발용 표기만 지운 원문, 없으면 빈 문자열 */
    static String frequencyCode(String frequency) {
        String f = StudentText.value(frequency);
        if (f.equals(PatientInfoView.EMPTY)) return "";
        Matcher m = TIMES_A_DAY.matcher(f);
        if (m.find()) {
            String code = PER_DAY.get(Integer.parseInt(m.group(1)));
            if (code != null) return code;
        }
        return f;
    }

    /** 1회 용량이 허가 제품 함량과 같으면 "1T", 아니면 비움 */
    static Optional<String> tabletCount(Medication o) {
        if (o.unit() == null || !o.unit().equalsIgnoreCase("mg")) return Optional.empty();
        double dose;
        try {
            dose = Double.parseDouble(o.dose().strip());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        Optional<AllowedDrug> d = drugs().find(o.drugName());
        if (d.isEmpty()) return Optional.empty();
        for (Double s : d.get().tabletStrengths()) {
            if (s != null && Math.abs(s - dose) < 1e-9) return Optional.of("1T");
        }
        return Optional.empty();
    }
}

package app.ui;

import app.domain.Medication;
import app.domain.MedicationRecord;
import app.domain.MedicationResult;
import app.domain.OrderType;
import app.domain.PatientCase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 투약 점검 화면의 로직(Swing 없음). 처방 선택지 제공, 조건별 필수값 검증, 학생 기록 저장을 맡는다 (S4, F-05).
 * 처방과 다른 용량이어도 저장은 허용한다(R7에서 지적). 여기서는 기록의 완결성만 본다.
 */
public class MedicationController {
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final AppState state;

    public MedicationController(AppState state) {
        this.state = state;
    }

    /** 입력 검증 오류. 입력 칸 이름(field)별 안내 문구. */
    public record Errors(Map<String, String> byField) {
        public boolean isEmpty() { return byField.isEmpty(); }
    }

    /** 저장 결과. 성공이면 errors가 비어 있다. */
    public record SaveResult(boolean saved, Errors errors) {}

    /** 선택 가능한 처방 목록. 과거 처방(PAST)은 투약 대상이 아니므로 제외한다. */
    public List<Medication> selectableOrders() {
        PatientCase c = state.currentCase();
        if (c == null) return List.of();
        return c.orders().stream().filter(o -> o.type() != OrderType.PAST).toList();
    }

    /** 사유가 필수인지: PRN 처방이거나 결과가 거부·보류인 경우 (R6). */
    public static boolean reasonRequired(Medication order, MedicationResult result) {
        boolean prn = order != null && order.type() == OrderType.PRN;
        return prn || result == MedicationResult.REFUSED || result == MedicationResult.HELD;
    }

    /** 처방 목록 한 줄 표시 문구. */
    public static String orderLabel(Medication o) {
        String kind = o.type() == OrderType.PRN ? "PRN" : "정규";
        return "[" + kind + "] " + o.drugName() + " " + o.dose() + o.unit();
    }

    /**
     * 선택한 처방을 학생이 읽을 한 줄로 (polish 8). 예: "Lorazepam 1mg PO PRN (불안·초조 시, 1일 최대 4mg)",
     * "Lithium carbonate 300mg PO 1일 3회 (08:30, 12:30, 18:30)". 개발용 표기는 숨긴다.
     */
    public static String orderSummary(Medication o) {
        if (o == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(o.drugName()).append(' ').append(StudentText.value(o.dose())).append(o.unit() == null ? "" : o.unit())
                .append(' ').append(StudentText.value(o.route()));
        java.util.List<String> extra = new java.util.ArrayList<>();
        if (o.type() == OrderType.PRN) {
            sb.append(" PRN");
            if (o.prnIndication() != null && !StudentText.value(o.prnIndication()).equals(PatientInfoView.EMPTY)) {
                extra.add(StudentText.value(o.prnIndication()));
            }
        } else {
            String f = StudentText.value(o.frequency());
            if (!f.equals(PatientInfoView.EMPTY)) sb.append(' ').append(f);
            if (!o.scheduleTimes().isEmpty()) extra.add(String.join(", ", o.scheduleTimes()));
        }
        String note = StudentText.note(o.note());
        if (!note.isBlank()) extra.add(note);
        if (!extra.isEmpty()) sb.append(" (").append(String.join(", ", extra)).append(')');
        return sb.toString();
    }

    /**
     * 투약 입력을 검증하고, 통과하면 학생 기록에 저장한다. 실패하면 저장하지 않으며 입력은 화면에 남긴다.
     * 입력값은 그대로 보존한다.
     */
    public SaveResult save(Medication order, String dose, String unit, String route, String time,
                           MedicationResult result, String reason, String orderBasis,
                           Boolean oralCheck, String reassessmentPlan) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (order == null) errors.put("order", "처방을 선택하세요.");
        if (blank(dose)) errors.put("dose", "용량을 입력하세요.");
        if (blank(unit)) errors.put("unit", "단위를 입력하세요.");
        if (blank(route)) errors.put("route", "경로를 입력하세요.");
        String t = time == null ? "" : time.trim();
        if (t.isEmpty()) errors.put("time", "투약 시각을 입력하세요. (예: 14:30)");
        else if (!TIME.matcher(t).matches()) errors.put("time", "시각은 HH:mm 형식으로 입력하세요. (예: 14:30)");
        if (result == null) errors.put("result", "투약 결과(투여·거부·보류)를 선택하세요.");
        if (blank(orderBasis)) errors.put("orderBasis", "처방 근거를 입력하세요.");
        if (order != null && result != null && reasonRequired(order, result) && blank(reason)) {
            errors.put("reason", "PRN·거부·보류는 사유를 입력해야 합니다.");
        }
        if (!errors.isEmpty()) return new SaveResult(false, new Errors(errors));

        state.addMedication(new MedicationRecord(order.orderId(), order.drugName(), dose, unit, route, t,
                result, blank(reason) ? null : reason, orderBasis, oralCheck,
                blank(reassessmentPlan) ? null : reassessmentPlan));
        return new SaveResult(true, new Errors(Map.of()));
    }

    /** 저장된 학생 투약 기록의 목록 표시 문구. */
    public List<String> recordLines() {
        return state.studentMedications().stream().map(r ->
                "[학생] " + r.administeredTime() + " " + r.drugName() + " " + r.dose() + r.unit()
                        + " " + r.route() + " · " + resultLabel(r.result())).toList();
    }

    /** 투약 결과의 한국어 이름. */
    public static String resultLabel(MedicationResult r) {
        return switch (r) {
            case GIVEN -> "투여";
            case REFUSED -> "거부";
            case HELD -> "보류";
        };
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

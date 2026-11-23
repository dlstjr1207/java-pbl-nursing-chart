package app.scoring;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.Medication;
import app.domain.MedicationRecord;
import app.domain.MedicationResult;
import app.domain.OrderType;
import java.util.ArrayList;
import java.util.List;

/**
 * R6 투약 기록 완결성 점검. 각 기록의 약명·용량·경로·시각·처방 근거가 있는지,
 * PRN 처방이거나 결과가 거부·보류이면 사유가 있는지 본다.
 * 값이 처방과 맞는지는 보지 않는다(R7 범위 밖). 점수는 가중치 미정이라 null이다.
 * 기록이 0건인 경우는 R8이 알리므로 여기서는 결과를 만들지 않는다.
 */
public final class MedicationCompletenessChecker {
    public static final String RULE = "R6";

    private MedicationCompletenessChecker() {}

    /** 기록별로 항목 결과(통과·누락)를 돌려준다. 입력 위치는 "medication[n].필드". orders는 PRN 판정용(null 허용). */
    public static List<CheckResult> check(List<MedicationRecord> records, List<Medication> orders) {
        List<CheckResult> out = new ArrayList<>();
        if (records == null) return out;
        for (int i = 0; i < records.size(); i++) {
            MedicationRecord r = records.get(i);
            String p = "medication[" + (i + 1) + "].";
            String label = "투약 " + (i + 1) + ": ";
            out.add(item(p + "drugName", !blank(r.drugName()), label + "약명", ""));
            out.add(item(p + "dose", !blank(r.dose()), label + "용량", ""));
            out.add(item(p + "route", !blank(r.route()), label + "경로", ""));
            out.add(item(p + "administeredTime", !blank(r.administeredTime()), label + "시각", ""));
            out.add(item(p + "orderBasis", !blank(r.orderBasis()), label + "처방 근거", ""));
            out.add(item(p + "result", r.result() != null, label + "결과(투여·거부·보류)", ""));
            if (reasonRequired(r, orders)) {
                out.add(item(p + "reason", !blank(r.reason()), label + "사유", "PRN·거부·보류는 사유가 필수입니다."));
            }
        }
        return out;
    }

    /** 사유 필수 여부: 참조한 처방이 PRN이거나 결과가 거부·보류. */
    static boolean reasonRequired(MedicationRecord r, List<Medication> orders) {
        if (r.result() == MedicationResult.REFUSED || r.result() == MedicationResult.HELD) return true;
        if (orders == null || r.orderId() == null) return false;
        return orders.stream().anyMatch(o -> r.orderId().equals(o.orderId()) && o.type() == OrderType.PRN);
    }

    private static CheckResult item(String ref, boolean ok, String name, String hint) {
        if (ok) return new CheckResult(RULE, CheckTarget.MEDICATION, ref, CheckStatus.PASS, name + "이(가) 있습니다.", null);
        String msg = name + "을(를) 입력하세요." + (hint.isEmpty() ? "" : " " + hint);
        return new CheckResult(RULE, CheckTarget.MEDICATION, ref, CheckStatus.MISSING, msg, null);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

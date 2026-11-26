package app.cases;

import java.util.List;

/**
 * 허용 약물 목록(allowed-drugs.json)의 항목 1건 (D26·D29). 값은 근거표에서 확인한 허가 용법이다.
 * AI 생성 케이스의 처방이 이 범위를 벗어나면 사용하지 않는다. 비어 있는 칸은 검사하지 않는다.
 *
 * @param name         약명 (영문). 처방의 drugName에 이 이름이나 별칭이 들어 있으면 같은 약으로 본다
 * @param aliases      별칭 (예: 한글 이름)
 * @param unit         용량 단위 (예: mg)
 * @param route        경로 (예: PO)
 * @param minDose      1회 용량 하한
 * @param maxDose      1회 용량 상한
 * @param minDailyDose 1일 총량 하한 (정규 처방: 1회 용량 × 1일 횟수)
 * @param maxDailyDose 1일 총량 상한 (PRN은 1회 용량이 이 값을 넘지 않아야 한다)
 * @param timesPerDay  정규 처방의 1일 횟수 (예: 리튬 3회 분할). 비어 있으면 횟수는 검사하지 않는다
 * @param prnDoses     PRN 처방에서 허용하는 1회 용량 목록
 * @param evidenceId   근거표 ID (예: E-20)
 * @param note         비고 (예: 허가 외 사용 설명)
 */
public record AllowedDrug(
        String name,
        List<String> aliases,
        String unit,
        String route,
        Double minDose,
        Double maxDose,
        Double minDailyDose,
        Double maxDailyDose,
        Integer timesPerDay,
        List<Double> prnDoses,
        String evidenceId,
        String note) {

    public AllowedDrug {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        prnDoses = prnDoses == null ? List.of() : List.copyOf(prnDoses);
    }

    /** 처방 약명이 이 약인지 (이름·별칭이 들어 있는지, 대소문자·띄어쓰기 무시). */
    public boolean matches(String drugName) {
        if (drugName == null) return false;
        String target = norm(drugName);
        if (norm(name).length() > 0 && target.contains(norm(name))) return true;
        for (String a : aliases) {
            if (norm(a).length() > 0 && target.contains(norm(a))) return true;
        }
        return false;
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }
}

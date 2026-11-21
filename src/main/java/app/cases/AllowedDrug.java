package app.cases;

/**
 * 허용 약물 목록(allowed-drugs.json)의 항목 1건 (D26). 값은 팀이 근거표를 보고 직접 채운다.
 *
 * @param name       약명 (케이스 처방의 drugName과 대소문자 무시로 비교)
 * @param unit       용량 단위 (예: mg)
 * @param route      경로 (예: PO). 비어 있으면 경로는 검사하지 않는다
 * @param minDose    1회 용량 하한 (비어 있으면 하한 검사 안 함)
 * @param maxDose    1회 용량 상한 (비어 있으면 상한 검사 안 함)
 * @param evidenceId 근거표 ID (예: E-20)
 */
public record AllowedDrug(
        String name,
        String unit,
        String route,
        Double minDose,
        Double maxDose,
        String evidenceId) {
}

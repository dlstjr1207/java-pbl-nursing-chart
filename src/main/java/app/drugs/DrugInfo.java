package app.drugs;

/**
 * 약 한 가지의 식약처 허가정보 요약 (단계 7). 투약 화면에서 참고용으로만 보여 주고 채점에는 쓰지 않는다.
 * 채점 기준값은 근거표에서 확인한 allowed-drugs.json·PC-01 값을 그대로 쓴다.
 *
 * @param drug       허용 약물 이름 (allowed-drugs.json의 name, 예: Aripiprazole)
 * @param itemSeq    품목기준코드 (대표 제품)
 * @param itemName   제품명
 * @param company    업체명
 * @param permitDate 허가일 (yyyyMMdd)
 * @param changeDate 변경일 (yyyyMMdd, 없으면 빈 문자열)
 * @param efficacy   효능효과 (줄바꿈으로 구분한 평문)
 * @param dosage     용법용량 (줄바꿈으로 구분한 평문)
 */
public record DrugInfo(String drug, String itemSeq, String itemName, String company, String permitDate,
                       String changeDate, String efficacy, String dosage) {
}

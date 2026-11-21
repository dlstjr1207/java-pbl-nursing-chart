package app.domain;

import java.util.List;

/**
 * 케이스의 처방 1건 (orders). 학생 투약 기록은 orderId로 이 처방을 참조한다.
 * 용량은 미확정 값([확인필요])을 그대로 둘 수 있도록 문자열로 보관한다.
 *
 * @param orderId       처방 식별자 (정규·PRN 구분이 가능해야 함)
 * @param type          정규·PRN·과거 처방
 * @param drugName      약명
 * @param dose          1회 용량
 * @param unit          단위
 * @param route         경로
 * @param frequency     횟수·간격 표기
 * @param scheduleTimes 정규 투약 시각 목록 (HH:mm)
 * @param prnIndication PRN 적응 상황 (정규 처방이면 null)
 * @param note          비고
 */
public record Medication(
        String orderId,
        OrderType type,
        String drugName,
        String dose,
        String unit,
        String route,
        String frequency,
        List<String> scheduleTimes,
        String prnIndication,
        String note) {

    public Medication {
        scheduleTimes = Lists.copy(scheduleTimes);
    }
}

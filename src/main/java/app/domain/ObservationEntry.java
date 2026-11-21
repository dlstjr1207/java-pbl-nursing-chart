package app.domain;

import java.util.List;

/**
 * 관찰 기록 1건 (F-04). 케이스의 기존 관찰 로그(observationLog)와 학생 기록(Attempt)이 같은 형식을 쓰고,
 * 어느 쪽 목록에 들어 있는지로 출처를 구분한다.
 *
 * @param time         관찰 시각 (HH:mm)
 * @param shift        근무
 * @param tags         관찰 분류 태그 (학생 입력은 1개 이상)
 * @param narrative    서술 (입력 그대로 보존)
 * @param intervention 시행한 처치 (없으면 null)
 * @param vitals       선택적 활력징후 (없으면 null)
 */
public record ObservationEntry(
        String time,
        Shift shift,
        List<String> tags,
        String narrative,
        String intervention,
        VitalSign vitals) {

    public ObservationEntry {
        tags = Lists.copy(tags);
    }
}

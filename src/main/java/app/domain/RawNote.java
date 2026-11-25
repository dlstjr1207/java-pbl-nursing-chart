package app.domain;

import java.util.List;

/**
 * 원자료 메모 1개. 학생이 근무 중에 적어 둔 듯한 짧은 메모로, 학생은 여러 메모에서 필요한 사실만 골라 기록한다.
 * 메모는 시간 순서가 일부 섞인 채 그대로 보여 준다. irrelevant·cues는 학생에게 보이지 않고 점검에만 쓴다.
 *
 * @param time       메모 시각 (HH:mm)
 * @param text       메모 내용 (환자 말은 정리하지 않은 원문 그대로 따옴표 안에)
 * @param irrelevant 이번 과제와 관련 없는 메모인지 (식사 배식, 보호자 전화, 다른 업무 등)
 * @param cues       관련 없는 메모를 학생 기록에서 알아볼 낱말 (예: "점심", "원무과")
 */
public record RawNote(String time, String text, boolean irrelevant, List<String> cues) {

    public RawNote {
        cues = Lists.copy(cues);
    }

    /** 관련 있는 메모 (cues 없음). */
    public static RawNote of(String time, String text) {
        return new RawNote(time, text, false, List.of());
    }
}

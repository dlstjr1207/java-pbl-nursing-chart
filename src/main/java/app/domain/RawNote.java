package app.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 원자료 메모 1개. 학생이 근무 중에 적어 둔 듯한 짧은 메모로, 학생은 여러 메모에서 필요한 사실만 골라 기록한다.
 * 메모는 시간 순서가 일부 섞인 채 그대로 보여 준다. irrelevant·cues·kind는 학생에게 보이지 않고 점검에만 쓴다.
 *
 * @param time       메모 시각 (HH:mm)
 * @param text       메모 내용 (환자 말은 정리하지 않은 원문 그대로 따옴표 안에)
 * @param irrelevant 이번 과제와 관련 없는 메모인지 (식사 배식, 보호자 전화, 다른 업무 등)
 * @param cues       관련 없는 메모를 학생 기록에서 알아볼 낱말 (예: "점심", "원무과")
 * @param kind       메모 종류 (아래 상수 중 하나, 예전 케이스는 null). 사건 흐름의 시각 순서를 검사할 때 쓴다
 */
public record RawNote(String time, String text, boolean irrelevant, List<String> cues, String kind) {

    /** 메모 종류: 인계 */
    public static final String HANDOFF = "HANDOFF";
    /** 사건 관찰 (장소·행동) */
    public static final String EVENT = "EVENT";
    /** 환자 말 */
    public static final String SPEECH = "SPEECH";
    /** 간호 중재 (말 걸기 뒤 1:1·조용한 곳 등) */
    public static final String INTERVENTION = "INTERVENTION";
    /** PRN 투약 */
    public static final String PRN = "PRN";
    /** 재평가 */
    public static final String REASSESS = "REASSESS";
    /** 활력징후 */
    public static final String VITALS = "VITALS";
    /** 다음 근무 전달 */
    public static final String NEXT_SHIFT = "NEXT_SHIFT";
    /** 관련 없음 */
    public static final String IRRELEVANT = "IRRELEVANT";

    /** 종류 코드 → 한국어 이름 (문서·검사 메시지용) */
    public static final Map<String, String> KIND_NAMES = Map.of(
            HANDOFF, "인계", EVENT, "사건 관찰", SPEECH, "환자 말", INTERVENTION, "간호 중재", PRN, "PRN 투약",
            REASSESS, "재평가", VITALS, "활력징후", NEXT_SHIFT, "다음 근무 전달", IRRELEVANT, "관련 없음");

    public RawNote {
        cues = Lists.copy(cues);
    }

    /** 종류 없이 만든다 (예전 형식·테스트용). */
    public RawNote(String time, String text, boolean irrelevant, List<String> cues) {
        this(time, text, irrelevant, cues, null);
    }

    /** 관련 있는 메모 (cues 없음). */
    public static RawNote of(String time, String text) {
        return new RawNote(time, text, false, List.of());
    }

    /** 같은 메모에 종류만 바꾼다. */
    public RawNote withKind(String k) {
        return new RawNote(time, text, irrelevant, cues, k);
    }

    /**
     * 종류 표기를 코드로 맞춘다: 대소문자·공백·하이픈을 정리하고 한국어 이름("사건 관찰")도 받는다. 모르는 값은 그대로(대문자).
     */
    public static String normalizeKind(String k) {
        if (k == null || k.isBlank()) return null;
        String s = k.trim();
        for (Map.Entry<String, String> e : KIND_NAMES.entrySet()) {
            if (e.getValue().replace(" ", "").equals(s.replace(" ", ""))) return e.getKey();
        }
        return s.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}

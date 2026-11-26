package app.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 정신상태 평가(MSE) 기록 (D38, B4). 의식 수준은 5단계에서 고르고, 나머지는 짧게 적는다.
 * 점수에는 넣지 않고, 비어 있으면 "정신상태 평가를 기록하세요"로 피드백만 준다. 정답 키와 비교하지 않는다.
 *
 * @param consciousness 의식 수준 (CONSCIOUSNESS 중 하나, 고르지 않았으면 null)
 * @param appearance    외모·행동
 * @param mood          기분·정동
 * @param thought       사고 내용
 * @param perception    지각 (환청·환시)
 * @param orientation   지남력
 */
public record MentalStatus(String consciousness, String appearance, String mood, String thought,
                           String perception, String orientation) {

    /** 의식 수준 5단계 */
    public static final List<String> CONSCIOUSNESS = List.of(
            "명료(alert)", "기면(drowsy)", "혼미(stupor)", "반혼수(semicoma)", "혼수(coma)");

    /** 항목 이름 (화면·결과 표시 순서) */
    public static final List<String> LABELS = List.of("의식 수준", "외모·행동", "기분·정동", "사고 내용", "지각(환청·환시)", "지남력");

    /** 앞뒤 공백을 지우고 빈 칸은 null로 맞춘다 */
    public MentalStatus {
        consciousness = clean(consciousness);
        appearance = clean(appearance);
        mood = clean(mood);
        thought = clean(thought);
        perception = clean(perception);
        orientation = clean(orientation);
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** 값을 LABELS 순서로 */
    public List<String> values() {
        List<String> v = new ArrayList<>();
        v.add(consciousness);
        v.add(appearance);
        v.add(mood);
        v.add(thought);
        v.add(perception);
        v.add(orientation);
        return v;
    }

    /** 하나도 적지 않았는지 */
    public boolean isEmpty() {
        return values().stream().allMatch(x -> x == null);
    }

    /** 비어 있으면(또는 null이면) true */
    public static boolean blank(MentalStatus m) {
        return m == null || m.isEmpty();
    }

    /** 결과·내보내기 표시 줄: "의식 수준: 명료(alert)" 등. 적지 않은 칸은 "-" */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        List<String> v = values();
        for (int i = 0; i < LABELS.size(); i++) out.add(LABELS.get(i) + ": " + (v.get(i) == null ? "-" : v.get(i)));
        return out;
    }
}

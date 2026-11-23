package app.scoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 정답 키 요소 한 개 (예: {@code 투약=로라제팜|lorazepam&1mg|1 mg&po|경구}).
 * '=' 앞은 이름, 뒤는 조건이다. '&'로 나뉜 묶음은 모두 있어야 하고, 묶음 안의 '|' 동의어는 하나만 있으면 된다.
 * 비교는 대소문자와 띄어쓰기를 무시한다(예: "1 mg"과 "1mg"은 같다).
 */
public final class ElementPattern {
    private final String label;
    private final List<List<String>> groups;

    private ElementPattern(String label, List<List<String>> groups) {
        this.label = label;
        this.groups = groups;
    }

    /** 요소 문자열을 읽는다. '='가 없으면 조건 전체를 이름으로 쓴다. */
    public static ElementPattern parse(String element) {
        String text = element == null ? "" : element.trim();
        int eq = text.indexOf('=');
        String label = eq > 0 ? text.substring(0, eq).trim() : null;
        String expr = eq > 0 ? text.substring(eq + 1) : text;
        List<List<String>> groups = new ArrayList<>();
        for (String g : expr.split("&")) {
            List<String> alts = new ArrayList<>();
            for (String a : g.split("\\|")) {
                if (!a.isBlank()) alts.add(a.trim());
            }
            if (!alts.isEmpty()) groups.add(alts);
        }
        if (label == null) label = display(groups);
        return new ElementPattern(label, groups);
    }

    /** 요소 목록을 한꺼번에 읽는다. */
    public static List<ElementPattern> parseAll(List<String> elements) {
        if (elements == null) return List.of();
        return elements.stream().filter(e -> e != null && !e.isBlank()).map(ElementPattern::parse).toList();
    }

    /** 사람이 읽는 이름 (예: 장소). */
    public String label() { return label; }

    /** 대표 표현 (묶음마다 첫 동의어). 안내 문구에 쓴다. */
    public String example() { return display(groups); }

    /** 글에 모든 묶음이 들어 있는지 (포함 여부). */
    public boolean foundIn(String text) {
        if (groups.isEmpty() || text == null) return false;
        String t = norm(text);
        for (List<String> alts : groups) {
            if (alts.stream().noneMatch(a -> t.contains(norm(a)))) return false;
        }
        return true;
    }

    /** 값이 동의어 중 하나와 같은지 (정확히 일치, 숫자는 1과 1.0을 같게 본다). 첫 묶음만 쓴다. */
    public boolean equalsValue(String value) {
        if (groups.isEmpty() || value == null) return false;
        String v = norm(value);
        for (String a : groups.get(0)) {
            String n = norm(a);
            if (n.equals(v) || sameNumber(n, v)) return true;
        }
        return false;
    }

    /** 첫 동의어 (기준값 안내용). */
    public String expected() { return groups.isEmpty() ? "" : groups.get(0).get(0); }

    static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }

    private static boolean sameNumber(String a, String b) {
        try {
            return Double.parseDouble(a) == Double.parseDouble(b);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String display(List<List<String>> groups) {
        return String.join(" ", groups.stream().map(g -> g.get(0)).toArray(String[]::new));
    }

    @Override
    public String toString() {
        return label + "=" + Arrays.toString(groups.toArray());
    }
}

package app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 학생 화면에 보일 문장에서 개발·검토용 표기를 걷어 낸다 (polish A).
 * 근거 번호(E-20 등), [검증필요]·[확인필요], 결정 번호(D29 등)는 학생에게 보이지 않고
 * "파일 → 근거·출처 보기" 창(EvidenceDialog)에서만 원문 그대로 보인다. 케이스 데이터 자체는 바꾸지 않는다.
 */
public final class StudentText {

    /** 개발용 표기가 들어 있는지 판단하는 패턴 */
    private static final Pattern MARK = Pattern.compile("\\bE-\\d+\\b|\\[(검증|확인)필요[^\\]]*\\]|\\bD\\d{2}\\b");
    /** 개발용 표기가 든 괄호 덩어리: "(E-28)" 등 */
    private static final Pattern MARKED_PAREN = Pattern.compile("\\s*\\([^()]*(E-\\d+|\\[(검증|확인)필요|\\bD\\d{2}\\b)[^()]*\\)");
    /** 남은 [검증필요 ...]·[확인필요] 꼬리표 */
    private static final Pattern TAG = Pattern.compile("\\s*\\[(검증|확인)필요[^\\]]*\\]");

    private StudentText() {
    }

    /**
     * 비고·설명 문장을 학생용으로 정리한다. 개발용 표기가 든 문장은 통째로 빼고, 괄호 속 근거 번호만 지운다.
     * 예: "1일 최대 4mg. E-29. PRN 불안·초조 적응증 [검증필요]" → "1일 최대 4mg"
     */
    public static String note(String s) {
        if (s == null) return "";
        String t = MARKED_PAREN.matcher(s).replaceAll("");
        List<String> kept = new ArrayList<>();
        for (String sentence : t.split("(?<=\\.)\\s+|\\.$")) {
            String x = sentence.strip();
            if (x.endsWith(".")) x = x.substring(0, x.length() - 1).strip();
            if (x.isEmpty() || MARK.matcher(x).find()) continue;
            kept.add(x);
        }
        return String.join(". ", kept);
    }

    /** 값 칸(경로·횟수·시각 등)이 개발용 표기뿐이면 "-"로, 아니면 꼬리표만 지워 돌려준다. */
    public static String value(String s) {
        if (s == null || s.isBlank()) return PatientInfoView.EMPTY;
        String t = TAG.matcher(s).replaceAll("").strip();
        return t.isEmpty() ? PatientInfoView.EMPTY : t;
    }

    /** 점검 결과 문구: "[검증필요 교육용 예시, 배점 없음]" 같은 꼬리표를 지운다. */
    public static String message(String s) {
        if (s == null) return "";
        return TAG.matcher(s).replaceAll("").strip();
    }

    /** 개발용 표기가 남아 있는지 (테스트·확인용). */
    public static boolean hasMarks(String s) {
        return s != null && MARK.matcher(s).find();
    }
}

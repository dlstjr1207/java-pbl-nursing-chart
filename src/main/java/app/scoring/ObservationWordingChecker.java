package app.scoring;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.ObservationEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * R1 환자 발언과 관찰 구분. 관찰 서술에 환자 발언을 따옴표로 인용했는지 점검한다(배점 대상).
 * 판단 표현 목록이 있으면 "관찰한 행동으로 바꿔 쓰세요"로 피드백만 준다(배점 없음, E-04 등급 C라 [검증필요] 교육용 예시).
 */
public final class ObservationWordingChecker {
    public static final String RULE = "R1";

    /** 판단 표현 예시 목록 (기획안 '점검 기준', 시나리오 V1). */
    public static final List<String> JUDGMENT_WORDS = List.of("협조적", "비협조적", "이상한", "정상적", "잘 지냄", "문제없음", "불안해 보임");

    /** 따옴표 쌍: "…", “…”, '…', ‘…’, 「…」 */
    private static final Pattern QUOTE = Pattern.compile("\"[^\"]+\"|“[^”]+”|'[^']+'|‘[^’]+’|「[^」]+」");

    private ObservationWordingChecker() {
    }

    public static List<CheckResult> check(List<ObservationEntry> observations) {
        List<CheckResult> out = new ArrayList<>();
        if (observations == null || observations.isEmpty()) {
            out.add(new CheckResult(RULE, CheckTarget.OBSERVATION, "observation", CheckStatus.MISSING,
                    "관찰 기록이 없어 환자 발언 인용을 확인할 수 없습니다.", null));
            return out;
        }
        for (int i = 0; i < observations.size(); i++) {
            String text = observations.get(i).narrative() == null ? "" : observations.get(i).narrative();
            String ref = "observation[" + (i + 1) + "].narrative";
            boolean quoted = QUOTE.matcher(text).find();
            out.add(new CheckResult(RULE, CheckTarget.OBSERVATION, ref, quoted ? CheckStatus.PASS : CheckStatus.MISSING,
                    quoted ? "환자 발언을 따옴표로 인용했습니다."
                           : "환자 발언은 따옴표(\" \")로 그대로 인용하세요.", null));
            String compact = text.replaceAll("\\s+", "");
            for (String w : JUDGMENT_WORDS) {
                if (containsWord(compact, w.replaceAll("\\s+", ""))) {
                    out.add(new CheckResult(RULE, CheckTarget.OBSERVATION, ref, CheckStatus.FEEDBACK,
                            "'" + w + "'은(는) 판단 표현입니다. 관찰한 행동으로 바꿔 쓰세요. [검증필요 교육용 예시, 배점 없음]", null));
                }
            }
        }
        return out;
    }

    /** '협조적'이 '비협조적' 안에서 다시 잡히지 않도록, 더 긴 판단 표현에 포함된 경우는 건너뛴다. */
    private static boolean containsWord(String text, String word) {
        int from = 0;
        while (true) {
            int at = text.indexOf(word, from);
            if (at < 0) return false;
            boolean partOfLonger = word.equals("협조적") && at > 0 && text.charAt(at - 1) == '비';
            if (!partOfLonger) return true;
            from = at + 1;
        }
    }
}

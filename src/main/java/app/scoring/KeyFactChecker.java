package app.scoring;

import app.domain.AnswerKey;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.ObservationEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * R2 핵심 사실. 정답 키의 요소(장소·행동·발언 인용·간호·투약·재평가)가 학생 관찰 기록(서술·처치)에 있는지
 * 요소별 동의어로 확인한다. 정답 키가 없으면 미산출이다.
 */
public final class KeyFactChecker {
    public static final String RULE = "R2";

    private KeyFactChecker() {
    }

    public static List<CheckResult> check(List<ObservationEntry> observations, AnswerKey key) {
        List<ElementPattern> facts = key == null ? List.of() : ElementPattern.parseAll(key.observationElements());
        if (facts.isEmpty()) {
            return List.of(new CheckResult(RULE, CheckTarget.OBSERVATION, "observation", CheckStatus.NOT_SCORED,
                    "이 케이스에는 핵심 사실 정답 키가 없어 미산출입니다.", null));
        }
        StringBuilder text = new StringBuilder();
        if (observations != null) {
            for (ObservationEntry o : observations) {
                text.append(o.narrative() == null ? "" : o.narrative()).append('\n');
                text.append(o.intervention() == null ? "" : o.intervention()).append('\n');
            }
        }
        List<CheckResult> out = new ArrayList<>();
        for (ElementPattern f : facts) {
            boolean ok = f.foundIn(text.toString());
            out.add(new CheckResult(RULE, CheckTarget.OBSERVATION, "observation.fact[" + f.label() + "]",
                    ok ? CheckStatus.PASS : CheckStatus.MISSING,
                    ok ? "관찰 기록에 " + f.label() + "이(가) 있습니다."
                       : "관찰 기록에 " + f.label() + "을(를) 적으세요. (예: " + f.example() + ")", null));
        }
        return out;
    }
}

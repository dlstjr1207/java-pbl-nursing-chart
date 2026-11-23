package app.domain;

import java.util.List;

/**
 * AI 해설 (F-10). 점수에 반영하지 않는다. 실패하면 status만 남기고 내용은 비운다.
 *
 * @param status         NONE, SUCCESS, FAILED 등 (attempts.ai_feedback_status)
 * @param strengths      잘한 점
 * @param improvements   고칠 점
 * @param exampleRewrite 고쳐 쓴 예시
 */
public record AiFeedback(
        String status,
        List<String> strengths,
        List<String> improvements,
        String exampleRewrite) {

    public AiFeedback {
        strengths = Lists.copy(strengths);
        improvements = Lists.copy(improvements);
    }
}

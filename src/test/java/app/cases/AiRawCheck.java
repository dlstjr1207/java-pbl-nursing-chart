package app.cases;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.JdkHttpTransport;
import java.util.List;

/** 실제 AI의 케이스 생성 응답 원문 앞부분과 읽기 오류 원인을 보여 주는 점검 도구 (개발용). */
public final class AiRawCheck {
    public static void main(String[] args) throws Exception {
        AiClient ai = new AiClient(AiConfig.load(AiConfig.DEFAULT_PATH), new JdkHttpTransport());
        CaseGenerator gen = new CaseGenerator(ai, new CaseValidator(AllowedDrugs.load()), new CaseLoader(), 1);
        String content = ai.complete(gen.systemPrompt(), gen.userPrompt(List.of()));
        System.out.println("응답 길이=" + content.length());
        System.out.println("앞 300자: " + content.substring(0, Math.min(300, content.length())).replaceAll("\\s+", " "));
        System.out.println("뒤 200자: " + content.substring(Math.max(0, content.length() - 200)).replaceAll("\\s+", " "));
        try {
            gen.parse(content);
            System.out.println("읽기 성공");
        } catch (RuntimeException e) {
            Throwable t = e;
            while (t.getCause() != null) t = t.getCause();
            System.out.println("읽기 실패: " + e.getClass().getSimpleName() + " / " + t.getMessage());
        }
    }
}

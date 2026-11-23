package app.cases;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.JdkHttpTransport;

/** 실제 AI로 케이스를 n번 생성해 성공 여부와 마지막 위반 목록을 보여 주는 점검 도구 (개발용, 키는 출력하지 않음). */
public final class AiGenProblems {
    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        AiClient ai = new AiClient(AiConfig.load(AiConfig.DEFAULT_PATH), new JdkHttpTransport());
        CaseGenerator gen = new CaseGenerator(ai, new CaseValidator(AllowedDrugs.load()), new CaseLoader(), args.length > 1 ? Integer.parseInt(args[1]) : 1);
        for (int i = 1; i <= n; i++) {
            CaseGenerator.Result r = gen.generate();
            System.out.println("#" + i + " generated=" + r.generated() + " id=" + r.patientCase().id());
            r.problems().forEach(p -> System.out.println("   - " + p));
        }
    }
}

package app.ui;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.AiTransport;
import app.ai.JdkHttpTransport;
import app.domain.AiFeedback;
import app.domain.Attempt;
import app.storage.Database;
import app.storage.DbConfig;
import app.storage.MySqlAttemptRepository;
import app.storage.MySqlUserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 진단 도구 (개발용, 프로그램에 포함되지 않음): demo01의 저장된 기록으로 실제 AI 해설 요청을 여러 번 다시 보내
 * 해설이 저장 가능한 형식으로 오는지(재요청 포함), 내용이 어떤지 본다. 키·접속 정보는 출력하지 않는다.
 * 실행: java -cp target/nursing-chart-trainer.jar:target/test-classes app.ui.AiExplainDiag 출력파일 반복수 기록번호...
 */
public final class AiExplainDiag {
    public static void main(String[] args) throws Exception {
        Path outFile = Path.of(args[0]);
        int n = Integer.parseInt(args[1]);
        Database db = Database.forApp(DbConfig.load(Path.of("config", "db.properties")));
        long uid = new MySqlUserRepository(db).findByLoginId("demo01").orElseThrow().id();
        List<String> out = new ArrayList<>();
        JdkHttpTransport real = new JdkHttpTransport();
        for (int k = 2; k < args.length; k++) {
            Attempt a = new MySqlAttemptRepository(db).findById(uid, Long.parseLong(args[k])).orElseThrow();
            String prompt = SbarController.explainPrompt(ResultView.caseOf(a), a, a.sbar(), a.checks());
            for (int i = 1; i <= n; i++) {
                int[] calls = {0};
                AiTransport counting = (url, key, body) -> {
                    calls[0]++;
                    return real.post(url, key, body);
                };
                AiClient ai = new AiClient(AiConfig.load(Path.of("config", "ai.properties")), counting);
                long t = System.currentTimeMillis();
                AiFeedback f = ai.explain(prompt);
                String head = "## 기록 " + a.attemptId() + " (" + a.caseId() + ") " + i + "회: " + f.status() + ", 요청 " + calls[0] + "번, "
                        + (System.currentTimeMillis() - t) / 1000 + "초";
                System.out.println(head);
                out.add(head);
                out.add(ResultView.aiText(f));
            }
        }
        Files.write(outFile, out);
    }
}

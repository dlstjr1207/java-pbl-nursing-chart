package app.tools;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.AiConnection;
import app.ai.AiServerSettings;
import app.ai.JdkHttpTransport;
import app.cases.AllowedDrugs;
import app.cases.CaseGenerator;
import app.cases.CaseLoader;
import app.cases.CaseValidator;
import app.domain.Attempt;
import app.domain.ModelAnswer;
import app.domain.PatientCase;
import app.storage.InMemoryAttemptRepository;
import app.ui.AppState;
import app.ui.HomeController;
import app.ui.SbarController;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AI 중계 서버를 실제로 거쳐 프로그램의 AI 기능이 되는지 보는 점검 도구 (개발용, 프로그램에 포함 안 됨).
 * config/가 없는 폴더에서 실행해 키 없이 주소만으로 동작하는지 본다. Main과 같은 순서로 조립한다:
 * 주소 저장 → 연결 확인 → AI 케이스 생성 → 모범 기록으로 제출 → AI 해설.
 * 사용: RelayE2ECheck &lt;AI 서버 주소&gt; &lt;설정 저장 폴더&gt;
 */
public final class RelayE2ECheck {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        Path dataDir = Path.of(args[1]);
        System.out.println("현재 폴더: " + Path.of("").toAbsolutePath() + ", config/ai.properties 있음: "
                + Files.exists(AiConfig.DEFAULT_PATH));
        JdkHttpTransport transport = new JdkHttpTransport();
        AiClient ai = new AiClient(AiConfig.disabled(), transport);
        AiConnection c = new AiConnection(ai, AiConfig.load(AiConfig.DEFAULT_PATH),
                new AiServerSettings(dataDir.resolve(AiServerSettings.FILE_NAME)), transport);
        System.out.println("키 파일 사용: " + c.usesKeyFile());
        System.out.println("연결: " + c.saveAndConnect(url) + " — " + c.detail());
        if (!ai.isEnabled()) System.exit(2);

        AppState state = new AppState();
        state.login("중계확인");
        CaseGenerator gen = new CaseGenerator(ai, new CaseValidator(AllowedDrugs.load()), new CaseLoader(),
                CaseGenerator.DEFAULT_MAX_ATTEMPTS);
        HomeController home = new HomeController(state, new CaseLoader(), gen);
        System.out.println("AI 생성 버튼 사용 가능: " + home.aiGenerationAvailable());
        long t0 = System.currentTimeMillis();
        CaseGenerator.Result r = home.generateCase();
        System.out.println("AI 케이스 생성: " + (r.generated() ? "성공" : "실패 → 기본 케이스") + ", "
                + (System.currentTimeMillis() - t0) / 1000 + "초, " + r.patientCase().id() + " " + r.patientCase().title()
                + (r.generated() ? "" : " / " + r.message() + " " + r.problems()));
        home.applyGenerated(r);
        PatientCase pc = state.currentCase();
        ModelAnswer m = pc.modelAnswer();
        state.addObservation(m.observationRecord());
        state.addMedication(m.medicationRecord());
        var s = m.sbarReport();
        t0 = System.currentTimeMillis();
        var sub = new SbarController(state, new InMemoryAttemptRepository(), () -> 1L, ai)
                .submit(s.situation(), s.background(), s.assessment(), s.recommendations());
        Attempt a = sub.attempt();
        System.out.println("제출: " + sub.submitted() + ", 점수 " + (a == null ? "-" : a.score()));
        System.out.println("AI 해설: " + (a == null || a.aiFeedback() == null ? "-" : a.aiFeedback().status()) + ", "
                + (System.currentTimeMillis() - t0) / 1000 + "초");
        if (a != null && a.aiFeedback() != null && a.aiFeedback().strengths() != null) {
            System.out.println("  잘한 점 " + a.aiFeedback().strengths().size() + "개, 고칠 점 "
                    + a.aiFeedback().improvements().size() + "개");
        }
        System.out.println("남은 오늘 사용량: " + AiClient.checkRelay(transport, url));
        System.exit(r.generated() && a != null && a.aiFeedback() != null
                && AiClient.STATUS_SUCCESS.equals(a.aiFeedback().status()) ? 0 : 1);
    }
}

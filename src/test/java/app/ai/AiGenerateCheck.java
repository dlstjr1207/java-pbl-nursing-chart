package app.ai;

import app.cases.AllowedDrugs;
import app.cases.CaseGenerator;
import app.cases.CaseLoader;
import app.cases.CaseValidator;
import app.domain.Medication;
import app.domain.PatientCase;
import app.scoring.Scorer;

/**
 * 실제 AI로 케이스 생성 1회와 해설 1회를 해 보고 결과를 요약하는 점검 도구 (개발용, 프로그램에 포함 안 됨).
 * 키는 출력하지 않는다. 생성 케이스는 target/ai-generated-case.json에 저장해 사람이 볼 수 있게 한다.
 */
public final class AiGenerateCheck {
    public static void main(String[] args) throws Exception {
        AiClient ai = new AiClient(AiConfig.load(AiConfig.DEFAULT_PATH), new JdkHttpTransport());
        CaseGenerator gen = new CaseGenerator(ai, new CaseValidator(AllowedDrugs.load()), new CaseLoader(),
                CaseGenerator.DEFAULT_MAX_ATTEMPTS);
        long t0 = System.currentTimeMillis();
        CaseGenerator.Result r = gen.generate();
        System.out.println("생성 성공=" + r.generated() + ", 걸린 시간=" + (System.currentTimeMillis() - t0) / 1000 + "초, 안내=" + r.message());
        if (!r.problems().isEmpty()) System.out.println("마지막 문제: " + r.problems());
        PatientCase c = r.patientCase();
        System.out.println("케이스=" + c.id() + " / " + c.title() + " / 출처=" + c.source());
        System.out.println("환자=" + c.patient().name() + ", " + c.patient().age() + "세, 진단=" + c.patient().diagnosis());
        for (Medication m : c.orders()) {
            System.out.println("  처방 [" + m.type() + "] " + m.drugName() + " " + m.dose() + m.unit() + " " + m.route()
                    + " " + m.frequency() + " " + m.scheduleTimes());
        }
        if (c.answerKey() != null) {
            System.out.println("정답 키 투약: " + c.answerKey().medicationElements());
            System.out.println("정답 키 관찰 요소 수: " + c.answerKey().observationElements().size());
        }
        if (r.generated()) {
            CaseGenerator.export(c, java.nio.file.Path.of("target", "ai-generated-case.json"));
            var model = c.modelAnswer();
            System.out.println("모범 기록 있음=" + (model != null));
            // 정답 키만으로 채점이 되는지 (학생 기록 없음 → 누락 지적, 점수 계산 확인)
            System.out.println("빈 기록 점수=" + Scorer.evaluate(c, java.util.List.of(), java.util.List.of(), null).score());
        }
        var fb = ai.explain("학생 SBAR: S 불안 사건, B 조현정동장애, A VAS 2, R 18:30 리튬. 점검 결과: R7 통과.");
        System.out.println("해설 상태=" + fb.status() + (fb.strengths() == null ? "" : ", 잘한 점 " + fb.strengths().size()
                + "개, 고칠 점 " + fb.improvements().size() + "개, 예시 " + (fb.exampleRewrite() == null ? 0 : fb.exampleRewrite().length()) + "자"));
    }
}

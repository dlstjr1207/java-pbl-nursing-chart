package app.cases;

import app.ai.AiClient;
import app.ai.AiConfig;
import app.ai.AiTransport;
import app.ai.JdkHttpTransport;
import app.domain.PatientCase;
import app.domain.RawNote;
import app.scoring.RawNotesChecker;
import app.scoring.Scorer;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 실제 AI로 케이스를 N번 생성해 원자료 메모 검사를 통과하는지 보는 점검 도구 (개발용, 프로그램에 포함 안 됨).
 * 시도마다 응답을 다시 검사해 거부 이유를 남기고, 최종 케이스의 원자료 메모 전문과 모범 기록 점수,
 * 원자료를 통째로 붙여 넣었을 때의 R10 피드백을 마크다운으로 저장한다. 키는 출력하지 않는다.
 * 사용: RawNotesGenCheck &lt;출력폴더&gt; [횟수]
 */
public final class RawNotesGenCheck {

    /** 응답 본문을 모아 두는 전송 (실제 전송은 JdkHttpTransport). */
    private static final class Recording implements AiTransport {
        final AiTransport real = new JdkHttpTransport();
        final List<String> bodies = new ArrayList<>();

        @Override
        public String post(String url, String apiKey, String body) throws IOException {
            String r = real.post(url, apiKey, body);
            bodies.add(r);
            return r;
        }
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        Files.createDirectories(out);
        Recording rec = new Recording();
        CaseValidator validator = new CaseValidator(AllowedDrugs.load());
        CaseGenerator gen = new CaseGenerator(new AiClient(AiConfig.load(AiConfig.DEFAULT_PATH), rec), validator,
                new CaseLoader(), CaseGenerator.DEFAULT_MAX_ATTEMPTS);
        StringBuilder summary = new StringBuilder("| 회 | 결과 | 시도 수 | 시도별 거부 이유 | 최종 검사 | 메모 수(관련 없음) | 모범 기록 점수 | 모범 R10 | 붙여 넣기 R10 |\n|---|---|---|---|---|---|---|---|---|\n");
        for (int i = 1; i <= n; i++) {
            int before = rec.bodies.size();
            long t0 = System.currentTimeMillis();
            CaseGenerator.Result r = gen.generate();
            long sec = (System.currentTimeMillis() - t0) / 1000;
            List<String> reasons = new ArrayList<>();
            for (int k = before; k < rec.bodies.size(); k++) {
                List<String> p;
                try {
                    String content = JsonParser.parseString(rec.bodies.get(k)).getAsJsonObject().getAsJsonArray("choices")
                            .get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                    Files.writeString(out.resolve("run" + i + "-try" + (k - before + 1) + ".json"), content, StandardCharsets.UTF_8);
                    PatientCase c = gen.parse(content);
                    p = new ArrayList<>(validator.check(c));
                    if (p.isEmpty()) p.addAll(CaseGenerator.modelScoreProblems(c));
                } catch (RuntimeException e) {
                    p = List.of("읽기 실패: " + e.getMessage());
                }
                reasons.add("시도" + (k - before + 1) + ": " + (p.isEmpty() ? "통과" : String.join(" / ", p)));
            }
            PatientCase c = r.patientCase();
            StringBuilder md = new StringBuilder("# " + i + "회 생성 (" + sec + "초)\n\n");
            md.append("- 결과: ").append(r.generated() ? "AI 케이스 사용" : "실패 → " + c.id()).append('\n');
            reasons.forEach(x -> md.append("- ").append(x).append('\n'));
            String finalCheck = "-";
            String count = "-";
            String modelScore = "-";
            String modelR10 = "-";
            String pasteR10 = "-";
            if (r.generated()) {
                List<String> fc = validator.check(c);
                finalCheck = fc.isEmpty() ? "통과" : "문제 " + fc.size();
                long irr = c.rawNotes().stream().filter(RawNote::irrelevant).count();
                count = c.rawNotes().size() + "(" + irr + ")";
                var m = c.modelAnswer();
                var s = Scorer.evaluate(c, List.of(m.observationRecord()), List.of(m.medicationRecord()), m.sbarReport());
                modelScore = String.valueOf(s.score());
                modelR10 = s.checks().stream().anyMatch(x -> x.ruleId().equals("R10")) ? "있음" : "없음";
                String pasted = RawNotesChecker.allNotes(c.rawNotes().stream().filter(x -> !x.irrelevant()).toList());
                var o = m.observationRecord();
                var po = new app.domain.ObservationEntry(o.time(), o.shift(), o.tags(), pasted, o.intervention(), o.vitals());
                var ps = Scorer.evaluate(c, List.of(po), List.of(m.medicationRecord()), m.sbarReport());
                pasteR10 = ps.checks().stream().filter(x -> x.ruleId().equals("R10")).map(x -> x.message().split("\\.")[0])
                        .distinct().reduce((a, b) -> a + "; " + b).orElse("없음");
                md.append("\n## 환자\n\n").append(c.id()).append(" · ").append(c.patient().name()).append(", ")
                        .append(c.patient().age()).append("세 ").append(c.patient().sex()).append(", ").append(c.patient().diagnosis())
                        .append(", ").append(c.patient().room()).append(", 입원 ").append(c.patient().hospitalDay()).append("일차\n");
                md.append("\n## 과제 안내 (task)\n\n").append(c.task()).append('\n');
                md.append("\n## 원자료 메모 전문 (화면 순서, ★ = 관련 없는 메모, 학생에게는 표시 안 됨)\n\n");
                for (RawNote x : c.rawNotes()) {
                    md.append("- ").append(x.irrelevant() ? "★ " : "").append(x.time()).append("  ").append(x.text())
                            .append(x.irrelevant() ? "  (cues: " + String.join(", ", x.cues()) + ")" : "").append('\n');
                }
                md.append("\n## 정답 키\n\n- 관찰: ").append(c.answerKey().observationElements()).append("\n- 투약: ")
                        .append(c.answerKey().medicationElements()).append('\n');
                md.append("\n## 모범 관찰 기록\n\n").append(m.observationRecord().narrative()).append('\n');
                md.append("\n- 모범 기록 점수: ").append(modelScore).append(", 모범 기록 R10: ").append(modelR10)
                        .append("\n- 관련 메모를 통째로 붙여 넣은 관찰 기록의 R10: ").append(pasteR10)
                        .append(", 점수 ").append(ps.score()).append('\n');
                CaseGenerator.export(c, out.resolve("run" + i + "-case.json"));
            }
            Files.writeString(out.resolve("run" + i + ".md"), md.toString(), StandardCharsets.UTF_8);
            summary.append("| ").append(i).append(" | ").append(r.generated() ? "성공" : "실패").append(" | ")
                    .append(rec.bodies.size() - before).append(" | ").append(String.join("<br>", reasons).replace("|", "/"))
                    .append(" | ").append(finalCheck).append(" | ").append(count).append(" | ").append(modelScore)
                    .append(" | ").append(modelR10).append(" | ").append(pasteR10).append(" |\n");
            System.out.println(i + "회: " + (r.generated() ? "성공" : "실패") + ", 시도 " + (rec.bodies.size() - before) + ", " + sec + "초");
        }
        Files.writeString(out.resolve("summary.md"), summary.toString(), StandardCharsets.UTF_8);
        System.out.println(summary);
    }
}

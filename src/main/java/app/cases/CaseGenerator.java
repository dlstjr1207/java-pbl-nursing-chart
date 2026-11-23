package app.cases;

import app.ai.AiClient;
import app.domain.Attempt;
import app.domain.CaseSource;
import app.domain.DomainJson;
import app.domain.PatientCase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * AI로 가상 환자 케이스를 만든다 (F-14, D20, D24, S9).
 * AI가 만든 JSON을 {@link CaseValidator}로 검사해 통과한 것만 쓰고, 위반이면 이유를 알려 다시 요청한다(한도까지).
 * 키가 없거나 허용 약물 목록이 비어 있거나 한도를 넘기면 AI 없이 기본 케이스(PC-01, 없으면 DEMO)로 되돌린다.
 * 사건 유형은 환자케이스 문서의 첫 도입 유형(불안 증가 → PRN 투약 → 재평가)만 쓴다. 점수에는 관여하지 않는다.
 */
public class CaseGenerator {

    /** 기본 케이스 ID (PC-01이 없으면 DEMO). */
    public static final String FIXED_ID = "PC-01";
    public static final String DEMO_ID = "demo";
    /** 첫 도입 사건 유형 */
    public static final String EVENT_ANXIETY_PRN = "불안 증가 → PRN 투약 → 재평가";
    /** 재생성 기본 한도(첫 요청 포함 총 시도 횟수) */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * 생성 결과.
     *
     * @param patientCase 실습에 쓸 케이스 (생성 성공이면 AI 케이스, 아니면 기본 케이스)
     * @param generated   AI 생성 케이스를 쓰는지
     * @param message     화면에 보일 안내 (실패 이유 포함)
     * @param problems    마지막 시도의 위반 목록 (성공이면 빈 목록)
     */
    public record Result(PatientCase patientCase, boolean generated, String message, List<String> problems) {
    }

    private final AiClient ai;
    private final CaseValidator validator;
    private final CaseLoader loader;
    private final int maxAttempts;

    public CaseGenerator(AiClient ai, CaseValidator validator, CaseLoader loader, int maxAttempts) {
        this.ai = ai;
        this.validator = validator;
        this.loader = loader;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** AI 키가 있고 허용 약물 목록이 채워져 있어 생성을 쓸 수 있는지 (D26). */
    public boolean available() {
        return ai.isEnabled() && validator.generationAvailable();
    }

    /** 생성을 쓸 수 없는 이유 (쓸 수 있으면 빈 문자열). */
    public String unavailableReason() {
        if (!ai.isEnabled()) return "AI 키가 설정되지 않아 케이스를 생성할 수 없습니다. 기본 케이스로 실습합니다.";
        if (!validator.generationAvailable()) return "허용 약물 목록이 비어 있어 케이스를 생성할 수 없습니다. 기본 케이스로 실습합니다.";
        return "";
    }

    /** 첫 도입 유형으로 케이스를 만든다. 실패하면 기본 케이스를 돌려준다. */
    public Result generate() {
        if (!available()) {
            return fallback(unavailableReason(), List.of());
        }
        List<String> problems = List.of();
        for (int i = 1; i <= maxAttempts; i++) {
            String content;
            try {
                content = ai.complete(systemPrompt(), userPrompt(problems));
            } catch (IOException e) {
                problems = List.of("AI 호출 실패");
                continue;
            }
            PatientCase c;
            try {
                c = parse(content);
            } catch (RuntimeException e) {
                problems = List.of("JSON 형식 오류: 응답을 케이스로 읽을 수 없음");
                continue;
            }
            problems = validator.check(c);
            if (problems.isEmpty()) {
                return new Result(c, true, "AI가 새 케이스를 만들었습니다.", List.of());
            }
        }
        return fallback("케이스 생성에 " + maxAttempts + "번 실패해 기본 케이스로 실습합니다.", problems);
    }

    /** 응답 문자열(코드 펜스 허용)을 케이스로 바꾼다. 출처는 항상 AI_GENERATED로 고정한다. */
    PatientCase parse(String content) {
        String json = content == null ? "" : content.trim();
        if (json.startsWith("```")) {
            int nl = json.indexOf('\n');
            int last = json.lastIndexOf("```");
            if (nl > 0 && last > nl) json = json.substring(nl + 1, last).trim();
        }
        PatientCase c = DomainJson.fromJson(json, PatientCase.class);
        if (c == null) throw new IllegalArgumentException("빈 응답");
        // 정답 키는 PC-01과 같은 규칙으로 채점하기 위해 쓴다(검사기가 처방과 맞는지 확인). 모범 기록은 제출 후 참고용.
        // 출처·검토 상태는 프로그램이 정한다.
        return new PatientCase(c.id(), c.title(), c.difficulty(), c.objectives(), "AI_GENERATED",
                CaseSource.AI_GENERATED, c.patient(), c.adr(), c.riskTier(), c.orders(), c.regimenHistory(),
                c.lithiumLevel(), c.vitals(), c.observationLog(), c.safetyRounds(), c.briefing(),
                c.sbarReference(), c.answerKey(), c.modelAnswer());
    }

    private Result fallback(String message, List<String> problems) {
        String id = loader.exists(FIXED_ID) ? FIXED_ID : DEMO_ID;
        return new Result(loader.load(id), false, message, new ArrayList<>(problems));
    }

    /** 생성한 케이스를 JSON 파일로 내보낸다. 사람이 검토한 뒤 PC-01.json으로 저장할 때 쓴다 (D24). */
    public static void export(PatientCase c, Path file) throws IOException {
        Files.writeString(file, DomainJson.toJson(c), StandardCharsets.UTF_8);
    }

    /** 저장된 실습 기록에서 케이스를 다시 읽는다. 생성 케이스 원문이 없으면 null (같은 환자 재현, S9). */
    public static PatientCase restore(Attempt a) {
        if (a == null || a.generatedCaseJson() == null || a.generatedCaseJson().isBlank()) return null;
        return DomainJson.fromJson(a.generatedCaseJson(), PatientCase.class);
    }

    private String systemPrompt() {
        return "너는 간호학생 정신과 실습용 가상 환자 케이스를 만든다. 실제 환자 정보를 쓰지 않는다. "
                + "반드시 JSON 객체 하나만 답한다. 키: id, title, patient(name 등), orders(orderId, type=REGULAR|PRN|PAST, "
                + "drugName, dose, unit, route, frequency('1일 N회'), scheduleTimes[HH:mm], prnIndication), "
                + "vitals(time HH:mm 등), observationLog, briefing(학생이 기록할 사건의 시각·장소·행동·환자 발언·간호·PRN 투약), "
                + "answerKey, modelAnswer(observation, medication, sbar 문장). "
                + "answerKey 형식: observationElements는 '이름=동의어1|동의어2' 문자열 배열(장소, 행동, 발언 인용, 간호, 투약, 재평가), "
                + "medicationElements는 ['약명=…', '용량=숫자', '단위=mg', '경로=PO', '시각=HH:mm']이며 PRN 처방과 같은 약·용량이어야 한다. "
                + "sbarElements는 situation·background·assessment·recommendation 배열. "
                + "약물·용량은 아래 허용 목록 안에서만 고르고, 정규 처방은 1일 총량이 허용 범위 안이어야 한다.";
    }

    private String userPrompt(List<String> problems) {
        StringBuilder sb = new StringBuilder("사건 유형: ").append(EVENT_ANXIETY_PRN).append('\n');
        sb.append("허용 약물: ").append(DomainJson.toJson(validator.allowedDrugs().all())).append('\n');
        if (!problems.isEmpty()) {
            sb.append("이전 응답의 문제를 고쳐 다시 만든다: ").append(String.join("; ", problems));
        }
        return sb.toString();
    }
}

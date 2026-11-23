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
    /** 매번 다른 환자를 만들도록 요청 조건(성별·나이·사건 장소)을 고르는 난수. 테스트에서는 고정 시드. */
    private final java.util.Random random;

    /** 사건 장소 후보 (병동 안의 장소, 임상 값 아님). */
    static final List<String> PLACES = List.of("데이룸", "복도", "병실", "간호사실 앞", "식당", "면회실 앞", "샤워실 앞", "프로그램실");
    /** 생성 환자 나이 범위 */
    static final int MIN_AGE = 20;
    static final int MAX_AGE = 60;

    public CaseGenerator(AiClient ai, CaseValidator validator, CaseLoader loader, int maxAttempts) {
        this(ai, validator, loader, maxAttempts, new java.util.Random());
    }

    public CaseGenerator(AiClient ai, CaseValidator validator, CaseLoader loader, int maxAttempts, java.util.Random random) {
        this.ai = ai;
        this.validator = validator;
        this.loader = loader;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.random = random;
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
        String conditions = conditions();
        for (int i = 1; i <= maxAttempts; i++) {
            String content;
            try {
                content = ai.complete(systemPrompt(), userPrompt(conditions, problems));
            } catch (IOException e) {
                problems = List.of("AI 호출 실패");
                continue;
            }
            PatientCase c;
            try {
                c = parse(content);
            } catch (RuntimeException e) {
                // 원인을 다음 요청에 알려 고치게 한다
                problems = List.of("JSON 형식 오류: 응답을 케이스로 읽을 수 없음 (" + shortReason(e) + ")");
                continue;
            }
            problems = new ArrayList<>(validator.check(c));
            problems.addAll(diversityProblems(c));
            if (problems.isEmpty()) {
                return new Result(c, true, "AI가 새 케이스를 만들었습니다.", List.of());
            }
        }
        return fallback("케이스 생성에 " + maxAttempts + "번 실패해 기본 케이스로 실습합니다.", problems);
    }

    /** 응답 문자열(코드 펜스 허용)을 케이스로 바꾼다. 출처는 항상 AI_GENERATED로 고정한다. */
    PatientCase parse(String content) {
        // 앞뒤 설명문·코드 펜스를 걷어 내고, 문자열 칸에 온 객체 등 흔한 형태 차이를 맞춘 뒤 읽는다
        PatientCase c = DomainJson.gson().fromJson(GeneratedCaseJson.normalize(content), PatientCase.class);
        if (c == null) throw new IllegalArgumentException("빈 응답");
        // 정답 키는 PC-01과 같은 규칙으로 채점하기 위해 쓴다(검사기가 처방과 맞는지 확인). 모범 기록은 제출 후 참고용.
        // 출처·검토 상태는 프로그램이 정한다.
        return new PatientCase(c.id(), c.title(), c.difficulty(), c.objectives(), "AI_GENERATED",
                CaseSource.AI_GENERATED, c.patient(), c.adr(), c.riskTier(), c.orders(), c.regimenHistory(),
                c.lithiumLevel(), c.vitals(), c.observationLog(), c.safetyRounds(), c.briefing(),
                c.sbarReference(), c.answerKey(), c.modelAnswer());
    }

    private static String shortReason(RuntimeException e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        String m = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        int cut = m.indexOf("\nSee ");
        if (cut > 0) m = m.substring(0, cut);
        return m.length() > 160 ? m.substring(0, 160) : m;
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

    String systemPrompt() {
        return "너는 간호학생 정신과 실습용 가상 환자 케이스를 만든다. 실제 환자 정보를 쓰지 않는다. "
                + "반드시 아래 견본과 같은 키와 값 형식의 JSON 객체 하나만 답한다(설명문 없이). "
                + "briefing·title·modelAnswer의 observation·medication·sbar는 반드시 문자열이다. "
                + "type은 REGULAR|PRN|PAST, shift는 DAY|EVENING|NIGHT, 시각은 HH:mm. "
                + "약물·용량은 허용 목록 안에서만 고르고, 정규 처방은 (1회 용량 × scheduleTimes 수)가 1일 허용 범위 안이어야 한다. "
                + "answerKey.medicationElements는 PRN 처방과 같은 약명·용량·경로여야 하고, 시각은 briefing의 투약 시각이다. "
                + "briefing은 필수이고, observationLog는 2건 이상, vitals는 2회 이상(서로 다른 시각) 넣는다.\n"
                + "견본:\n" + TEMPLATE;
    }

    /** 생성 응답의 형식 견본 (값은 예시 자리일 뿐 임상 값이 아님). */
    static final String TEMPLATE = """
            {"id":"GEN-1","title":"짧은 제목","difficulty":"기본","objectives":["학습 목표"],
             "patient":{"name":"가상 이름","age":30,"sex":"F","patientNo":"SIM-G01","room":"000호",
                        "diagnosis":"진단명","admissionType":"동의입원","admissionDate":"2026-10-01","hospitalDay":10},
             "adr":{"allergies":["없음"],"adverseReactions":[]},
             "orders":[{"orderId":"G-REG1","type":"REGULAR","drugName":"허용 약명","dose":"숫자","unit":"mg","route":"PO",
                        "frequency":"1일 N회","scheduleTimes":["08:30"]},
                       {"orderId":"G-PRN1","type":"PRN","drugName":"허용 약명","dose":"숫자","unit":"mg","route":"PO",
                        "frequency":"필요 시","prnIndication":"불안·초조 시"}],
             "vitals":[{"measurementId":"V1","time":"HH:mm","systolic":0,"diastolic":0,"pulse":0,"respiration":0,"temperature":0.0,"spo2":0},
                       {"measurementId":"V2","time":"HH:mm","systolic":0,"diastolic":0,"pulse":0,"respiration":0,"temperature":0.0,"spo2":0}],
             "observationLog":[{"time":"HH:mm","shift":"DAY","tags":["기타"],"narrative":"사건 전 관찰 문장"},
                               {"time":"HH:mm","shift":"DAY","tags":["안정 회복"],"narrative":"재평가 결과 문장"}],
             "briefing":"사건 시각·장소·행동·환자 발언(따옴표)·간호·PRN 투약을 담은 문장",
             "answerKey":{"observationElements":["장소=동의어1|동의어2","행동=…","발언 인용=…","간호=…","투약=약명|영문약명&1mg|1 mg&po|경구","재평가=…"],
                          "medicationElements":["약명=영문약명|한글약명","용량=숫자","단위=mg","경로=PO|경구","시각=HH:mm"],
                          "sbarElements":{"situation":["불안 사건=불안|초조"],"background":["진단=진단명"],"assessment":["재평가 결과=…"],"recommendation":["다음 근무 관찰=관찰"]}},
             "modelAnswer":{"observation":"모범 관찰 문장","medication":"모범 투약 문장","sbar":"S: …\\nB: …\\nA: …\\nR: …"}}
            """;

    /**
     * 이번 요청의 환자·사건 조건. 매번 성별·나이·사건 장소를 바꿔 같은 환자가 반복되지 않게 한다 (polish 11).
     * 기본 케이스(PC-01)의 이름·나이·병실은 쓰지 못하게 알린다.
     */
    String conditions() {
        String sex = random.nextBoolean() ? "F" : "M";
        int age = MIN_AGE + random.nextInt(MAX_AGE - MIN_AGE + 1);
        String place = PLACES.get(random.nextInt(PLACES.size()));
        StringBuilder sb = new StringBuilder("이번 환자 조건: 성별 ").append(sex).append(", 나이 ").append(age)
                .append("세, 사건 장소 ").append(place).append(".\n")
                .append("이름·병실 번호·입원 일수·진단·환자 발언은 매번 새로 정한다(흔한 예시 반복 금지). ")
                .append("진단은 허용 약물로 치료하는 범위(조현병, 조현정동장애, 양극성 장애) 안에서 고른다. ")
                .append("나이는 ").append(MIN_AGE).append("~").append(MAX_AGE).append("세.\n");
        PatientCase base = baseCase();
        if (base != null && base.patient() != null) {
            sb.append("금지: 이름 '").append(base.patient().name()).append("', 나이 ").append(base.patient().age())
                    .append("세, 병실 '").append(base.patient().room()).append("' (기본 케이스와 같은 값).\n");
        }
        return sb.toString();
    }

    /** 생성 케이스가 기본 케이스와 같은 이름·나이·병실이거나 나이 범위를 벗어나면 문제로 알린다. */
    List<String> diversityProblems(PatientCase c) {
        List<String> out = new ArrayList<>();
        if (c.patient() == null) return out;
        Integer age = c.patient().age();
        if (age == null || age < MIN_AGE || age > MAX_AGE) {
            out.add("patient.age는 " + MIN_AGE + "~" + MAX_AGE + "세여야 함");
        }
        PatientCase base = baseCase();
        if (base != null && base.patient() != null) {
            var b = base.patient();
            if (same(b.name(), c.patient().name())) out.add("patient.name이 기본 케이스와 같음");
            if (b.age() != null && b.age().equals(age)) out.add("patient.age가 기본 케이스와 같음");
            if (same(b.room(), c.patient().room())) out.add("patient.room이 기본 케이스와 같음");
        }
        return out;
    }

    private PatientCase baseCase() {
        try {
            return loader.exists(FIXED_ID) ? loader.load(FIXED_ID) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && a.replaceAll("\\s+", "").equals(b.replaceAll("\\s+", ""));
    }

    String userPrompt(List<String> problems) {
        return userPrompt("", problems);
    }

    String userPrompt(String conditions, List<String> problems) {
        StringBuilder sb = new StringBuilder("사건 유형: ").append(EVENT_ANXIETY_PRN).append('\n');
        sb.append(conditions);
        sb.append("허용 약물: ").append(DomainJson.toJson(validator.allowedDrugs().all())).append('\n');
        if (!problems.isEmpty()) {
            sb.append("이전 응답의 문제를 고쳐 다시 만든다: ").append(String.join("; ", problems));
        }
        return sb.toString();
    }
}

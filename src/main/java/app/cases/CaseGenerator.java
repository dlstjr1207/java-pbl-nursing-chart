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

    /** 생성 시각 (케이스 ID에 쓴다). 테스트에서 고정할 수 있다. */
    private java.util.function.Supplier<java.time.LocalDateTime> clock = java.time.LocalDateTime::now;
    /** 이 실행에서 마지막으로 붙인 ID (같은 분에 또 만들면 뒤에 -2, -3을 붙인다). */
    private static String lastId;
    private static int sameMinuteCount;
    private static final java.time.format.DateTimeFormatter ID_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    /** 사건 장소 후보 (병동 안의 장소, 임상 값 아님). */
    static final List<String> PLACES = List.of("데이룸", "복도", "병실", "간호사실 앞", "식당", "면회실 앞", "샤워실 앞", "프로그램실");
    /** 이름 성씨 후보 (매번 다른 성을 제안해 같은 성이 반복되지 않게 한다, 임상 값 아님). */
    static final List<String> SURNAMES = List.of("김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오",
            "서", "신", "권", "황", "안", "송", "류", "홍");
    /** 이 실행에서 최근에 만든 환자의 이름·성·사건 장소 (다음 생성에서 피한다). */
    private final java.util.ArrayDeque<String> recentNames = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> recentSurnames = new java.util.ArrayDeque<>();
    /** 실제로 만든 케이스의 장소 (거부 기준) */
    private final java.util.ArrayDeque<String> recentPlaces = new java.util.ArrayDeque<>();
    /** 요청할 때 제안한 장소 (다음 제안에서 피한다) */
    private final java.util.ArrayDeque<String> requestedPlaces = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> recentRooms = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> recentDiagnoses = new java.util.ArrayDeque<>();
    /** 진단 후보 (허용 약물로 치료하는 범위, D29) */
    static final List<String> DIAGNOSES = List.of("조현병", "조현정동장애", "양극성 장애");
    private static final int RECENT_MAX = 3;

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

    /** 생성 시각을 정한다 (테스트용). */
    CaseGenerator withClock(java.util.function.Supplier<java.time.LocalDateTime> clock) {
        this.clock = clock;
        return this;
    }

    /**
     * 생성 케이스 ID. AI가 아니라 프로그램이 생성 시각으로 붙인다 (예: GEN-20261123-1530).
     * 같은 분에 다시 만들면 GEN-20261123-1530-2처럼 번호를 붙여 과거 기록에서 서로 구분되게 한다.
     */
    String newCaseId() {
        String base = "GEN-" + clock.get().format(ID_FMT);
        synchronized (CaseGenerator.class) {
            if (base.equals(lastId)) {
                sameMinuteCount++;
                return base + "-" + sameMinuteCount;
            }
            lastId = base;
            sameMinuteCount = 1;
            return base;
        }
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
        String previous = null; // 검사에 걸린 직전 케이스(JSON). 다시 요청할 때 이것을 고치게 한다
        String conditions = conditions();
        for (int i = 1; i <= maxAttempts; i++) {
            String content;
            try {
                content = ai.complete(systemPrompt(), userPrompt(conditions, problems, previous));
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
                previous = null;
                continue;
            }
            problems = new ArrayList<>(validator.check(c));
            problems.addAll(diversityProblems(c));
            if (problems.isEmpty()) problems.addAll(modelScoreProblems(c)); // 형식이 맞을 때만 채점해 본다
            if (problems.isEmpty()) {
                remember(c);
                return new Result(withId(c, newCaseId()), true, "AI가 새 케이스를 만들었습니다.", List.of());
            }
            previous = DomainJson.toJson(c);
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
        return new PatientCase("GEN-검사중", c.title(), c.difficulty(), c.objectives(), "AI_GENERATED",
                CaseSource.AI_GENERATED, c.patient(), c.adr(), c.riskTier(), tidyOrders(c.orders()), c.regimenHistory(),
                c.lithiumLevel(), c.vitals(), c.observationLog(), c.safetyRounds(), c.briefing(),
                c.sbarReference(), tidyKey(c.answerKey()), tidyModel(c.modelAnswer()), tidyOrder(tidyCues(c.rawNotes())), c.task());
    }

    // ---- 용량 표기 정리: AI가 "1.0"으로 준 값을 "1"로 저장해 화면·정답 키·모범 기록을 같은 표기로 맞춘다 ----

    private static String tidy(String s) {
        return app.scoring.ElementPattern.trimZeroDecimal(s);
    }

    private static List<String> tidyAll(List<String> list) {
        return list == null ? null : list.stream().map(CaseGenerator::tidy).toList();
    }

    /** 처방의 용량 "1.0" → "1". */
    static List<app.domain.Medication> tidyOrders(List<app.domain.Medication> orders) {
        if (orders == null) return null;
        return orders.stream().map(m -> new app.domain.Medication(m.orderId(), m.type(), m.drugName(), tidy(m.dose()),
                m.unit(), m.route(), m.frequency(), m.scheduleTimes(), m.prnIndication(), m.note())).toList();
    }

    /** 정답 키 요소의 숫자 표기 정리 (예: "투약=…&1.0mg|1.0 mg&…" → "1mg|1 mg"). 시각(09:30)은 바뀌지 않는다. */
    static app.domain.AnswerKey tidyKey(app.domain.AnswerKey k) {
        if (k == null) return null;
        app.domain.SbarElements s = k.sbarElements();
        app.domain.SbarElements s2 = s == null ? null : new app.domain.SbarElements(tidyAll(s.situation()),
                tidyAll(s.background()), tidyAll(s.assessment()), tidyAll(s.recommendation()));
        return new app.domain.AnswerKey(tidyAll(k.observationElements()), tidyAll(k.medicationElements()), s2);
    }

    /** 모범 투약 기록의 용량 "1.0" → "1". */
    static app.domain.ModelAnswer tidyModel(app.domain.ModelAnswer m) {
        if (m == null || m.medicationRecord() == null) return m;
        app.domain.MedicationRecord r = m.medicationRecord();
        app.domain.MedicationRecord r2 = new app.domain.MedicationRecord(r.orderId(), r.drugName(), tidy(r.dose()), r.unit(),
                r.route(), r.administeredTime(), r.result(), r.reason(), r.orderBasis(), r.oralCheck(), r.reassessmentPlan());
        return new app.domain.ModelAnswer(m.observation(), m.medication(), m.sbar(), m.observationRecord(), r2, m.sbarReport());
    }

    /**
     * 관련 없는 메모의 알아볼 낱말(cues) 정리: 그 메모 글에 없거나 관련 있는 메모에도 나오는 흔한 낱말(예: '측정', '침상')은 뺀다.
     * 메모 내용은 바꾸지 않는다. 남는 낱말이 없으면 그 메모 글에서 관련 메모에 없는 낱말을 골라 쓰고(cuesFromText),
     * 그런 낱말도 없으면 그대로 두어 검사기가 이유를 알리게 한다.
     */
    static List<app.domain.RawNote> tidyCues(List<app.domain.RawNote> notes) {
        if (notes == null) return null;
        String relevant = notes.stream().filter(n -> n != null && !n.irrelevant() && n.text() != null)
                .map(n -> squash(n.text())).reduce("", (a, b) -> a + "\n" + b);
        List<app.domain.RawNote> out = new ArrayList<>();
        for (app.domain.RawNote n : notes) {
            if (n == null || !n.irrelevant()) {
                out.add(n);
                continue;
            }
            String own = squash(n.text());
            List<String> keep = n.cues().stream().filter(c -> c != null && !c.isBlank())
                    .filter(c -> own.contains(squash(c)) && !relevant.contains(squash(c))).toList();
            if (keep.isEmpty()) keep = cuesFromText(n.text(), relevant);
            out.add(keep.isEmpty() ? n : new app.domain.RawNote(n.time(), n.text(), true, keep));
        }
        return out;
    }

    /** 알아볼 낱말로 쓰지 않는 흔한 말 */
    private static final java.util.Set<String> COMMON_WORDS = java.util.Set.of("측정", "침상", "지원", "확인", "환자", "요청",
            "문의", "전화", "완료", "다른", "관련", "시간", "병동", "간호사", "옴", "함", "받음", "드림", "해줌", "있음", "없음");
    /** 낱말 끝에서 떼는 조사·메모체 끝말 (긴 것부터) */
    private static final List<String> PARTICLES = List.of("에서", "으로", "에게", "이", "가", "을", "를", "은", "는", "에", "로",
            "와", "과", "의", "도", "함", "옴", "됨");

    /**
     * 관련 없는 메모의 알아볼 낱말이 모두 빠졌을 때, 그 메모 글에서 관련 있는 메모에 나오지 않는 낱말(2글자 이상, 조사 뗌)을
     * 최대 3개 고른다. 메모 글에 있는 낱말만 쓰므로 새 내용을 지어내지 않는다.
     */
    static List<String> cuesFromText(String text, String relevantSquashed) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String w : text.split("[^\\p{L}\\p{N}]+")) {
            for (String p : PARTICLES) {
                if (w.length() > p.length() + 1 && w.endsWith(p)) {
                    w = w.substring(0, w.length() - p.length());
                    break;
                }
            }
            if (w.length() < 2 || w.matches(".*\\d.*") || COMMON_WORDS.contains(w)) continue;
            if (relevantSquashed.contains(squash(w)) || out.contains(w)) continue;
            out.add(w);
            if (out.size() == 3) break;
        }
        return out;
    }

    /**
     * 메모 순서 정리: 메모가 시각순으로 완전히 정렬돼 있으면 가운데의 시각이 다른 이웃 두 메모의 자리를 바꿔
     * 순서를 일부 섞는다(두서없는 메모). 메모 글·시각은 바꾸지 않고 보이는 순서만 바꾼다.
     */
    static List<app.domain.RawNote> tidyOrder(List<app.domain.RawNote> notes) {
        if (notes == null || notes.size() < 3) return notes;
        for (int i = 1; i < notes.size(); i++) {
            app.domain.RawNote a = notes.get(i - 1);
            app.domain.RawNote b = notes.get(i);
            if (a == null || b == null || a.time() == null || b.time() == null) return notes;
            if (b.time().compareTo(a.time()) < 0) return notes; // 이미 섞여 있음
        }
        List<app.domain.RawNote> out = new ArrayList<>(notes);
        int mid = out.size() / 2;
        for (int d = 0; d < out.size(); d++) {
            for (int i : new int[] {mid + d, mid - d}) {
                if (i >= 1 && i < out.size() && !out.get(i).time().equals(out.get(i - 1).time())) {
                    java.util.Collections.swap(out, i - 1, i);
                    return out;
                }
            }
        }
        return notes; // 모든 시각이 같으면 그대로 (검사기가 알린다)
    }

    private static String squash(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** 케이스 ID만 바꾼 사본. */
    private static PatientCase withId(PatientCase c, String id) {
        return new PatientCase(id, c.title(), c.difficulty(), c.objectives(), c.reviewStatus(), c.source(), c.patient(),
                c.adr(), c.riskTier(), c.orders(), c.regimenHistory(), c.lithiumLevel(), c.vitals(), c.observationLog(),
                c.safetyRounds(), c.briefing(), c.sbarReference(), c.answerKey(), c.modelAnswer(), c.rawNotes(), c.task());
    }

    /**
     * 모범 기록을 자기 케이스 정답 키로 채점해 100점인지 확인한다 (생성 직후 검사).
     * 모범 기록이 만점이 아니면 정답 키와 모범 기록이 어긋난 것이므로 거부하고 다시 생성한다.
     */
    static List<String> modelScoreProblems(PatientCase c) {
        var m = c.modelAnswer();
        if (m == null || m.observationRecord() == null || m.medicationRecord() == null || m.sbarReport() == null) {
            return List.of("modelAnswer의 observationRecord·medicationRecord·sbarReport(구조화된 모범 기록)가 필요함");
        }
        app.scoring.Scorer.Result r = app.scoring.Scorer.evaluate(c, List.of(m.observationRecord()),
                List.of(m.medicationRecord()), m.sbarReport());
        if (r.score() != null && r.score() >= 100.0 - 1e-9) return List.of();
        List<String> out = new ArrayList<>();
        out.add("모범 기록이 자기 정답 키로 " + (r.score() == null ? "미산출" : r.score() + "점") + " (100점이어야 함)");
        r.checks().stream()
                .filter(x -> x.status() == app.domain.CheckStatus.MISSING || x.status() == app.domain.CheckStatus.MISMATCH)
                .limit(6)
                .forEach(x -> out.add("모범 기록 " + x.ruleId() + " " + x.targetRef() + ": " + x.message()));
        // 관찰 핵심 사실이 빠졌으면 어떤 동의어를 넣어야 하는지 구체적으로 알린다 (같은 실수를 되풀이하지 않게)
        if (c.answerKey() != null) {
            for (var e : app.scoring.ElementPattern.parseAll(c.answerKey().observationElements())) {
                boolean missing = r.checks().stream().anyMatch(x -> x.status() == app.domain.CheckStatus.MISSING
                        && x.targetRef().equals("observation.fact[" + e.label() + "]"));
                if (missing) {
                    String raw = c.answerKey().observationElements().stream()
                            .filter(x -> x != null && x.startsWith(e.label() + "=")).findFirst().orElse(e.label());
                    out.add("고치는 법: modelAnswer.observationRecord의 narrative나 intervention에 정답 키 '" + raw
                            + "'의 동의어 하나를 글자 그대로 넣는다 (동의어는 메모에 있는 낱말이어야 함)");
                }
            }
        }
        return out;
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
                + "briefing은 필수이고, observationLog는 2건 이상, vitals는 2회 이상(서로 다른 시각) 넣는다. "
                + "vitals는 생리적으로 가능한 값만 쓴다(수축기 70~200, 이완기 40~130, 맥박 40~160, 호흡 8~40, 체온 34~41, SpO2 80~100). "
                + "학생이 담당 간호사 입장이다. briefing에서 학생(간호사)을 제3자로 쓰지 말고(예: '간호사에게 알리고' 금지), "
                + "간호 행위는 행동 그대로 서술한다(예: 1:1로 조용한 곳에서 대화하며 안정을 도움). "
                + "briefing에는 PRN 투약 시각, answerKey '장소' 동의어 중 하나, 따옴표 안 환자 발언(answerKey '발언 인용' 동의어 포함)이 반드시 들어간다. "
                + "answerKey의 약명 동의어는 한글·영어를 모두 쓰고(예: 로라제팜|Lorazepam), 경로는 반드시 PO|경구로 쓴다. "
                + "id는 쓰지 않는다(프로그램이 붙인다). "
                + "modelAnswer에는 문장(observation·medication·sbar)과 함께 구조화된 observationRecord·medicationRecord·sbarReport를 넣고, "
                + "이 구조화된 모범 기록이 answerKey의 모든 요소를 포함해 자기 정답 키로 100점이어야 한다"
                + "(관찰 서술에 큰따옴표로 환자 발언 인용, 판단 표현 대신 관찰한 행동, 재평가 시각 포함; 투약 기록의 모든 칸 채움; 모든 시각은 HH:mm). "
                + "answerKey 요소의 동의어는 '…' 같은 자리 표시 없이 실제 낱말로 쓰고, 이 케이스의 사건에 맞게 바꾼다(견본 낱말을 그대로 베끼지 않는다). "
                + "'발언 인용' 동의어는 따옴표 없이 환자 발언 속 핵심 낱말 1~3개다(문장 전체 금지, 예: 발언 인용=답답|무서워). "
                + "answerKey.observationElements의 투약 요소는 정확히 '투약=한글약명|영문약명&{용량}mg|{용량} mg&PO|경구' 형식이다(경로 묶음은 PO|경구 하나). "
                + "모범 기록(modelAnswer의 모든 문장과 구조화된 기록)은 학생에게 보여 줄 자연스러운 문장이며 정답 키 기호(| &)를 쓰지 않는다. "
                + "observationRecord.narrative에는 observationElements 각 요소(장소·행동·발언 인용·간호·투약·재평가)의 동의어 중 하나를 글자 그대로 넣고, "
                + "sbarReport의 S·B·A·R 각 칸에는 sbarElements 해당 영역 요소의 동의어를 글자 그대로 넣는다. "
                + "sbarReport.recommendations의 time도 반드시 HH:mm 시각이다(예: 16:00). DAY·EVENING·NIGHT 같은 근무 이름이나 '다음 근무'를 time에 쓰지 않는다. "
                + "모범 기록은 다음 채점 기준도 만족해야 한다: "
                + "재평가 계획은 '재평가' 낱말과 시각을 한 문장에 함께 쓰고(예: '10:00(투여 30분 후) 불안 정도 재평가 예정', "
                + "medicationRecord.reassessmentPlan '10:00 불안 VAS 재평가'), '재평가 안 함·없음·생략' 같은 부정 표현을 쓰지 않는다. "
                + "PRN을 투여한 medicationRecord는 result GIVEN, oralCheck true, reason을 채운다. "
                + "sbarReport의 S·B·A·R 각 칸과 recommendations(시각+중재 글)에 sbarElements 해당 영역의 모든 요소 동의어가 하나씩 들어가야 한다"
                + "(R 요소가 '18:30&리튬'처럼 시각과 약을 함께 요구하면 그 시각 행의 중재에 약명을 쓴다).\n"
                + RAW_NOTES_RULES
                + "견본:\n" + TEMPLATE;
    }

    /**
     * 원자료 메모 조건. 학생 화면에는 briefing 대신 rawNotes(두서없는 근무 중 메모)와 task를 보여 주고,
     * 학생은 메모에서 필요한 사실만 골라 기록한다. CaseValidator가 같은 조건을 검사한다.
     */
    static final String RAW_NOTES_RULES = "학생 화면에는 briefing 대신 rawNotes(원자료 메모)와 task(과제 안내)만 보인다. "
            + "briefing은 학생에게 보이지 않는 정리된 사실(모범 기록·해설용)이다. rawNotes 조건:\n"
            + "(a) 담당 간호사(학생)가 근무 중에 적어 둔 짧은 메모 10~12개, 각각 {\"time\":\"HH:mm\",\"text\":\"…\"}. "
            + "배열 순서는 시간순이 아니게 2곳 이상 섞는다(예: 10:00 메모가 09:30 메모보다 앞).\n"
            + "(b) 환자 말은 정리하지 않은 원문 그대로 큰따옴표 안에 쓴다. 기본 케이스 수준으로 끊기고 반복되고 두서없게 쓴다: "
            + "환자 말(큰따옴표)이 들어간 관련 메모를 반드시 2~3개(한 메모에 몰지 말고 서로 다른 시각의 메모에 나눠서) 두고, 환자 말 전체에 말줄임표(…)를 3번 이상 넣고, "
            + "적어도 한 메모의 환자 말에서는 같은 낱말(2글자 이상)을 2~3번 되풀이한다"
            + "(예: \"아 그니까… 저기, 저기 저 사람이… 누가 나를 감시하는 것 같아요… 계속, 계속 봐요 계속\", "
            + "\"여기는… 여기는 아무도 없죠? 없죠?\"). 매끄럽게 정리된 한 문장(예: \"누가 나를 감시하는 것 같아요\")만 쓰지 않는다.\n"
            + "(c) 메모에 다음이 모두 있어야 한다: 근무 시작 인계(진단·입원 일수·현재 처방), 사건 장소와 시각, 관찰한 행동, "
            + "간호 중재(행동 그대로), PRN 투약(약명·용량·경로·투여 시각·사유·구강 확인), 재평가 시각과 결과, "
            + "다음 근무에 넘길 일(정규 투약 시각·관찰). 활력징후를 메모에 쓰면 vitals 값과 같아야 한다. "
            + "answerKey의 모든 요소(observationElements·medicationElements·sbarElements 각 영역)의 동의어 중 하나가 "
            + "관련 있는 메모 글에 글자 그대로 있어야 하고, '발언 인용' 동의어는 메모의 큰따옴표 안 환자 말에 있어야 한다.\n"
            + "(d) 이번 사건과 관련 없는 메모 2~3개를 섞고, 그런 메모에는 하나도 빠짐없이 반드시 \"irrelevant\":true와 "
            + "\"cues\"(그 메모에만 있는 낱말 2~4개)를 붙인다. irrelevant 표시가 없는 메모는 모두 이번 환자의 사건·인계·투약·"
            + "활력징후 메모여야 하고, 각 메모에 answerKey 동의어나 vitals 혈압 값(예: 118/76)이 하나 이상 있어야 한다. "
            + "다른 환자 일, 배식, 보호자 전화, 물품·비품 정리, 라운딩 특이사항 없음, 행정 업무처럼 이번 사건과 관계없는 메모를 "
            + "irrelevant 표시 없이 두지 않는다(이런 메모는 모두 irrelevant:true, 이 메모만 2~3개) "
            + "(예: 점심 배식, 보호자 전화로 면회 시간 문의, 다른 환자 채혈 지원). 관련 없는 메모에는 임상 수치를 쓰지 않는다. "
            + "cues 낱말은 그 메모에만 있는 구체적인 명사(예: 배식, 원무과, 면회, 혈당)로 고르고, "
            + "관련 있는 메모와 모범 기록 어디에도 쓰지 않는다(측정·침상·지원·확인·환자 같은 흔한 말 금지).\n"
            + "관련 없는 메모의 시각도 케이스 근무 시간 안이어야 한다(Day 07:00~15:30, Evening 15:00~23:30, Night 23:00~07:30). "
            + "식사·투약 같은 일과는 그 근무 시간대에 맞는 것만 쓴다: Day는 아침(07~09시)·점심(11~13시) 배식, "
            + "Evening은 저녁 배식(17~19시)·취침 전 약, Night는 배식 없이 수면·라운딩. "
            + "Evening에 점심 배식, Night에 식사 배식, Day에 저녁 배식을 쓰지 않는다.\n"
            + "(e) 판단 표현(예: 협조적, 불안해 보임)은 메모에 써도 되지만 모범 기록에는 쓰지 않는다.\n"
            + "메모는 짧은 메모체(예: '데이룸. 왔다 갔다 계속 서성거림, 손톱 물어뜯음')로 쓴다. "
            + "모범 기록은 메모 문장을 그대로 옮기지 말고 필요한 사실만 골라 자연스러운 문장으로 새로 쓴다(환자 말 인용은 그대로 가능). "
            + "순서: rawNotes를 먼저 쓰고, answerKey의 모든 동의어는 rawNotes(관련 있는 메모)에 실제로 쓴 낱말에서 고른다"
            + "(예: 메모에 '차분해짐'이라고 썼으면 재평가 결과=차분|안정, 메모에 '안정'이 없으면 '안정'을 동의어로 쓰지 않는다). "
            + "답하기 전에 observationElements·medicationElements·sbarElements의 요소 하나하나가 메모 글에 글자 그대로 있는지, "
            + "관계없는 메모마다 irrelevant:true와 cues가 있는지, 환자 말에 말줄임표와 낱말 되풀이가 있는지 확인한다.\n"
            + "task에는 근무 날짜·근무 구분·시간, '아래 메모에서 필요한 사실만 골라 (1) 사건의 관찰 기록 1건 (2) PRN 투약 기록 1건 "
            + "(3) 근무 끝 SBAR 인계를 작성' 안내만 쓰고, 사건 내용이나 정답은 쓰지 않는다.\n";

    /** 생성 응답의 형식 견본 (값은 예시 자리일 뿐 임상 값이 아님). */
    static final String TEMPLATE = """
            {"title":"짧은 제목","difficulty":"기본","objectives":["학습 목표"],
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
             "briefing":"사건 시각·장소·행동·환자 발언(큰따옴표)·담당 간호사인 학생이 한 간호(행동 그대로)·PRN 투약 시각을 담은 문장. 끝에 과제 안내",
             "task":"2026-10-20 Day 근무(07:00~15:30). 아래 메모에서 필요한 사실만 골라 (1) … (2) … (3) …",
             "rawNotes":[{"time":"07:20","text":"인계 받음. 진단·입원 일수·현재 처방 메모"},
                         {"time":"HH:mm","text":"장소. 관찰한 행동 메모"},
                         {"time":"HH:mm","text":"말 걸었더니 → \\"두서없는 환자 말 원문\\""},
                         {"time":"HH:mm","text":"재평가 시각·결과 메모 (시간 순서를 섞은 자리)"},
                         {"time":"HH:mm","text":"간호 중재 메모 + 그때 한 환자 말 \\"끊기고… 되풀이되는, 되풀이되는 말 원문\\""},
                         {"time":"HH:mm","text":"점심 배식 도움 같은 관련 없는 메모","irrelevant":true,"cues":["점심","배식"]},
                         {"time":"HH:mm","text":"PRN 약명 용량 경로 투여, 사유, 구강 확인 메모"},
                         {"time":"HH:mm","text":"활력징후 메모(vitals와 같은 값)"},
                         {"time":"HH:mm","text":"보호자 전화 같은 관련 없는 메모","irrelevant":true,"cues":["보호자","면회"]},
                         {"time":"HH:mm","text":"다음 근무에 넘길 일 메모(정규 투약 시각·관찰)"}],
             "answerKey":{"observationElements":["장소=데이룸|휴게실","행동=서성|왔다 갔다|손톱","발언 인용=답답|무서워","간호=1:1|일대일|지지|조용한 곳","투약=로라제팜|Lorazepam&1mg|1 mg&PO|경구","재평가=30분|재평가"],
                          "medicationElements":["약명=영문약명|한글약명","용량=숫자","단위=mg","경로=PO|경구","시각=HH:mm"],
                          "sbarElements":{"situation":["불안 사건=불안|초조"],"background":["진단=진단명"],"assessment":["재평가 결과=안정|불안 감소"],"recommendation":["다음 근무 관찰=관찰"]}},
             "modelAnswer":{"observation":"모범 관찰 문장","medication":"모범 투약 문장","sbar":"S: …\\nB: …\\nA: …\\nR: …",
                            "observationRecord":{"time":"HH:mm","shift":"DAY","tags":["불안 급증","PRN 투여"],
                                                 "narrative":"장소·행동·\\"환자 발언\\"·간호·투약(약명 용량 경로 시각)·재평가 시각을 담은 관찰 문장",
                                                 "intervention":"한 간호"},
                            "medicationRecord":{"orderId":"G-PRN1","drugName":"영문약명","dose":"숫자","unit":"mg","route":"PO",
                                                "administeredTime":"HH:mm","result":"GIVEN","reason":"투약 사유(관찰한 행동)",
                                                "orderBasis":"PRN 처방(…)","oralCheck":true,"reassessmentPlan":"HH:mm 재평가"},
                            "sbarReport":{"situation":"…","background":"…","assessment":"…",
                                          "recommendations":[{"time":"16:00","intervention":"…"},{"time":"18:00","intervention":"… 관찰"}]}}}
            """;

    /**
     * 이번 요청의 환자·사건 조건. 매번 성별·나이·사건 장소를 바꿔 같은 환자가 반복되지 않게 한다 (polish 11).
     * 기본 케이스(PC-01)의 이름·나이·병실은 쓰지 못하게 알린다.
     */
    String conditions() {
        String sex = random.nextBoolean() ? "F" : "M";
        int age = MIN_AGE + random.nextInt(MAX_AGE - MIN_AGE + 1);
        java.util.Set<String> avoid = new java.util.HashSet<>(recentPlaces);
        avoid.addAll(requestedPlaces);
        String place = pick(PLACES, avoid);
        String surname = pick(SURNAMES, recentSurnames);
        String diagnosis = pick(DIAGNOSES, recentDiagnoses.stream().limit(1).toList());
        String room;
        do {
            room = (2 + random.nextInt(6)) + "0" + (1 + random.nextInt(8)) + "호";
        } while (recentRooms.contains(room));
        remember(recentDiagnoses, diagnosis);
        remember(requestedPlaces, place);
        remember(recentSurnames, surname);
        // 사건 시각도 매번 다르게 (08:00~19:55, 5분 단위)
        String time = String.format("%02d:%02d", 8 + random.nextInt(12), 5 * random.nextInt(12));
        StringBuilder sb = new StringBuilder("이번 환자 조건: 성별 ").append(sex).append(", 나이 ").append(age)
                .append("세, 성씨 ").append(surname).append(", 사건 장소 ").append(place)
                .append(", 사건 시각 ").append(time).append(" 무렵, 병실 ").append(room).append(", 진단 ").append(diagnosis).append(".\n")
                .append("이름(위 성씨로 시작)·입원 일수·환자 발언은 매번 새로 정한다(흔한 예시 반복 금지). 병실·진단은 위 조건을 그대로 쓴다. ")
                .append("진단은 허용 약물로 치료하는 범위(조현병, 조현정동장애, 양극성 장애) 안에서 고른다. ")
                .append("나이는 ").append(MIN_AGE).append("~").append(MAX_AGE).append("세.\n");
        if (!recentNames.isEmpty()) {
            sb.append("최근에 만든 이름은 쓰지 않는다: ").append(String.join(", ", recentNames)).append(".\n");
        }
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
        if (c.patient().name() != null && recentNames.stream().anyMatch(n -> same(n, c.patient().name()))) {
            out.add("patient.name이 최근에 만든 환자와 같음: " + c.patient().name());
        }
        if (c.patient().room() != null && recentRooms.stream().anyMatch(x -> same(x, c.patient().room()))) {
            out.add("patient.room이 최근 케이스와 같음: " + c.patient().room() + ", 요청한 병실을 쓴다");
        }
        String place = placeOf(c);
        if (place != null && recentPlaces.stream().anyMatch(x -> same(x, place))) {
            out.add("answerKey 장소(" + place + ")가 최근 케이스와 같음, 요청한 사건 장소를 쓴다");
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

    /** 정답 키 '장소'의 첫 동의어 (없으면 null). */
    static String placeOf(PatientCase c) {
        if (c.answerKey() == null) return null;
        return app.scoring.ElementPattern.parseAll(c.answerKey().observationElements()).stream()
                .filter(e -> e.label().equals("장소")).map(app.scoring.ElementPattern::expected).findFirst().orElse(null);
    }

    /** 최근 목록에 없는 후보를 무작위로 고른다 (모두 최근이면 아무거나). */
    private String pick(List<String> all, java.util.Collection<String> recent) {
        List<String> left = all.stream().filter(x -> !recent.contains(x)).toList();
        List<String> from = left.isEmpty() ? all : left;
        return from.get(random.nextInt(from.size()));
    }

    private static void remember(java.util.ArrayDeque<String> list, String v) {
        if (v == null) return;
        list.remove(v);
        list.addFirst(v);
        while (list.size() > RECENT_MAX) list.removeLast();
    }

    /** 생성에 성공한 케이스의 이름을 기억한다 (다음 생성에서 피한다). */
    private void remember(PatientCase c) {
        if (c.patient() != null) {
            remember(recentNames, c.patient().name());
            remember(recentRooms, c.patient().room());
        }
        remember(recentPlaces, placeOf(c));
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
        return userPrompt("", problems, null);
    }

    /**
     * 생성 요청 글. 다시 요청할 때는 직전 케이스(previous)와 문제 목록을 함께 보내, 처음부터 새로 만들지 않고
     * 문제만 고치게 한다(새로 만들면 고친 곳 대신 다른 곳이 어긋나는 일이 잦다).
     */
    String userPrompt(String conditions, List<String> problems, String previous) {
        StringBuilder sb = new StringBuilder("사건 유형: ").append(EVENT_ANXIETY_PRN).append('\n');
        sb.append(conditions);
        sb.append("허용 약물: ").append(DomainJson.toJson(validator.allowedDrugs().all())).append('\n');
        if (!problems.isEmpty()) {
            sb.append("이전 응답의 문제를 고쳐 다시 만든다: ").append(String.join("; ", problems));
            if (previous != null) {
                sb.append("\n아래 이전 응답에서 위 문제만 고치고, 문제가 없는 부분(환자·메모·정답 키·모범 기록)은 그대로 둔 채 ")
                        .append("전체 JSON을 다시 답한다. 고친 뒤 다른 조건(메모 순서 섞임, 관련 없는 메모 표시, 정답 키 동의어가 메모에 있음, ")
                        .append("모범 기록은 메모를 그대로 옮기지 않음)이 깨지지 않았는지 확인한다.\n이전 응답: ").append(previous);
            }
        }
        return sb.toString();
    }
}

package app.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.domain.DomainJson;
import app.domain.PatientCase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T-30: CaseValidator 검증. 약물·용량은 모두 합성 값이다. */
class CaseValidatorTest {

    private static final AllowedDrugs ALLOWED = AllowedDrugs.load(
            CaseValidatorTest.class.getClassLoader(), "cases/allowed-drugs-test.json");
    private final CaseValidator validator = new CaseValidator(ALLOWED);

    /** 정답 키(장소 데이룸, 투약 시각 09:30)와 맞는 브리핑 */
    private static final String B = "09:30 데이룸에서 서성임";

    private static String json(String drug, String dose, String unit, String route, String time, String briefing) {
        return """
            {"id":"G-1","title":"합성","source":"AI_GENERATED",
             "patient":{"name":"가상환자","admissionDate":"2026-10-11","hospitalDay":10},
             "briefing":"%s",
             "vitals":[{"time":"%s","pulse":80},{"time":"10:00","pulse":82}],
             "observationLog":[{"time":"09:00","shift":"DAY","tags":["기타"],"narrative":"합성 관찰"},
                               {"time":"10:00","shift":"DAY","tags":["기타"],"narrative":"합성 관찰"}],
             "orders":[{"orderId":"O1","type":"REGULAR","drugName":"%s","dose":"%s","unit":"%s","route":"%s","scheduleTimes":["08:00"]}],
             "task":"2026-10-20 Day 근무. 합성 과제",
             "rawNotes":[{"time":"07:20","kind":"HANDOFF","text":"인계 받음. 입원 10일째. 정규약 {DRUG} PO 아침·16:00, 관찰 부탁"},{"time":"09:30","kind":"PRN","text":"데이룸. 서성거림. %s %smg PO 줌"},
                         {"time":"09:00","kind":"EVENT","text":"데이룸 합성 메모 1"},{"time":"10:00","kind":"REASSESS","text":"데이룸 합성 메모 2. VAS 2, RASS 0"},
                         {"time":"09:10","kind":"SPEECH","text":"데이룸. \\"아… 저기, 저기… 괜찮아요\\""},
                         {"time":"09:20","kind":"INTERVENTION","text":"데이룸 의자. \\"네… 네\\""},{"time":"12:00","kind":"IRRELEVANT","text":"점심 배식","irrelevant":true,"cues":["점심"]},
                         {"time":"14:00","kind":"IRRELEVANT","text":"보호자 전화","irrelevant":true,"cues":["보호자"]}],
             "answerKey":{"observationElements":["장소=데이룸"],"medicationElements":["약명=%s|TestDrug","용량=%s","경로=PO|경구","시각=09:30"],
                          "sbarElements":{"situation":["불안 사건=서성","투약={DRUG}|TestDrug"],"background":["입원 일수=10일","현재 처방={DRUG}|TestDrug","경로=PO"],
                                          "assessment":["재평가=vas 2","RASS=rass 0"],"recommendation":["16:00 투약=16:00&{DRUG}|TestDrug","관찰=관찰"]}}}
            """.formatted(briefing, time, drug, dose, unit, route, drug, dose, drug, dose).replace("{DRUG}", drug);
    }

    private List<String> check(String drug, String dose, String unit, String route, String time, String briefing) {
        PatientCase c = DomainJson.fromJson(json(drug, dose, unit, route, time, briefing), PatientCase.class);
        return validator.check(c);
    }

    @Test
    void SBAR_요소가_PC01보다_약하면_거부한다() {
        // Windows 시연 검토: "B: 조현병 환자임"처럼 짧은 SBAR로 만점이 나오지 않게 PC-01 수준 요소를 요구한다
        String weak = json("테스트약A", "3", "mg", "PO", "09:30", B).replaceAll("\"sbarElements\":\\{[^}]*\\}",
                "\"sbarElements\":{\"situation\":[\"불안 사건=서성\"],\"background\":[\"진단=합성\"],\"assessment\":[\"재평가=안정\"],\"recommendation\":[\"관찰=관찰\"]}");
        List<String> p = validator.check(DomainJson.fromJson(weak, PatientCase.class));
        assertTrue(p.contains("answerKey SBAR situation(S) 요소가 1개뿐임 (2개 이상: 사건 증상, PRN 투약 약명)"), p.toString());
        assertTrue(p.stream().anyMatch(x -> x.startsWith("answerKey SBAR background(B) 요소가 1개뿐임")), p.toString());
        assertTrue(p.contains("answerKey SBAR B 요소에 입원 일수(10일)가 없음 (예: \"입원 10일=10일\")"), p.toString());
        assertTrue(p.stream().anyMatch(x -> x.startsWith("answerKey SBAR B 요소에 현재 정규 처방 약명이 없음")), p.toString());
        assertTrue(p.stream().anyMatch(x -> x.startsWith("answerKey SBAR A 요소에 재평가 수치(숫자)가 없음")), p.toString());
        assertTrue(p.stream().anyMatch(x -> x.startsWith("answerKey SBAR R 요소에 다음 시각 정규 투약(HH:mm과 약명)이 없음")), p.toString());
        // PC-01은 이 기준을 통과한다 (기준이 PC-01 구성에서 나왔으므로)
        PatientCase pc01 = new CaseLoader().load("PC-01");
        List<String> q = new java.util.ArrayList<>();
        validator.checkSbarStrength(q, pc01);
        assertEquals(List.of(), q);
    }

    @Test
    void 정상_케이스는_통과한다() {
        assertEquals(List.of(), check("테스트약A", "3", "mg", "PO", "09:30", B));
        validator.validate(DomainJson.fromJson(json("테스트약a", "1", "mg", "po", "09:30", B), PatientCase.class));
    }

    @Test
    void 목록_밖_약물을_거부한다() {
        List<String> p = check("없는약", "3", "mg", "PO", "09:30", B);
        assertTrue(p.get(0).contains("허용 약물 목록에 없음"));
    }

    @Test
    void 용량_범위_밖을_거부한다() {
        assertTrue(check("테스트약A", "6", "mg", "PO", "09:30", B).get(0).contains("상한"));
        assertTrue(check("테스트약A", "0.5", "mg", "PO", "09:30", B).get(0).contains("하한"));
    }

    @Test
    void 용량_형식_단위_경로_오류를_거부한다() {
        assertTrue(check("테스트약A", "[확인필요]", "mg", "PO", "09:30", B).get(0).contains("숫자가 아님"));
        assertTrue(check("테스트약A", "3", "g", "PO", "09:30", B).get(0).contains("unit"));
        assertTrue(check("테스트약A", "3", "mg", "IV", "09:30", B).get(0).contains("route"));
    }

    @Test
    void 시각_형식과_필수_항목_누락을_거부한다() {
        assertTrue(check("테스트약A", "3", "mg", "PO", "25:99", B).get(0).contains("HH:mm"));
        assertTrue(check("테스트약A", "3", "mg", "PO", "09:30", "").get(0).contains("briefing"));
        CaseLoadException e = assertThrows(CaseLoadException.class,
                () -> validator.validate(DomainJson.fromJson("{\"title\":\"x\"}", PatientCase.class)));
        assertTrue(e.getMessage().contains("id 누락"));
        assertFalse(validator.check(null).isEmpty());
    }

    @Test
    void 허용_약물_목록이_비어_있으면_생성을_끈다() {
        assertFalse(new CaseValidator(new AllowedDrugs(List.of())).generationAvailable());
        assertFalse(new CaseValidator(AllowedDrugs.parse("{\"drugs\": []}", "t")).generationAvailable());
        assertTrue(validator.generationAvailable());
        // 실제 파일은 팀이 채우기 전까지 비어 있어도 읽힌다.
        AllowedDrugs.load();
    }

    @Test
    void 허용_약물_파일_형식_오류를_거부한다() {
        assertThrows(CaseLoadException.class, () -> AllowedDrugs.parse("{ nope", "t"));
        assertThrows(CaseLoadException.class, () -> AllowedDrugs.parse("{\"drugs\":[{\"unit\":\"mg\"}]}", "t"));
        assertTrue(AllowedDrugs.load(getClass().getClassLoader(), "cases/없음.json").isEmpty());
    }

    /** 정상 케이스 JSON을 고쳐 한 가지만 어긋나게 한다. */
    private List<String> checkJson(String from, String to) {
        String j = json("테스트약A", "3", "mg", "PO", "09:30", B);
        assertTrue(j.contains(from), from);
        return validator.check(DomainJson.fromJson(j.replace(from, to), PatientCase.class));
    }

    @Test
    void 활력징후가_생리적으로_가능한_범위_밖이면_거부한다() {
        assertTrue(checkJson("\"pulse\":80", "\"pulse\":80,\"systolic\":120,\"diastolic\":80,\"respiration\":18,\"temperature\":36.5,\"spo2\":98")
                .isEmpty());
        assertTrue(checkJson("\"pulse\":80", "\"pulse\":200").stream().anyMatch(x -> x.contains("pulse(맥박) 200") && x.contains("40~160")));
        assertTrue(checkJson("\"pulse\":80", "\"pulse\":80,\"systolic\":60").stream().anyMatch(x -> x.contains("수축기")));
        assertTrue(checkJson("\"pulse\":80", "\"pulse\":80,\"diastolic\":135").stream().anyMatch(x -> x.contains("이완기")));
        assertTrue(checkJson("\"pulse\":80", "\"pulse\":80,\"respiration\":6").stream().anyMatch(x -> x.contains("호흡")));
        assertTrue(checkJson("\"pulse\":80", "\"pulse\":80,\"temperature\":42.0").stream().anyMatch(x -> x.contains("체온")));
        assertTrue(checkJson("\"pulse\":80", "\"pulse\":80,\"spo2\":75").stream().anyMatch(x -> x.contains("SpO2")));
        assertTrue(checkJson("\"pulse\":80", "\"pulse\":80,\"spo2\":100,\"temperature\":34.0").isEmpty()); // 경계값은 통과
    }

    @Test
    void 투약_정답_시각이_브리핑에_없으면_거부한다() {
        assertTrue(checkJson("09:30 데이룸", "10:15 데이룸").stream().anyMatch(x -> x.contains("투약 시각(09:30)이 briefing에 없음")));
    }

    @Test
    void 정답_장소가_브리핑에_없으면_거부한다() {
        assertTrue(checkJson("09:30 데이룸에서", "09:30 복도에서").stream().anyMatch(x -> x.contains("'장소'")));
        assertTrue(checkJson("\"장소=데이룸\"", "\"장소=복도|데이룸\"").isEmpty()); // 동의어 중 하나면 된다
    }

    @Test
    void 발언_인용_동의어는_브리핑의_따옴표_안에_있어야_한다() {
        String key = "\"장소=데이룸\",\"발언 인용=무서워\"";
        assertTrue(checkJson("\"장소=데이룸\"", key).stream().anyMatch(x -> x.contains("'발언 인용'")));
        // 따옴표 밖에만 있으면 거부
        String outside = B + " 무서워하는 모습";
        assertTrue(checkJson("\"briefing\":\"" + B + "\"", "\"briefing\":\"" + outside + "\"")
                .stream().noneMatch(x -> x.contains("'발언 인용'"))); // 발언 인용 요소가 없는 키는 검사하지 않음
        String j = json("테스트약A", "3", "mg", "PO", "09:30", outside).replace("\"장소=데이룸\"", key);
        assertTrue(validator.check(DomainJson.fromJson(j, PatientCase.class)).stream().anyMatch(x -> x.contains("'발언 인용'")));
        // 따옴표 안에 있으면 통과 (곧은·둥근 따옴표 모두)
        for (String quote : List.of(B + " \\\"무서워요\\\"라고 말함", B + " “무서워요”라고 말함")) {
            // 학생은 원자료 메모를 보므로 메모의 따옴표 안에도 같은 말이 있어야 한다
            String ok = json("테스트약A", "3", "mg", "PO", "09:30", quote).replace("\"장소=데이룸\"", key)
                    .replace("합성 메모 1", quote.substring(B.length() + 1));
            assertEquals(List.of(), validator.check(DomainJson.fromJson(ok, PatientCase.class)), quote);
        }
        assertEquals("무서워요 ", CaseValidator.quoted("그는 “무서워요”라고 말함"));
    }

    @Test
    void 정답_키_약명은_한글과_영어_경로는_PO와_경구를_모두_가져야_한다() {
        assertTrue(checkJson("약명=테스트약A|TestDrug", "약명=TestDrug").stream().anyMatch(x -> x.contains("한글·영어 약명")));
        assertTrue(checkJson("약명=테스트약A|TestDrug", "약명=테스트약A").stream().anyMatch(x -> x.contains("한글·영어 약명")));
        assertTrue(checkJson("경로=PO|경구", "경로=PO").stream().anyMatch(x -> x.contains("PO와 경구")));
        assertTrue(checkJson("\"경로=PO|경구\",", "").stream().anyMatch(x -> x.contains("경로(PO|경구)가 없음")));
        // 관찰 핵심 사실의 '투약' 요소도 본다 (AI가 "Lorazepam 1mg po"처럼 영어로만 쓴 경우)
        assertTrue(checkJson("\"장소=데이룸\"", "\"장소=데이룸\",\"투약=Lorazepam 1mg po\"").stream()
                .anyMatch(x -> x.contains("투약 약명")));
        assertTrue(checkJson("\"장소=데이룸\"", "\"장소=데이룸\",\"투약=로라제팜|Lorazepam&1mg&po\"").stream()
                .anyMatch(x -> x.contains("투약 경로")));
        String j = json("테스트약A", "3", "mg", "PO", "09:30", B)
                .replace("\"장소=데이룸\"", "\"장소=데이룸\",\"투약=로라제팜|Lorazepam&1mg|1 mg&PO|경구\"")
                .replace("합성 메모 2", "로라제팜 1mg PO"); // 원자료 메모에도 같은 투약이 있어야 한다
        assertEquals(List.of(), validator.check(DomainJson.fromJson(j, PatientCase.class)));
    }

    @Test
    void PC01도_새_검사를_통과한다() {
        PatientCase pc01 = new CaseLoader().load("PC-01");
        List<String> p = new CaseValidator(AllowedDrugs.load()).check(pc01);
        assertTrue(p.stream().noneMatch(x -> x.contains("briefing") || x.contains("한글") || x.contains("경구") || x.contains("범위")), p.toString());
    }
}

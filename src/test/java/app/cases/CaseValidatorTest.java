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

    private static String json(String drug, String dose, String unit, String route, String time, String briefing) {
        return """
            {"id":"G-1","title":"합성","source":"AI_GENERATED",
             "patient":{"name":"가상환자"},
             "briefing":"%s",
             "vitals":[{"time":"%s","pulse":80}],
             "orders":[{"orderId":"O1","type":"REGULAR","drugName":"%s","dose":"%s","unit":"%s","route":"%s","scheduleTimes":["08:00"]}]}
            """.formatted(briefing, time, drug, dose, unit, route);
    }

    private List<String> check(String drug, String dose, String unit, String route, String time, String briefing) {
        PatientCase c = DomainJson.fromJson(json(drug, dose, unit, route, time, briefing), PatientCase.class);
        return validator.check(c);
    }

    @Test
    void 정상_케이스는_통과한다() {
        assertEquals(List.of(), check("테스트약A", "3", "mg", "PO", "09:30", "상황"));
        validator.validate(DomainJson.fromJson(json("테스트약a", "1", "mg", "po", "09:30", "상황"), PatientCase.class));
    }

    @Test
    void 목록_밖_약물을_거부한다() {
        List<String> p = check("없는약", "3", "mg", "PO", "09:30", "상황");
        assertTrue(p.get(0).contains("허용 약물 목록에 없음"));
    }

    @Test
    void 용량_범위_밖을_거부한다() {
        assertTrue(check("테스트약A", "6", "mg", "PO", "09:30", "상황").get(0).contains("상한"));
        assertTrue(check("테스트약A", "0.5", "mg", "PO", "09:30", "상황").get(0).contains("하한"));
    }

    @Test
    void 용량_형식_단위_경로_오류를_거부한다() {
        assertTrue(check("테스트약A", "[확인필요]", "mg", "PO", "09:30", "상황").get(0).contains("숫자가 아님"));
        assertTrue(check("테스트약A", "3", "g", "PO", "09:30", "상황").get(0).contains("unit"));
        assertTrue(check("테스트약A", "3", "mg", "IV", "09:30", "상황").get(0).contains("route"));
    }

    @Test
    void 시각_형식과_필수_항목_누락을_거부한다() {
        assertTrue(check("테스트약A", "3", "mg", "PO", "25:99", "상황").get(0).contains("HH:mm"));
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
}

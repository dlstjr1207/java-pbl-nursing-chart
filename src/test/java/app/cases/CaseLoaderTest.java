package app.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.domain.CaseSource;
import app.domain.PatientCase;
import org.junit.jupiter.api.Test;

/** T-02: CaseLoader 검증. 케이스 값은 모두 합성 값이다. */
class CaseLoaderTest {

    private final CaseLoader loader = new CaseLoader();

    @Test
    void 합성_케이스를_읽는다() {
        PatientCase c = loader.load("TEST-01");
        assertEquals("TEST-01", c.id());
        assertEquals(CaseSource.FIXED, c.source());
        assertEquals(2, c.orders().size());
        assertEquals("테스트환자", c.patient().name());
    }

    @Test
    void 파일이_없으면_없다고_알린다() {
        CaseLoadException e = assertThrows(CaseLoadException.class, () -> loader.load("PC-99"));
        assertTrue(e.getMessage().contains("cases/PC-99.json"));
        assertFalse(loader.exists("PC-99"));
        assertTrue(loader.exists("TEST-01"));
    }

    @Test
    void 잘못된_ID는_거부한다() {
        assertThrows(CaseLoadException.class, () -> loader.load("../x"));
        assertThrows(CaseLoadException.class, () -> loader.load(null));
    }

    @Test
    void 깨진_JSON은_거부한다() {
        CaseLoadException e = assertThrows(CaseLoadException.class, () -> loader.parse("{ not json", "t"));
        assertTrue(e.getMessage().contains("JSON 형식"));
    }

    @Test
    void 빈_내용은_거부한다() {
        assertThrows(CaseLoadException.class, () -> loader.parse("", "t"));
    }

    @Test
    void 필수_항목_누락을_원인과_함께_거부한다() {
        String json = "{\"title\":\"\",\"source\":\"UNKNOWN\",\"orders\":[{\"drugName\":\"A\"}]}";
        CaseLoadException e = assertThrows(CaseLoadException.class, () -> loader.parse(json, "t"));
        String msg = e.getMessage();
        assertTrue(msg.contains("id 누락"));
        assertTrue(msg.contains("title 누락"));
        assertTrue(msg.contains("source"));
        assertTrue(msg.contains("patient 누락"));
        assertTrue(msg.contains("orders[0].orderId"));
        assertTrue(msg.contains("orders[0].type"));
    }

    @Test
    void DEMO_케이스를_읽는다() {
        // T-03: DEMO 케이스가 로더를 통과하고 DEMO로 표시된다. 채점 정답 키는 없다.
        PatientCase c = loader.load("demo");
        assertEquals(CaseSource.DEMO, c.source());
        assertEquals("DEMO", c.reviewStatus());
        assertEquals("김하늘", c.patient().name());
        assertEquals(5, c.orders().size());
        assertTrue(c.title().contains("DEMO"));
        assertEquals(null, c.answerKey());
        assertEquals(null, c.modelAnswer());
    }

    @Test
    void 잘못된_형식의_값은_거부한다() {
        String json = "{\"id\":\"X\",\"orders\":\"문자열\"}";
        assertThrows(CaseLoadException.class, () -> loader.parse(json, "t"));
    }
}

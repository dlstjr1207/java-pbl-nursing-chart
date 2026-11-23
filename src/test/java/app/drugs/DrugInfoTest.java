package app.drugs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.AllowedDrugs;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 약 정보(단계 7) 테스트. 실제 API는 부르지 않고 2026-11-23에 받은 실제 응답을 줄인 샘플(src/test/resources/drugs)을 쓴다.
 * 샘플은 선택 품목만 남기고 주의사항(NB·PN) 칸을 생략했다.
 */
class DrugInfoTest {

    private static final Map<String, String> SAMPLE = Map.of(
            "아리피졸정10밀리그램", "aripiprazole", "산도스올란자핀정10밀리그램", "olanzapine",
            "명인탄산리튬정300밀리그램", "lithium", "스리반정1밀리그램", "lorazepam");

    private static String read(String name) {
        try (InputStream in = DrugInfoTest.class.getClassLoader().getResourceAsStream("drugs/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final DrugInfoUpdater.Fetcher SAMPLES = word -> read("sample-" + SAMPLE.get(word) + ".json");

    @Test
    void 샘플응답에서_효능효과와_용법용량을_평문으로_꺼낸다() {
        DrugInfo d = DrugInfoParser.parse(read("sample-lithium.json"), "Lithium", "198500638");
        assertEquals("명인탄산리튬정300밀리그램", d.itemName());
        assertEquals("명인제약(주)", d.company());
        assertEquals("조증·조울증의 치료 및 예방적 유지치료", d.efficacy());
        assertEquals("성인 : 탄산리튬으로서 1일 0.6-1.8g을 1일 3회 분할 경구투여한다.\n연령, 증상에 따라 적절히 증감한다.",
                d.dosage());
        assertFalse(d.dosage().contains("<"), "XML 태그가 남지 않는다");
    }

    @Test
    void 다른_품목이_섞여_있어도_지정한_품목만_고른다() {
        // 샘플 첫 항목은 다른 제품(리단정)이다
        DrugInfo d = DrugInfoParser.parse(read("sample-lithium.json"), "Lithium", "198500638");
        assertEquals("198500638", d.itemSeq());
        assertThrows(IllegalArgumentException.class,
                () -> DrugInfoParser.parse(read("sample-lithium.json"), "Lithium", "000000000"));
    }

    @Test
    void 키_미등록_오류응답은_이유를_알린다() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> DrugInfoParser.parse(read("sample-error-30.json"), "Lithium", "198500638"));
        assertTrue(e.getMessage().contains("등록되지 않은 서비스키"));
        assertTrue(e.getMessage().contains("30"));
    }

    @Test
    void 갱신명령은_네_약을_모아_파일형식으로_저장한다() throws IOException {
        List<DrugInfo> drugs = DrugInfoUpdater.collect(SAMPLES);
        assertEquals(List.of("Aripiprazole", "Olanzapine", "Lithium", "Lorazepam"),
                drugs.stream().map(DrugInfo::drug).toList());
        String json = DrugInfoUpdater.toJson(drugs, LocalDate.of(2026, 11, 23));
        var catalog = new DrugInfoCatalog(DrugInfoCatalog.parse(json), AllowedDrugs.load());
        assertEquals("2026-11-23", catalog.fetchedAt());
        assertTrue(catalog.find("Lorazepam").orElseThrow().dosage().contains("1일 1 ∼ 4 mg"));
    }

    @Test
    void 갱신명령은_한_약이라도_실패하면_멈춘다() {
        DrugInfoUpdater.Fetcher broken = word -> word.startsWith("스리반") ? read("sample-error-30.json") : SAMPLES.fetch(word);
        var e = assertThrows(IOException.class, () -> DrugInfoUpdater.collect(broken));
        assertTrue(e.getMessage().startsWith("Lorazepam"));
    }

    @Test
    void 저장된_파일은_PC01_처방약명으로_찾는다() {
        DrugInfoCatalog c = DrugInfoCatalog.load();
        assertEquals(4, c.all().size());
        // PC-01 처방 약명과 별칭
        assertEquals("Lithium", c.find("Lithium carbonate").orElseThrow().drug());
        assertEquals("Lorazepam", c.find("로라제팜").orElseThrow().drug());
        assertEquals("Aripiprazole", c.find("Aripiprazole").orElseThrow().drug());
        assertEquals("Olanzapine", c.find("Olanzapine").orElseThrow().drug());
        assertTrue(c.find("Haloperidol").isEmpty());
        assertTrue(c.describe("Haloperidol").contains("저장되어 있지 않습니다"));
        String text = c.describe("Lithium carbonate");
        assertTrue(text.startsWith("제품명: 명인탄산리튬정300밀리그램"));
        assertTrue(text.contains("[효능효과]") && text.contains("[용법용량]"));
    }

    /** 저장된 허가정보 문장이 근거표에서 확인한 용량 범위(allowed-drugs.json)와 같은 내용을 담는지. 다르면 사람이 근거표를 다시 본다. */
    @Test
    void 저장된_용법용량은_근거표_확인값과_어긋나지_않는다() {
        DrugInfoCatalog c = DrugInfoCatalog.load();
        assertTrue(c.find("Aripiprazole").orElseThrow().dosage().contains("1일 10-30 mg"));   // E-20, 10~30
        assertTrue(c.find("Olanzapine").orElseThrow().dosage().contains("1일 5 mg 에서 20 mg"));  // E-28, 5~20
        assertTrue(c.find("Lithium").orElseThrow().dosage().contains("1일 0.6-1.8g을 1일 3회 분할")); // E-21
        assertTrue(c.find("Lorazepam").orElseThrow().dosage().contains("1일 1 ∼ 4 mg"));     // E-29, 1~4
    }

    @Test
    void 문서XML이_아니면_태그만_걷어낸다() {
        assertEquals("", DrugDocText.toText(null));
        assertEquals("가\n나", DrugDocText.toText("<p>가</p><p>나&nbsp;</p>"));
    }
}

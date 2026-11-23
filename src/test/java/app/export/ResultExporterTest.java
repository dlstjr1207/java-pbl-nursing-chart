package app.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import app.domain.Attempt;
import app.domain.SbarRecommendation;
import app.storage.InMemoryAttemptRepository;
import app.ui.AppState;
import app.ui.SbarController;
import app.ui.Screen;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 실습 결과 내보내기 (T-26). DEMO 케이스와 합성 값만 쓴다. */
class ResultExporterTest {
    @TempDir Path dir;

    private Attempt attempt() {
        AppState state = new AppState();
        state.login("테스터");
        state.startPractice(new CaseLoader().load("demo"));
        state.go(Screen.SBAR);
        return new SbarController(state, new InMemoryAttemptRepository(), () -> 1L)
                .submit("상황 문장", "배경", "평가", List.of(new SbarRecommendation("09:00", "재측정")))
                .attempt();
    }

    @Test
    void 입력과_점검_결과를_한글_그대로_UTF8로_저장한다() throws Exception {
        Path f = dir.resolve("out.txt");
        assertNull(new ResultExporter().export(attempt(), f));
        String text = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(text.contains("S: 상황 문장"));
        assertTrue(text.contains("R: 09:00 재측정"));
        assertTrue(text.contains("[점검 결과]"));
        assertTrue(text.contains("교육용 예시"));
    }

    @Test
    void 없는_폴더나_빈_선택은_안내_문구를_돌려준다() {
        ResultExporter e = new ResultExporter();
        assertNotNull(e.export(attempt(), dir.resolve("없음/out.txt")));
        assertNotNull(e.export(null, dir.resolve("a.txt")));
        assertNotNull(e.export(attempt(), null));
        assertNotNull(e.export(attempt(), dir)); // 폴더를 파일로 쓰려는 경우
    }

    @Test
    void 파일_이름_제안은_안전한_문자만_쓴다() {
        String n = ResultExporter.suggestedFileName(attempt());
        assertTrue(n.startsWith("practice-") && n.endsWith(".txt"));
        assertEquals(n, n.replaceAll("[^A-Za-z0-9_.-]", "_"));
    }
}

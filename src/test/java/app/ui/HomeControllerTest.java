package app.ui;

import static org.junit.jupiter.api.Assertions.*;

import app.cases.CaseLoader;
import app.domain.CaseSource;
import org.junit.jupiter.api.Test;

class HomeControllerTest {
    private AppState loggedIn() {
        AppState s = new AppState();
        s.login("테스트사용자");
        return s;
    }

    @Test
    void PC01이_없으면_DEMO가_기본_케이스다() {
        HomeController c = new HomeController(loggedIn(), app.cases.WithoutPc01.loader());
        assertEquals("demo", c.defaultCaseId());
        assertTrue(c.defaultCaseDescription().contains("DEMO"));
    }

    @Test
    void PC01이_있으면_PC01이_기본_케이스이고_DEMO_문구가_없다() {
        AppState s = loggedIn();
        HomeController c = new HomeController(s, new CaseLoader());
        assertEquals("PC-01", c.defaultCaseId());
        assertNull(c.startDefaultPractice());
        assertEquals(CaseSource.FIXED, s.currentCase().source());
        assertTrue(!s.notices().contains(AppState.DEMO_NOTICE));
    }

    @Test
    void 실습을_시작하면_환자_정보_화면으로_가고_DEMO_문구가_붙는다() {
        AppState s = loggedIn();
        HomeController c = new HomeController(s, app.cases.WithoutPc01.loader());
        assertNull(c.startDefaultPractice());
        assertEquals(Screen.PATIENT, s.current());
        assertEquals(CaseSource.DEMO, s.currentCase().source());
        assertTrue(s.notices().contains(AppState.DEMO_NOTICE));
    }

    @Test
    void AI_생성은_비활성이고_이유가_있다() {
        HomeController c = new HomeController(loggedIn(), new CaseLoader());
        assertFalse(c.aiGenerationAvailable());
        assertFalse(c.aiGenerationReason().isBlank());
    }

    @Test
    void 케이스를_못_읽으면_오류_문구를_주고_화면은_그대로다() {
        AppState s = loggedIn();
        // 케이스가 없는 클래스패스: PC-01도 demo도 없다
        CaseLoader empty = new CaseLoader(new ClassLoader(null) {});
        HomeController c = new HomeController(s, empty);
        String error = c.startDefaultPractice();
        assertNotNull(error);
        assertEquals(Screen.HOME, s.current());
        assertNull(s.currentCase());
    }

    @Test
    void 로그아웃_상태에서는_실습을_시작할_수_없다() {
        HomeController c = new HomeController(new AppState(), new CaseLoader());
        assertThrows(IllegalStateException.class, c::startDefaultPractice);
    }
}

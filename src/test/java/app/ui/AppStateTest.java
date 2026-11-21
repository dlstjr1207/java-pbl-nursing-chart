package app.ui;

import static org.junit.jupiter.api.Assertions.*;

import app.cases.CaseLoader;
import app.domain.PatientCase;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppStateTest {
    private PatientCase demo() {
        return new CaseLoader().load("demo");
    }

    @Test
    void 시작은_로그인_화면이고_실습_화면은_막힌다() {
        AppState s = new AppState();
        assertEquals(Screen.LOGIN, s.current());
        assertFalse(s.go(Screen.HOME));
        assertFalse(s.go(Screen.PATIENT));
        assertEquals(Screen.LOGIN, s.current());
        assertEquals(List.of(AppState.EDU_NOTICE), s.notices());
    }

    @Test
    void 로그인하면_개인메인이고_케이스_없이는_실습_화면에_못_간다() {
        AppState s = new AppState();
        s.login("테스트사용자");
        assertEquals(Screen.HOME, s.current());
        assertEquals("사용자: 테스트사용자", s.userLabel());
        assertFalse(s.go(Screen.SBAR));
        assertFalse(s.go(Screen.LOGIN));
    }

    @Test
    void DEMO_케이스면_DEMO_문구가_표시되고_로그아웃하면_상태가_지워진다() {
        AppState s = new AppState();
        s.login("테스트사용자");
        s.startPractice(demo());
        assertEquals(Screen.PATIENT, s.current());
        assertTrue(s.notices().contains(AppState.DEMO_NOTICE));
        assertTrue(s.go(Screen.MEDICATION));
        s.logout();
        assertEquals(Screen.LOGIN, s.current());
        assertNull(s.currentCase());
        assertEquals("환자: 선택 안 함", s.patientLabel());
        assertEquals(List.of(AppState.EDU_NOTICE), s.notices());
    }

    @Test
    void 개인메인으로_돌아가면_실습이_끝난다() {
        AppState s = new AppState();
        s.login("a");
        s.startPractice(demo());
        assertTrue(s.go(Screen.HOME));
        assertNull(s.currentCase());
    }

    @Test
    void 로그인_없이_실습_시작은_거부한다() {
        assertThrows(IllegalStateException.class, () -> new AppState().startPractice(demo()));
    }
}

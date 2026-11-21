package app.auth;

import static org.junit.jupiter.api.Assertions.*;

import app.storage.InMemoryUserRepository;
import app.ui.AppState;
import app.ui.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S1(가입·로그인·로그아웃)과 S8(사용자 분리)의 인증 부분. 메모리 저장소를 쓴다. */
class AuthServiceTest {

    private InMemoryUserRepository repo;
    private AppState state;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        repo = new InMemoryUserRepository();
        state = new AppState();
        auth = new AuthService(repo, state);
    }

    @Test
    void signupStoresHashNotPlaintext() {
        var r = auth.signup("nurse01", "password123", "가상학생");
        assertTrue(r.success());
        User saved = repo.findByLoginId("nurse01").orElseThrow();
        assertNotEquals("password123", saved.passwordHash());
        assertFalse(saved.passwordHash().contains("password123"));
    }

    @Test
    void signupRejectsInvalidInputPerField() {
        var r = auth.signup("ab", "short", " ");
        assertFalse(r.success());
        assertTrue(r.errors().keySet().containsAll(java.util.List.of("loginId", "password", "name")));
        assertTrue(repo.findByLoginId("ab").isEmpty());
    }

    @Test
    void duplicateSignupIsRejectedOnLoginIdField() {
        assertTrue(auth.signup("nurse01", "password123", "가상학생").success());
        var dup = auth.signup("nurse01", "otherpass99", "다른학생");
        assertFalse(dup.success());
        assertTrue(dup.errors().containsKey("loginId"));
        // 기존 계정은 그대로, 비밀번호도 바뀌지 않는다.
        assertTrue(auth.login("nurse01", "password123").success());
    }

    @Test
    void loginFailureMessageDoesNotRevealWhichIsWrong() {
        auth.signup("nurse01", "password123", "가상학생");
        var wrongPw = auth.login("nurse01", "wrongpass123");
        var wrongId = auth.login("nobody01", "password123");
        assertFalse(wrongPw.success());
        assertFalse(wrongId.success());
        assertEquals(wrongPw.message(), wrongId.message());
        assertEquals(Screen.LOGIN, state.current());
        assertTrue(auth.currentUser().isEmpty());
    }

    @Test
    void loginMovesToHomeAndLogoutClearsState() {
        auth.signup("nurse01", "password123", "가상학생");
        var r = auth.login("nurse01", "password123");
        assertTrue(r.success());
        assertEquals(Screen.HOME, state.current());
        assertEquals("가상학생", state.userName());
        assertEquals("nurse01", auth.currentUser().orElseThrow().loginId());

        auth.logout();
        assertTrue(auth.currentUser().isEmpty());
        assertNull(state.userName());
        assertNull(state.currentCase());
        assertEquals(Screen.LOGIN, state.current());
    }

    @Test
    void switchingUserLeavesNoPreviousUserState() {
        auth.signup("userA01", "password123", "사용자A");
        auth.signup("userB01", "password456", "사용자B");
        auth.login("userA01", "password123");
        auth.logout();
        auth.login("userB01", "password456");
        assertEquals("userB01", auth.currentUser().orElseThrow().loginId());
        assertEquals("사용자B", state.userName());
        assertNull(state.currentCase());
    }

    @Test
    void loginWhileAnotherUserLoggedInReplacesUser() {
        auth.signup("userA01", "password123", "사용자A");
        auth.signup("userB01", "password456", "사용자B");
        auth.login("userA01", "password123");
        // 실패한 로그인은 기존 로그인 상태를 건드리지 않는다.
        assertFalse(auth.login("userB01", "bad-password").success());
        assertEquals("userA01", auth.currentUser().orElseThrow().loginId());
        assertTrue(auth.login("userB01", "password456").success());
        assertEquals("userB01", auth.currentUser().orElseThrow().loginId());
    }

    @Test
    void nullInputsFailGenerically() {
        assertFalse(auth.login(null, null).success());
    }
}

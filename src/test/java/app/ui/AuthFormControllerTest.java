package app.ui;

import static org.junit.jupiter.api.Assertions.*;

import app.auth.AuthService;
import app.storage.InMemoryUserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** S1의 로그인·회원가입 화면 입력·실패·이동 검증 (화면 없이 컨트롤러로). */
class AuthFormControllerTest {
    private InMemoryUserRepository repo;
    private AppState state;
    private AuthFormController form;

    @BeforeEach
    void setUp() {
        repo = new InMemoryUserRepository();
        state = new AppState();
        form = new AuthFormController(new AuthService(repo, state), state);
    }

    @Test
    void 로그인_빈칸은_해당_칸_오류() {
        var r = form.login("", "");
        assertFalse(r.success());
        assertTrue(r.errors().containsKey("loginId"));
        assertTrue(r.errors().containsKey("password"));
        assertEquals(Screen.LOGIN, state.current());
    }

    @Test
    void 가입_화면_이동과_복귀() {
        form.openSignup();
        assertEquals(Screen.SIGNUP, state.current());
        form.openLogin();
        assertEquals(Screen.LOGIN, state.current());
    }

    @Test
    void 가입_형식_오류와_확인_불일치는_칸별로_표시하고_계정을_만들지_않는다() {
        var r = form.signup("ab", "short", "other", " ");
        assertFalse(r.success());
        assertTrue(r.errors().containsKey("loginId"));
        assertTrue(r.errors().containsKey("password"));
        assertTrue(r.errors().containsKey("name"));
        var r2 = form.signup("nurse01", "password123", "password124", "가상학생");
        assertEquals(Set.of("passwordConfirm"), r2.errors().keySet());
        assertTrue(repo.findByLoginId("nurse01").isEmpty());
    }

    @Test
    void 가입_후_로그인_실패_성공_흐름() {
        form.openSignup();
        var s = form.signup("nurse01", "password123", "password123", "가상학생");
        assertTrue(s.success());
        assertEquals(Screen.LOGIN, state.current());
        var bad = form.login("nurse01", "wrongpass1");
        assertFalse(bad.success());
        assertEquals(AuthService.LOGIN_FAILED, bad.message());
        assertEquals(bad.message(), form.login("nobody", "wrongpass1").message());
        assertTrue(form.login("nurse01", "password123").success());
        assertEquals(Screen.HOME, state.current());
    }

    @Test
    void 중복_아이디는_아이디_칸_오류() {
        form.signup("nurse01", "password123", "password123", "가상학생");
        var r = form.signup("nurse01", "password123", "password123", "다른학생");
        assertFalse(r.success());
        assertTrue(r.errors().containsKey("loginId"));
    }

    @Test
    void 시작_점검_설정_파일이_없으면_원인_안내(@TempDir Path dir) {
        var r = StartupCheck.check(dir.resolve("db.properties"));
        assertFalse(r.ok());
        assertTrue(r.problem().contains("DB 설정 파일이 없습니다"));
    }

    @Test
    void 시작_점검_값이_비면_항목을_알려_주고_비밀번호는_없다(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("db.properties");
        Files.writeString(f, "db.url=jdbc:mysql://localhost/x\ndb.user=u\ndb.password=SECRETVALUE\n");
        var r = StartupCheck.check(f);
        assertFalse(r.ok());
        assertTrue(r.problem().contains("db.test.url"));
        assertFalse(r.problem().contains("SECRETVALUE"));
    }
}

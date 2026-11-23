package app.ui;

import app.auth.AuthService;
import app.auth.SignupValidator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 로그인·회원가입 화면의 입력 검증과 이동 결정을 맡는다 (T-16, S1).
 * Swing에 의존하지 않아 화면 없이 테스트할 수 있다. 오류는 칸 이름(loginId, password, passwordConfirm, name)별로 돌려준다.
 */
public class AuthFormController {

    /**
     * 폼 처리 결과.
     *
     * @param errors  칸 이름 → 오류 문구 (해당 칸 옆에 표시)
     * @param message 칸에 연결되지 않는 안내(로그인 실패, 가입 완료 등). 없으면 null
     * @param success 성공 여부
     */
    public record Result(Map<String, String> errors, String message, boolean success) {}

    private final AuthService auth;
    private final AppState state;

    public AuthFormController(AuthService auth, AppState state) {
        this.auth = auth;
        this.state = state;
    }

    /** 로그인 시도. 빈 칸은 해당 칸 오류, 불일치는 공통 문구. 성공하면 AuthService가 개인 메인으로 옮긴다. */
    public Result login(String loginId, String password) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (loginId == null || loginId.isBlank()) errors.put("loginId", "아이디를 입력하세요.");
        if (password == null || password.isEmpty()) errors.put("password", "비밀번호를 입력하세요.");
        if (!errors.isEmpty()) return new Result(errors, null, false);
        AuthService.LoginResult r = auth.login(loginId.trim(), password);
        if (!r.success()) return new Result(Map.of(), r.message(), false);
        return new Result(Map.of(), null, true);
    }

    /**
     * 가입 시도. 형식·비밀번호 확인을 먼저 모두 검사하고, 통과해야 계정을 만든다.
     * 성공하면 로그인 화면으로 이동한다(가입 후 로그인).
     */
    public Result signup(String loginId, String password, String passwordConfirm, String name) {
        String id = loginId == null ? null : loginId.trim();
        Map<String, String> errors = new LinkedHashMap<>(SignupValidator.validate(id, password, name));
        if (password != null && !errors.containsKey("password") && !password.equals(passwordConfirm)) {
            errors.put("passwordConfirm", "비밀번호 확인이 일치하지 않습니다.");
        }
        if (!errors.isEmpty()) return new Result(errors, null, false);
        AuthService.SignupResult r = auth.signup(id, password, name);
        if (!r.success()) return new Result(new LinkedHashMap<>(r.errors()), null, false);
        state.go(Screen.LOGIN);
        return new Result(Map.of(), "가입이 완료되었습니다. 로그인하세요.", true);
    }

    /** 로그인 화면에서 회원가입 화면으로 이동한다. */
    public void openSignup() { state.go(Screen.SIGNUP); }

    /** 회원가입 화면에서 로그인 화면으로 돌아간다. */
    public void openLogin() { state.go(Screen.LOGIN); }
}

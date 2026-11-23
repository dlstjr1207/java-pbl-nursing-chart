package app.auth;

import app.storage.DuplicateLoginIdException;
import app.storage.UserRepository;
import app.ui.AppState;
import java.util.Map;
import java.util.Optional;

/**
 * 가입·로그인·로그아웃을 맡는 서비스 (S1, S8).
 * 현재 로그인 사용자를 하나만 들고 있으며, 로그아웃하면 사용자와 화면 상태(AppState)를 모두 지운다.
 * 기록 저장·조회는 이 사용자의 id로만 하도록 {@link #currentUser()}를 쓴다.
 */
public class AuthService {

    /** 로그인 실패 문구. 아이디·비밀번호 중 어느 쪽이 틀렸는지 구분하지 않는다 (D23). */
    public static final String LOGIN_FAILED = "아이디 또는 비밀번호가 올바르지 않습니다.";

    /**
     * 가입 결과.
     *
     * @param user   성공하면 만들어진 계정, 실패하면 null
     * @param errors 칸 이름(loginId, password, name) → 오류 문구. 성공이면 비어 있음
     */
    public record SignupResult(User user, Map<String, String> errors) {
        public boolean success() { return user != null; }
    }

    /**
     * 로그인 결과.
     *
     * @param user    성공하면 로그인한 계정, 실패하면 null
     * @param message 실패 문구. 성공이면 null
     */
    public record LoginResult(User user, String message) {
        public boolean success() { return user != null; }
    }

    private final UserRepository users;
    private final AppState state;
    private User currentUser;

    public AuthService(UserRepository users, AppState state) {
        this.users = users;
        this.state = state;
    }

    /** 현재 로그인한 사용자. 로그인 전이거나 로그아웃 후에는 비어 있다. */
    public Optional<User> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    /** 가입. 입력 검증 → 솔트·해시 → 저장. 중복 아이디는 loginId 칸 오류로 돌려준다. 가입만 하고 로그인하지 않는다. */
    public SignupResult signup(String loginId, String password, String name) {
        Map<String, String> errors = SignupValidator.validate(loginId, password, name);
        if (!errors.isEmpty()) {
            return new SignupResult(null, errors);
        }
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash(password, salt);
        try {
            User user = users.create(loginId, name.trim(), hash, salt);
            return new SignupResult(user, Map.of());
        } catch (DuplicateLoginIdException e) {
            return new SignupResult(null, Map.of("loginId", "이미 사용 중인 아이디입니다."));
        }
    }

    /** 로그인. 성공하면 상태를 개인 메인으로 옮긴다. 실패 원인은 구분하지 않는다. */
    public LoginResult login(String loginId, String password) {
        if (loginId == null || password == null) {
            return new LoginResult(null, LOGIN_FAILED);
        }
        Optional<User> found = users.findByLoginId(loginId);
        if (found.isEmpty() || !PasswordHasher.verify(password, found.get().salt(), found.get().passwordHash())) {
            return new LoginResult(null, LOGIN_FAILED);
        }
        // 다른 사용자가 이미 로그인한 상태면 먼저 비운다.
        logout();
        currentUser = found.get();
        state.login(currentUser.name());
        return new LoginResult(currentUser, null);
    }

    /** 로그아웃. 이전 사용자와 케이스·화면 상태를 지우고 로그인 화면으로 돌아간다. */
    public void logout() {
        currentUser = null;
        state.logout();
    }
}

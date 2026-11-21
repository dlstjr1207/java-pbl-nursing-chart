package app.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 가입 입력 검증 (D23). 아이디 영문·숫자 4~20자, 비밀번호 8자 이상, 이름 필수.
 * 오류는 입력 칸 이름(loginId, password, name)별 문구로 돌려줘 화면이 해당 칸에 연결할 수 있다.
 */
public final class SignupValidator {

    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern LOGIN_ID = Pattern.compile("[A-Za-z0-9]{4,20}");

    private SignupValidator() {
    }

    /** 칸 이름 → 오류 문구. 비어 있으면 통과. */
    public static Map<String, String> validate(String loginId, String password, String name) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            errors.put("loginId", "아이디는 영문·숫자 4~20자로 입력하세요.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            errors.put("password", "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상 입력하세요.");
        }
        if (name == null || name.isBlank()) {
            errors.put("name", "이름을 입력하세요.");
        }
        return errors;
    }
}

package app.storage;

/** 이미 사용 중인 아이디로 가입하려 할 때 던진다. */
public class DuplicateLoginIdException extends RuntimeException {

    public DuplicateLoginIdException(String loginId) {
        super("이미 사용 중인 아이디입니다: " + loginId);
    }
}

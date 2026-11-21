package app.storage;

/** DB 연결·스키마 적용 실패를 알리는 예외. 메시지에 비밀번호를 넣지 않는다. */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}

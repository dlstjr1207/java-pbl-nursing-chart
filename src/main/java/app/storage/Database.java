package app.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC 연결을 만드는 클래스 (D21, T-19).
 * 강의 방식대로 DriverManager로 연결하고, 쿼리는 각 Repository에서 PreparedStatement로 작성한다.
 * 연결 실패 시 원인은 알려 주되 비밀번호는 메시지에 넣지 않는다.
 */
public final class Database {

    private final String url;
    private final String user;
    private final String password;

    private Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** 실제 사용 DB(db.url)에 연결하는 객체를 만든다. */
    public static Database forApp(DbConfig config) {
        return new Database(config.url(), config.user(), config.password());
    }

    /** 테스트 전용 DB(db.test.url)에 연결하는 객체를 만든다. */
    public static Database forTest(DbConfig config) {
        return new Database(config.testUrl(), config.user(), config.password());
    }

    /** 이 객체가 연결하는 JDBC 주소 (비밀번호 없음) */
    public String url() {
        return url;
    }

    /**
     * 새 연결을 연다. 사용한 쪽에서 try-with-resources로 닫는다.
     *
     * @throws DatabaseException 서버가 꺼져 있거나, 계정·권한·DB 이름이 틀렸을 때
     */
    public Connection open() {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DatabaseException(
                    "DB에 연결할 수 없습니다 (주소: " + url + ", 계정: " + user + "). "
                            + "MySQL 서버 실행 여부, 계정 권한, DB 이름을 확인하세요. 원인: "
                            + e.getMessage() + " [SQLState " + e.getSQLState() + "]",
                    e);
        }
    }
}

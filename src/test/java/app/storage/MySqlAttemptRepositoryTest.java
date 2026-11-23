package app.storage;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.auth.PasswordHasher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;

/**
 * MySQL 실습 저장소 통합 테스트 (T-12). config/db.properties가 없으면 건너뛴다.
 * 테스트 전용 DB(이름에 test 포함)에서만 실행하고 users 표(하위 표는 CASCADE)를 비운다.
 */
class MySqlAttemptRepositoryTest extends AttemptRepositoryContract {

    private Database database;

    @BeforeEach
    void setUp() throws SQLException {
        assumeTrue(DbConfig.exists(DbConfig.DEFAULT_PATH), "config/db.properties 없음: DB 테스트 건너뜀");
        DbConfig config = DbConfig.load(DbConfig.DEFAULT_PATH);
        org.junit.jupiter.api.Assumptions.assumeTrue(config.testUrl() != null, "db.test.url 없음: DB 테스트 건너뜀");
        database = Database.forTest(config);
        try (Connection conn = database.open()) {
            String dbName = conn.getCatalog();
            assumeTrue(dbName != null && dbName.toLowerCase().contains("test"),
                    "db.test.url의 DB 이름에 'test'가 없어 건너뜀: " + dbName);
        }
        SchemaInitializer.apply(database);
        try (Connection conn = database.open();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users")) {
            ps.executeUpdate();
        }
    }

    @Override
    AttemptRepository repository() {
        return new MySqlAttemptRepository(database);
    }

    @Override
    long newUser(String loginId) {
        String salt = PasswordHasher.newSalt();
        return new MySqlUserRepository(database)
                .create(loginId, "테스트학생", PasswordHasher.hash("pass-1234", salt), salt).id();
    }
}

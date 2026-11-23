package app.storage;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;

/**
 * MySQL 계정 저장소 통합 테스트 (T-14). config/db.properties가 없으면 건너뛴다.
 * 테스트 전용 DB(이름에 test 포함)에서만 실행하고 users 표를 비운다.
 */
class MySqlUserRepositoryTest extends app.auth.UserRepositoryContractBridge {

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
    public UserRepository repository() {
        return new MySqlUserRepository(database);
    }
}

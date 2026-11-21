package app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 실제 MySQL 연결·스키마 적용 통합 테스트 (T-19).
 * config/db.properties가 없으면 건너뛴다 (CI, 키 없는 PC).
 * 테스트 전용 DB(db.test.url)만 사용하며, 시작할 때 그 DB의 테이블을 지우고 다시 만든다.
 */
class MySqlConnectionTest {

    private Database database;

    @BeforeEach
    void setUp() throws SQLException {
        assumeTrue(DbConfig.exists(DbConfig.DEFAULT_PATH), "config/db.properties 없음: DB 테스트 건너뜀");
        database = Database.forTest(DbConfig.load(DbConfig.DEFAULT_PATH));
        try (Connection conn = database.open()) {
            String dbName = conn.getCatalog();
            // 실수로 실제 DB를 지우지 않도록, 이름에 test가 들어간 DB에서만 실행한다
            assumeTrue(dbName != null && dbName.toLowerCase().contains("test"),
                    "db.test.url의 DB 이름에 'test'가 없어 건너뜀: " + dbName);
            dropAllTables(conn);
        }
    }

    @Test
    void connectsAndAppliesSchemaTwice() throws SQLException {
        SchemaInitializer.apply(database);
        SchemaInitializer.apply(database); // 두 번 실행해도 오류 없음

        assertEquals(SchemaInitializerTest.EXPECTED_TABLES.stream().sorted().toList(), tableNames());
    }

    @Test
    void storesKoreanTextAndRejectsDuplicateLoginId() throws SQLException {
        SchemaInitializer.apply(database);
        String insert = "INSERT INTO users (login_id, name, password_hash, salt) VALUES (?, ?, ?, ?)";
        try (Connection conn = database.open()) {
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, "smoke_user");
                ps.setString(2, "간호학생 테스트");
                ps.setString(3, "hash-placeholder");
                ps.setString(4, "salt-placeholder");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM users WHERE login_id = ?")) {
                ps.setString(1, "smoke_user");
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals("간호학생 테스트", rs.getString("name"));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, "smoke_user");
                ps.setString(2, "중복");
                ps.setString(3, "h");
                ps.setString(4, "s");
                assertThrows(SQLIntegrityConstraintViolationException.class, ps::executeUpdate);
            }
        }
    }

    private List<String> tableNames() throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name";
        try (Connection conn = database.open();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    /** 테스트 DB의 모든 테이블을 지운다 (외래 키 검사 잠시 해제). */
    private static void dropAllTables(Connection conn) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String name : names) {
                st.execute("DROP TABLE IF EXISTS `" + name.replace("`", "``") + "`");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}

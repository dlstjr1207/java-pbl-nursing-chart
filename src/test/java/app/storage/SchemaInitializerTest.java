package app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** schema.sql 파일 구성 검증 (DB 불필요). */
class SchemaInitializerTest {

    /** 기획안 '저장 방식 (D21)'의 테이블 초안 */
    static final List<String> EXPECTED_TABLES = List.of(
            "users", "attempts", "observation_records", "medication_records",
            "sbar_reports", "sbar_recommendations", "check_results");

    @Test
    void schemaHasOneCreateStatementPerPlannedTable() {
        List<String> statements = SchemaInitializer.splitStatements(SchemaInitializer.readSchema());
        assertEquals(EXPECTED_TABLES.size(), statements.size());
        for (int i = 0; i < EXPECTED_TABLES.size(); i++) {
            assertTrue(statements.get(i).startsWith("CREATE TABLE IF NOT EXISTS " + EXPECTED_TABLES.get(i) + " "),
                    "순서 " + i + ": " + EXPECTED_TABLES.get(i));
            assertTrue(statements.get(i).contains("utf8mb4"));
        }
    }

    @Test
    void schemaDoesNotCreateOrDropDatabases() {
        // 주석을 뺀 실제 SQL 문장만 검사한다
        for (String sql : SchemaInitializer.splitStatements(SchemaInitializer.readSchema())) {
            String upper = sql.toUpperCase();
            assertFalse(upper.contains("CREATE DATABASE"), sql);
            assertFalse(upper.contains("DROP "), sql);
        }
    }

    @Test
    void splitterIgnoresLineCommentsAndBlankParts() {
        List<String> parts = SchemaInitializer.splitStatements("""
                -- 주석
                CREATE TABLE a (id INT);
                  -- 들여쓴 주석
                CREATE TABLE b (id INT);
                ;
                """);
        assertEquals(List.of("CREATE TABLE a (id INT)", "CREATE TABLE b (id INT)"), parts);
    }
}

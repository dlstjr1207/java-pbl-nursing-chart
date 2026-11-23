package app.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * src/main/resources/db/schema.sql을 읽어 접속한 DB에 테이블을 만든다 (T-19).
 * 스크립트는 CREATE TABLE IF NOT EXISTS만 쓰므로 여러 번 실행해도 안전하다.
 * DDL에는 사용자 입력이 들어가지 않으므로 Statement로 실행한다.
 * (사용자 입력이 들어가는 쿼리는 Repository에서 PreparedStatement로 작성한다.)
 */
public final class SchemaInitializer {

    /** 클래스패스 안의 스키마 파일 위치 */
    public static final String SCHEMA_RESOURCE = "/db/schema.sql";

    private SchemaInitializer() {
    }

    /** 스키마 파일의 모든 문장을 실행한다. */
    public static void apply(Database database) {
        List<String> statements = splitStatements(readSchema());
        try (Connection conn = database.open(); Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            throw new DatabaseException("스키마 적용에 실패했습니다 (" + DbConfig.describe(database.url()) + "): " + e.getMessage(), e);
        }
    }

    /** 클래스패스에서 schema.sql 내용을 UTF-8로 읽는다. */
    static String readSchema() {
        try (InputStream in = SchemaInitializer.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("스키마 파일이 없습니다: " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("스키마 파일을 읽을 수 없습니다: " + SCHEMA_RESOURCE, e);
        }
    }

    /**
     * SQL 스크립트를 문장 단위로 나눈다.
     * '--'로 시작하는 줄 주석을 지우고 세미콜론으로 나눈다. 빈 문장은 버린다.
     */
    static List<String> splitStatements(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (!line.strip().startsWith("--")) {
                withoutComments.append(line).append('\n');
            }
        }
        List<String> statements = new ArrayList<>();
        for (String part : withoutComments.toString().split(";")) {
            String sql = part.strip();
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        return statements;
    }
}

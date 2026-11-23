package app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** DB 설정 파일 읽기 검증 (DB 불필요). 실제 config/db.properties는 읽지 않는다. */
class DbConfigTest {

    @TempDir
    Path dir;

    private Path write(String content) throws IOException {
        Path file = dir.resolve("db.properties");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void loadsAllRequiredKeys() throws IOException {
        Path file = write("""
                db.url=jdbc:mysql://localhost:3306/app_db
                db.test.url=jdbc:mysql://localhost:3306/app_db_test
                db.user=app_user
                db.password=dummy-pw-for-test
                """);
        DbConfig config = DbConfig.load(file);
        assertEquals("jdbc:mysql://localhost:3306/app_db", config.url());
        assertEquals("jdbc:mysql://localhost:3306/app_db_test", config.testUrl());
        assertEquals("app_user", config.user());
    }

    @Test
    void reportsEmptyKeysByNameWithoutValues() throws IOException {
        Path file = write("""
                db.url=jdbc:mysql://localhost:3306/app_db
                db.test.url=
                db.user=app_user
                db.password=
                """);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> DbConfig.load(file));
        assertFalse(e.getMessage().contains("db.test.url"), "db.test.url은 앱 실행에 필수가 아니다");
        assertTrue(e.getMessage().contains("db.password"));
        assertFalse(e.getMessage().contains("db.url,"));
    }

    @Test
    void toStringNeverShowsPassword() throws IOException {
        Path file = write("""
                db.url=jdbc:mysql://localhost:3306/app_db
                db.test.url=jdbc:mysql://localhost:3306/app_db_test
                db.user=app_user
                db.password=dummy-pw-for-test
                """);
        String text = DbConfig.load(file).toString();
        assertFalse(text.contains("dummy-pw-for-test"));
        assertTrue(text.contains("****"));
    }

    @Test
    void missingFileIsDetected() {
        assertFalse(DbConfig.exists(dir.resolve("none.properties")));
    }

    @Test
    void testUrlIsOptionalAndPasswordKeepsSpaces() throws IOException {
        Path file = write("""
                db.url=jdbc:mysql://localhost:3306/app_db?useSSL=false
                db.user=app_user
                db.password=dummy pw\s\s
                """);
        DbConfig config = DbConfig.load(file);
        assertEquals(null, config.testUrl());
        assertEquals("dummy pw  ", config.password());
    }

    @Test
    void messagesShowOnlyHostAndDatabase() throws IOException {
        Path file = write("""
                db.url=jdbc:mysql://localhost:3306/app_db?user=x&password=y
                db.test.url=jdbc:mysql://localhost:3306/app_db_test
                db.user=app_user
                db.password=dummy-pw-for-test
                """);
        String text = DbConfig.load(file).toString();
        assertTrue(text.contains("localhost:3306/app_db"));
        assertFalse(text.contains("jdbc:"));
        assertFalse(text.contains("password=y"));
        assertEquals("localhost:3306/app_db", DbConfig.describe("jdbc:mysql://localhost:3306/app_db?a=b"));
    }
}

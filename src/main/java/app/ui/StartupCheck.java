package app.ui;

import app.storage.Database;
import app.storage.DatabaseException;
import app.storage.DbConfig;
import app.storage.SchemaInitializer;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 앱 시작 시 DB 설정 파일과 연결을 확인한다 (T-16).
 * 실패하면 원인을 안내 문구로 돌려준다. 비밀번호는 문구에 넣지 않는다.
 */
public final class StartupCheck {

    /** @param database 성공하면 연결 객체, 실패하면 null. @param problem 실패 원인 안내(성공이면 null) */
    public record Result(Database database, String problem) {
        public boolean ok() { return database != null; }
    }

    private StartupCheck() {
    }

    /** 설정 파일 유무 → 항목 → 연결 → 스키마 적용 순으로 확인한다. */
    public static Result check(Path configPath) {
        if (!DbConfig.exists(configPath)) {
            return new Result(null, "DB 설정 파일이 없습니다: " + configPath
                    + "\nconfig/db.properties.example을 복사해 config/db.properties를 만들고 값을 직접 넣으세요.");
        }
        try {
            Database db = Database.forApp(DbConfig.load(configPath));
            try (Connection ignored = db.open()) {
                // 연결만 확인한다.
            }
            SchemaInitializer.apply(db);
            return new Result(db, null);
        } catch (DatabaseException | IllegalStateException e) {
            return new Result(null, e.getMessage());
        } catch (SQLException e) {
            return new Result(null, "DB 연결을 닫는 중 오류가 났습니다: " + e.getMessage());
        }
    }
}

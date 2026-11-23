package app.tools;

import app.storage.Database;
import app.storage.DbConfig;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * demo01 시연 계정의 실습 기록을 조회만 하는 점검 도구 (개발용). 지우지 않는다. 접속 정보는 출력하지 않는다.
 * 실행: java -cp target/nursing-chart-trainer.jar:target/test-classes app.tools.DemoRecords [케이스ID]
 */
public final class DemoRecords {
    public static void main(String[] args) throws Exception {
        String sql = "SELECT a.id, u.login_id, a.case_id, a.case_source, a.submitted_at, a.score, a.ai_feedback_status"
                + " FROM attempts a JOIN users u ON u.id = a.user_id WHERE u.login_id = 'demo01'"
                + (args.length > 0 ? " AND a.case_id = ?" : "") + " ORDER BY a.id";
        try (Connection c = Database.forApp(DbConfig.load(Path.of("config", "db.properties"))).open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (args.length > 0) ps.setString(1, args[0]);
            System.out.println("id | login_id | case_id | case_source | submitted_at | score | ai_feedback_status");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getLong(1) + " | " + rs.getString(2) + " | " + rs.getString(3) + " | " + rs.getString(4)
                            + " | " + rs.getTimestamp(5) + " | " + rs.getBigDecimal(6) + " | " + rs.getString(7));
                }
            }
        }
    }
}

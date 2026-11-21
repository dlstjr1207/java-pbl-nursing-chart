package app.storage;

import app.domain.AiFeedback;
import app.domain.Attempt;
import app.domain.CaseSource;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.DomainJson;
import app.domain.MedicationRecord;
import app.domain.MedicationResult;
import app.domain.ObservationEntry;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import app.domain.Shift;
import app.domain.VitalSign;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC(PreparedStatement)로 attempts와 하위 표(관찰·투약·SBAR·점검 결과)를 저장·조회한다.
 * 한 실습의 저장은 하나의 트랜잭션으로 묶는다. NOT NULL 칸에 값이 없으면 빈 문자열로 저장하고
 * 읽을 때도 빈 문자열로 돌려준다(입력 누락 점검은 R8·R6이 한다). 시각은 DATETIME이라 초 단위까지 보존한다.
 */
public class MySqlAttemptRepository implements AttemptRepository {

    private static final Type STRING_LIST = new TypeToken<List<String>>() { }.getType();

    private static final String ATTEMPT_SELECT = "SELECT id, user_id, case_id, case_source, generated_case_json, "
            + "started_at, submitted_at, score, ai_feedback_status, ai_feedback_json FROM attempts";

    private final Database database;

    public MySqlAttemptRepository(Database database) {
        this.database = database;
    }

    @Override
    public Attempt save(Attempt attempt) {
        try (Connection conn = database.open()) {
            conn.setAutoCommit(false);
            try {
                long id;
                if (attempt.attemptId() == null) {
                    id = insertAttempt(conn, attempt);
                } else {
                    id = attempt.attemptId();
                    updateAttempt(conn, attempt);
                    deleteChildren(conn, id);
                }
                insertChildren(conn, id, attempt);
                conn.commit();
                return InMemoryAttemptRepository.withId(attempt, id);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("실습 기록을 저장하지 못했습니다. 원인: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Attempt> findById(long userId, long attemptId) {
        String sql = ATTEMPT_SELECT + " WHERE user_id = ? AND id = ?";
        try (Connection conn = database.open(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(conn, rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("실습 기록을 조회하지 못했습니다. 원인: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Attempt> findByUser(long userId) {
        String sql = ATTEMPT_SELECT + " WHERE user_id = ? ORDER BY started_at DESC, id DESC";
        try (Connection conn = database.open(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            List<Attempt> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(read(conn, rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseException("실습 기록 목록을 조회하지 못했습니다. 원인: " + e.getMessage(), e);
        }
    }

    // ---------- 저장 ----------

    /** attempts에 새 행을 넣고 생성된 번호를 돌려준다. */
    private long insertAttempt(Connection conn, Attempt a) throws SQLException {
        String sql = "INSERT INTO attempts (user_id, case_id, case_source, generated_case_json, started_at, "
                + "submitted_at, score, ai_feedback_status, ai_feedback_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindAttempt(ps, a);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** 같은 사용자의 기존 행을 덮어쓴다. 없으면 IllegalArgumentException. */
    private void updateAttempt(Connection conn, Attempt a) throws SQLException {
        String sql = "UPDATE attempts SET user_id = ?, case_id = ?, case_source = ?, generated_case_json = ?, "
                + "started_at = ?, submitted_at = ?, score = ?, ai_feedback_status = ?, ai_feedback_json = ? "
                + "WHERE id = ? AND user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindAttempt(ps, a);
            ps.setLong(10, a.attemptId());
            ps.setLong(11, a.userId());
            if (ps.executeUpdate() == 0 && !exists(conn, a.userId(), a.attemptId())) {
                throw new IllegalArgumentException("덮어쓸 실습 기록이 없습니다: " + a.attemptId());
            }
        }
    }

    /** UPDATE가 값이 같아 0행을 돌려주는 경우와 실제로 없는 경우를 구분한다. */
    private boolean exists(Connection conn, long userId, long attemptId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM attempts WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, attemptId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** attempts 행의 공통 9개 값을 1~9번 자리에 채운다. */
    private void bindAttempt(PreparedStatement ps, Attempt a) throws SQLException {
        ps.setLong(1, a.userId());
        ps.setString(2, a.caseId());
        ps.setString(3, a.caseSource().name());
        ps.setString(4, a.generatedCaseJson());
        ps.setTimestamp(5, Timestamp.valueOf(a.startedAt()));
        ps.setTimestamp(6, a.submittedAt() == null ? null : Timestamp.valueOf(a.submittedAt()));
        if (a.score() == null) {
            ps.setNull(7, Types.DECIMAL);
        } else {
            ps.setDouble(7, a.score());
        }
        AiFeedback f = a.aiFeedback();
        ps.setString(8, f == null ? "NONE" : f.status());
        ps.setString(9, f == null ? null : DomainJson.toJson(f));
    }

    /** 하위 표 행을 지운다 (덮어쓰기 전). SBAR의 R 행은 외래키 CASCADE로 함께 지워진다. */
    private void deleteChildren(Connection conn, long attemptId) throws SQLException {
        for (String table : List.of("observation_records", "medication_records", "sbar_reports", "check_results")) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE attempt_id = ?")) {
                ps.setLong(1, attemptId);
                ps.executeUpdate();
            }
        }
    }

    private void insertChildren(Connection conn, long id, Attempt a) throws SQLException {
        String obsSql = "INSERT INTO observation_records (attempt_id, observed_time, shift, tags, narrative, "
                + "intervention, vitals_json) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(obsSql)) {
            for (ObservationEntry o : a.observationEntries()) {
                ps.setLong(1, id);
                ps.setString(2, nz(o.time()));
                ps.setString(3, o.shift() == null ? "" : o.shift().name());
                ps.setString(4, DomainJson.toJson(o.tags()));
                ps.setString(5, nz(o.narrative()));
                ps.setString(6, o.intervention());
                ps.setString(7, o.vitals() == null ? null : DomainJson.toJson(o.vitals()));
                ps.executeUpdate();
            }
        }

        String medSql = "INSERT INTO medication_records (attempt_id, order_id, drug_name, dose, unit, route, "
                + "administered_time, result, reason, order_basis, oral_check, reassessment_plan) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(medSql)) {
            for (MedicationRecord m : a.medicationRecords()) {
                ps.setLong(1, id);
                ps.setString(2, nz(m.orderId()));
                ps.setString(3, nz(m.drugName()));
                ps.setString(4, nz(m.dose()));
                ps.setString(5, nz(m.unit()));
                ps.setString(6, nz(m.route()));
                ps.setString(7, nz(m.administeredTime()));
                ps.setString(8, m.result() == null ? "" : m.result().name());
                ps.setString(9, m.reason());
                ps.setString(10, nz(m.orderBasis()));
                if (m.oralCheck() == null) {
                    ps.setNull(11, Types.TINYINT);
                } else {
                    ps.setBoolean(11, m.oralCheck());
                }
                ps.setString(12, m.reassessmentPlan());
                ps.executeUpdate();
            }
        }

        SbarReport sbar = a.sbar();
        if (sbar != null) {
            insertSbar(conn, id, sbar);
        }

        String checkSql = "INSERT INTO check_results (attempt_id, rule_id, target, target_ref, status, message, points) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            for (CheckResult c : a.checks()) {
                ps.setLong(1, id);
                ps.setString(2, c.ruleId());
                ps.setString(3, c.target().name());
                ps.setString(4, c.targetRef());
                ps.setString(5, c.status().name());
                ps.setString(6, c.message());
                if (c.points() == null) {
                    ps.setNull(7, Types.DECIMAL);
                } else {
                    ps.setDouble(7, c.points());
                }
                ps.executeUpdate();
            }
        }
    }

    private void insertSbar(Connection conn, long attemptId, SbarReport sbar) throws SQLException {
        String sql = "INSERT INTO sbar_reports (attempt_id, situation, background, assessment) VALUES (?, ?, ?, ?)";
        long sbarId;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, attemptId);
            ps.setString(2, nz(sbar.situation()));
            ps.setString(3, nz(sbar.background()));
            ps.setString(4, nz(sbar.assessment()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                sbarId = keys.getLong(1);
            }
        }
        String recSql = "INSERT INTO sbar_recommendations (sbar_id, seq, rec_time, intervention) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(recSql)) {
            int seq = 0;
            for (SbarRecommendation r : sbar.recommendations()) {
                ps.setLong(1, sbarId);
                ps.setInt(2, seq++);
                ps.setString(3, nz(r.time()));
                ps.setString(4, nz(r.intervention()));
                ps.executeUpdate();
            }
        }
    }

    /** NOT NULL 칸에 넣을 값: null이면 빈 문자열 */
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---------- 조회 ----------

    /** attempts 한 행과 하위 표를 읽어 Attempt로 만든다. */
    private Attempt read(Connection conn, ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        Timestamp submitted = rs.getTimestamp("submitted_at");
        double scoreValue = rs.getDouble("score");
        Double score = rs.wasNull() ? null : scoreValue;
        String feedbackJson = rs.getString("ai_feedback_json");
        String feedbackStatus = rs.getString("ai_feedback_status");
        AiFeedback feedback = null;
        if (feedbackJson != null) {
            feedback = DomainJson.fromJson(feedbackJson, AiFeedback.class);
        } else if (!"NONE".equals(feedbackStatus)) {
            feedback = new AiFeedback(feedbackStatus, null, null, null);
        }
        LocalDateTime submittedAt = submitted == null ? null : submitted.toLocalDateTime();
        return new Attempt(id, rs.getLong("user_id"), rs.getString("case_id"),
                CaseSource.valueOf(rs.getString("case_source")), rs.getString("generated_case_json"),
                rs.getTimestamp("started_at").toLocalDateTime(), submittedAt,
                readObservations(conn, id), readMedications(conn, id), readSbar(conn, id),
                readChecks(conn, id), score, feedback);
    }

    private List<ObservationEntry> readObservations(Connection conn, long attemptId) throws SQLException {
        String sql = "SELECT observed_time, shift, tags, narrative, intervention, vitals_json "
                + "FROM observation_records WHERE attempt_id = ? ORDER BY id";
        List<ObservationEntry> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String shift = rs.getString("shift");
                    String vitals = rs.getString("vitals_json");
                    list.add(new ObservationEntry(rs.getString("observed_time"),
                            shift.isEmpty() ? null : Shift.valueOf(shift),
                            DomainJson.gson().<List<String>>fromJson(rs.getString("tags"), STRING_LIST),
                            rs.getString("narrative"), rs.getString("intervention"),
                            vitals == null ? null : DomainJson.fromJson(vitals, VitalSign.class)));
                }
            }
        }
        return list;
    }

    private List<MedicationRecord> readMedications(Connection conn, long attemptId) throws SQLException {
        String sql = "SELECT order_id, drug_name, dose, unit, route, administered_time, result, reason, "
                + "order_basis, oral_check, reassessment_plan FROM medication_records WHERE attempt_id = ? ORDER BY id";
        List<MedicationRecord> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String result = rs.getString("result");
                    boolean oral = rs.getBoolean("oral_check");
                    Boolean oralCheck = rs.wasNull() ? null : oral;
                    list.add(new MedicationRecord(rs.getString("order_id"), rs.getString("drug_name"),
                            rs.getString("dose"), rs.getString("unit"), rs.getString("route"),
                            rs.getString("administered_time"),
                            result.isEmpty() ? null : MedicationResult.valueOf(result),
                            rs.getString("reason"), rs.getString("order_basis"), oralCheck,
                            rs.getString("reassessment_plan")));
                }
            }
        }
        return list;
    }

    /** SBAR이 없으면 null */
    private SbarReport readSbar(Connection conn, long attemptId) throws SQLException {
        String sql = "SELECT id, situation, background, assessment FROM sbar_reports WHERE attempt_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long sbarId = rs.getLong("id");
                String s = rs.getString("situation");
                String b = rs.getString("background");
                String a = rs.getString("assessment");
                return new SbarReport(s, b, a, readRecommendations(conn, sbarId));
            }
        }
    }

    private List<SbarRecommendation> readRecommendations(Connection conn, long sbarId) throws SQLException {
        String sql = "SELECT rec_time, intervention FROM sbar_recommendations WHERE sbar_id = ? ORDER BY seq";
        List<SbarRecommendation> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sbarId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SbarRecommendation(rs.getString("rec_time"), rs.getString("intervention")));
                }
            }
        }
        return list;
    }

    private List<CheckResult> readChecks(Connection conn, long attemptId) throws SQLException {
        String sql = "SELECT rule_id, target, target_ref, status, message, points "
                + "FROM check_results WHERE attempt_id = ? ORDER BY id";
        List<CheckResult> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double p = rs.getDouble("points");
                    Double points = rs.wasNull() ? null : p;
                    list.add(new CheckResult(rs.getString("rule_id"), CheckTarget.valueOf(rs.getString("target")),
                            rs.getString("target_ref"), CheckStatus.valueOf(rs.getString("status")),
                            rs.getString("message"), points));
                }
            }
        }
        return list;
    }
}

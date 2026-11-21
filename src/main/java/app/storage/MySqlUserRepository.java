package app.storage;

import app.auth.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Optional;

/** JDBC(PreparedStatement)로 users 테이블에 계정을 저장·조회한다. */
public class MySqlUserRepository implements UserRepository {

    private final Database database;

    public MySqlUserRepository(Database database) {
        this.database = database;
    }

    @Override
    public User create(String loginId, String name, String passwordHash, String salt) {
        String sql = "INSERT INTO users (login_id, name, password_hash, salt) VALUES (?, ?, ?, ?)";
        try (Connection conn = database.open();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, loginId);
            ps.setString(2, name);
            ps.setString(3, passwordHash);
            ps.setString(4, salt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new User(keys.getLong(1), loginId, name, passwordHash, salt);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DuplicateLoginIdException(loginId);
        } catch (SQLException e) {
            throw new DatabaseException("계정을 저장하지 못했습니다. 원인: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        String sql = "SELECT id, login_id, name, password_hash, salt FROM users WHERE login_id = ?";
        try (Connection conn = database.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loginId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new User(rs.getLong("id"), rs.getString("login_id"),
                        rs.getString("name"), rs.getString("password_hash"), rs.getString("salt")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("계정을 조회하지 못했습니다. 원인: " + e.getMessage(), e);
        }
    }
}

package xyz.negativeone.app.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {

  private final JdbcTemplate jdbc;

  public SessionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(String token, int userId, Instant expiresAt) {
    jdbc.update(
        "INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, ?)",
        token,
        userId,
        Timestamp.from(expiresAt));
  }

  public Optional<Integer> findValidUserId(String token, Instant now) {
    List<Integer> ids =
        jdbc.query(
            "SELECT user_id FROM sessions WHERE token = ? AND expires_at > ?",
            (rs, rowNum) -> rs.getInt("user_id"),
            token,
            Timestamp.from(now));
    if (ids.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(ids.get(0));
  }

  public int deleteByToken(String token) {
    return jdbc.update("DELETE FROM sessions WHERE token = ?", token);
  }

  public void deleteExpired(Instant now) {
    jdbc.update("DELETE FROM sessions WHERE expires_at <= ?", Timestamp.from(now));
  }
}

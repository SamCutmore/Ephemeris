package xyz.negativeone.app.persistence;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private final JdbcTemplate jdbc;

  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<User> findByName(String name) {
    return jdbc.query(
        "SELECT id, name, better_password FROM users WHERE name = ?",
        (rs, rowNum) -> {
          User user = new User();
          user.setId(rs.getInt("id"));
          user.setName(rs.getString("name"));
          user.setPassword(rs.getString("better_password"));
          return user;
        },
        name);
  }

  public User save(User user) {
    if (user.getId() != null) {
      jdbc.update(
          "UPDATE users SET name = ?, better_password = ? WHERE id = ?",
          user.getName(),
          user.getPassword(),
          user.getId());
      return user;
    }
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  "INSERT INTO users (name, better_password) VALUES (?, ?)", new String[] {"id"});
          ps.setString(1, user.getName());
          ps.setString(2, user.getPassword());
          return ps;
        },
        keys);
    Number id = keys.getKey();
    if (id != null) {
      user.setId(id.intValue());
    }
    return user;
  }
}

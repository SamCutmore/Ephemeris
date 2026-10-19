package xyz.negativeone.app.persistence;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MarketGroupRepository {

  private static final RowMapper<MarketGroupRecord> MAPPER =
      (rs, rowNum) -> {
        int parent = rs.getInt("parent_group_id");
        Integer parentGroupId = rs.wasNull() ? null : parent;
        return new MarketGroupRecord(
            rs.getInt("market_group_id"),
            parentGroupId,
            rs.getString("market_group_name"),
            rs.getString("description"),
            rs.getBoolean("has_types"));
      };

  private final JdbcTemplate jdbc;

  public MarketGroupRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<MarketGroupRecord> findRoots() {
    return jdbc.query(
        """
				SELECT market_group_id, parent_group_id, market_group_name, description, has_types
				FROM market_groups
				WHERE parent_group_id IS NULL
				ORDER BY market_group_name
				""",
        MAPPER);
  }

  public List<MarketGroupRecord> findByParent(int parentGroupId) {
    return jdbc.query(
        """
				SELECT market_group_id, parent_group_id, market_group_name, description, has_types
				FROM market_groups
				WHERE parent_group_id = ?
				ORDER BY market_group_name
				""",
        MAPPER,
        parentGroupId);
  }

  /** Insert or refresh a catalogue group (fixture / seed load). */
  public void upsert(
      int marketGroupId, Integer parentGroupId, String name, String description, boolean hasTypes) {
    int updated =
        jdbc.update(
            """
				UPDATE market_groups
				SET parent_group_id = ?, market_group_name = ?, description = ?, has_types = ?
				WHERE market_group_id = ?
				""",
            parentGroupId,
            name,
            description,
            hasTypes,
            marketGroupId);
    if (updated == 0) {
      jdbc.update(
          """
					INSERT INTO market_groups
					    (market_group_id, parent_group_id, market_group_name, description, has_types)
					VALUES (?, ?, ?, ?, ?)
					""",
          marketGroupId,
          parentGroupId,
          name,
          description,
          hasTypes);
    }
  }
}

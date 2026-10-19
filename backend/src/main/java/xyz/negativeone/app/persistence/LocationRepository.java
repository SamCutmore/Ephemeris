package xyz.negativeone.app.persistence;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LocationRepository {

  private static final RowMapper<LocationRecord> MAPPER =
      (rs, rowNum) -> {
        int parent = rs.getInt("parent_location_id");
        Integer parentLocationId = rs.wasNull() ? null : parent;
        return new LocationRecord(
            rs.getInt("location_id"),
            parentLocationId,
            rs.getString("location_name"),
            rs.getString("description"));
      };

  private final JdbcTemplate jdbc;

  public LocationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<LocationRecord> findRoots() {
    return jdbc.query(
        """
				SELECT location_id, parent_location_id, location_name, description
				FROM locations
				WHERE parent_location_id IS NULL
				ORDER BY location_name
				""",
        MAPPER);
  }

  public List<LocationRecord> findByParent(int parentLocationId) {
    return jdbc.query(
        """
				SELECT location_id, parent_location_id, location_name, description
				FROM locations
				WHERE parent_location_id = ?
				ORDER BY location_name
				""",
        MAPPER,
        parentLocationId);
  }
}

package xyz.negativeone.app.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MarketHistoryRepository {

  private static final RowMapper<MarketHistoryRecord> MAPPER =
      (rs, rowNum) ->
          new MarketHistoryRecord(
              rs.getInt("location_id"),
              rs.getInt("type_id"),
              rs.getDate("date").toLocalDate(),
              rs.getBigDecimal("low_price"),
              rs.getBigDecimal("high_price"),
              rs.getBigDecimal("avg_price"),
              rs.getLong("volume"),
              rs.getLong("orders"));

  private final JdbcTemplate jdbc;

  public MarketHistoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<MarketHistoryRecord> findByTypeAndLocation(int typeId, int locationId) {
    return jdbc.query(
        """
				SELECT location_id, type_id, date, low_price, high_price, avg_price, volume, orders
				FROM market_history
				WHERE type_id = ? AND location_id = ?
				ORDER BY date ASC
				""",
        MAPPER,
        typeId,
        locationId);
  }

  /** Insert or refresh a daily history row (fixture / seed load). */
  public void upsert(
      int locationId,
      int typeId,
      LocalDate date,
      BigDecimal lowPrice,
      BigDecimal highPrice,
      BigDecimal avgPrice,
      long volume,
      long orders) {
    Date sqlDate = Date.valueOf(date);
    int updated =
        jdbc.update(
            """
				UPDATE market_history
				SET low_price = ?, high_price = ?, avg_price = ?, volume = ?, orders = ?
				WHERE location_id = ? AND type_id = ? AND date = ?
				""",
            lowPrice,
            highPrice,
            avgPrice,
            volume,
            orders,
            locationId,
            typeId,
            sqlDate);
    if (updated == 0) {
      jdbc.update(
          """
					INSERT INTO market_history
					    (location_id, type_id, date, low_price, high_price, avg_price, volume, orders)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""",
          locationId,
          typeId,
          sqlDate,
          lowPrice,
          highPrice,
          avgPrice,
          volume,
          orders);
    }
  }
}

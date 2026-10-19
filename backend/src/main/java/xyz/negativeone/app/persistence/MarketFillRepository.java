package xyz.negativeone.app.persistence;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MarketFillRepository {

  private static final RowMapper<MarketFillRecord> MAPPER =
      (rs, rowNum) ->
          new MarketFillRecord(
              rs.getLong("fill_id"),
              rs.getInt("type_id"),
              rs.getInt("location_id"),
              rs.getLong("buy_order_id"),
              rs.getLong("sell_order_id"),
              rs.getBigDecimal("price"),
              rs.getInt("quantity"),
              rs.getTimestamp("filled_at").toInstant());

  private final JdbcTemplate jdbc;

  public MarketFillRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(
      int typeId,
      int locationId,
      long buyOrderId,
      long sellOrderId,
      BigDecimal price,
      int quantity) {
    jdbc.update(
        """
				INSERT INTO market_fills
				    (type_id, location_id, buy_order_id, sell_order_id, price, quantity)
				VALUES (?, ?, ?, ?, ?, ?)
				""",
        typeId,
        locationId,
        buyOrderId,
        sellOrderId,
        price,
        quantity);
  }

  public List<MarketFillRecord> findByUserBuyOrders(int userId) {
    return jdbc.query(
        """
				SELECT f.fill_id, f.type_id, f.location_id, f.buy_order_id, f.sell_order_id,
				       f.price, f.quantity, f.filled_at
				FROM market_fills f
				INNER JOIN market_orders o ON o.order_id = f.buy_order_id
				WHERE o.user_id = ?
				ORDER BY f.filled_at DESC, f.fill_id DESC
				""",
        MAPPER,
        userId);
  }
}

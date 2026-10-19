package xyz.negativeone.app.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MarketPriceRepository {

  private static final RowMapper<MarketPriceRecord> MAPPER =
      (rs, rowNum) ->
          new MarketPriceRecord(
              rs.getInt("type_id"),
              rs.getInt("location_id"),
              rs.getBigDecimal("average_price"), // median of open asks
              rs.getBigDecimal("adjusted_price"), // lowest ask
              rs.getInt("sample_size"));

  private final JdbcTemplate jdbc;

  public MarketPriceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<MarketPriceRecord> find(int typeId, int locationId) {
    List<MarketPriceRecord> rows =
        jdbc.query(
            """
				SELECT type_id, location_id, average_price, adjusted_price,
				       COALESCE(sample_size, 0) AS sample_size
				FROM market_prices
				WHERE type_id = ? AND location_id = ?
				""",
            MAPPER,
            typeId,
            locationId);
    return rows.stream().findFirst();
  }

  public void upsert(MarketPriceRecord price) {
    int updated =
        jdbc.update(
            """
				UPDATE market_prices
				SET average_price = ?, adjusted_price = ?, sample_size = ?
				WHERE type_id = ? AND location_id = ?
				""",
            price.medianPrice(),
            price.lowestPrice(),
            price.sampleSize(),
            price.typeId(),
            price.locationId());
    if (updated == 0) {
      jdbc.update(
          """
					INSERT INTO market_prices
					    (type_id, location_id, average_price, adjusted_price, sample_size)
					VALUES (?, ?, ?, ?, ?)
					""",
          price.typeId(),
          price.locationId(),
          price.medianPrice(),
          price.lowestPrice(),
          price.sampleSize());
    }
  }

  /** Open sell ask prices for a type/location. */
  public List<BigDecimal> findOpenSellPrices(int typeId, int locationId) {
    return jdbc.query(
        """
				SELECT price
				FROM market_orders
				WHERE type_id = ?
				  AND location_id = ?
				  AND side = 'sell'
				  AND status IN ('open', 'partial')
				  AND quantity > 0
				  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
				ORDER BY price ASC
				""",
        (rs, rowNum) -> rs.getBigDecimal("price"),
        typeId,
        locationId);
  }

  /** Recompute median + lowest from current open sells and persist. */
  public MarketPriceRecord refreshFromOpenSells(int typeId, int locationId) {
    List<BigDecimal> prices = new ArrayList<>(findOpenSellPrices(typeId, locationId));
    Collections.sort(prices);
    int n = prices.size();
    BigDecimal median = null;
    BigDecimal lowest = null;
    if (n > 0) {
      lowest = prices.get(0).setScale(2, RoundingMode.HALF_UP);
      if (n % 2 == 1) {
        median = prices.get(n / 2).setScale(2, RoundingMode.HALF_UP);
      } else {
        median =
            prices
                .get(n / 2 - 1)
                .add(prices.get(n / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
      }
    }
    MarketPriceRecord row = new MarketPriceRecord(typeId, locationId, median, lowest, n);
    upsert(row);
    return row;
  }
}

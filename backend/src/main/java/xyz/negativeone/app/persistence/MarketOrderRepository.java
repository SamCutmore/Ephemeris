package xyz.negativeone.app.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MarketOrderRepository {

  public static final int SOURCE_APP = 6;

  private static final RowMapper<MarketOrderRecord> MAPPER =
      (rs, rowNum) -> {
        int user = rs.getInt("user_id");
        Integer userId = rs.wasNull() ? null : user;
        Timestamp expiresTs = rs.getTimestamp("expires_at");
        Instant expiresAt = expiresTs == null ? null : expiresTs.toInstant();
        return new MarketOrderRecord(
            rs.getLong("order_id"),
            rs.getInt("type_id"),
            rs.getInt("location_id"),
            rs.getInt("source_id"),
            rs.getString("side"),
            rs.getBigDecimal("price"),
            rs.getInt("quantity"),
            rs.getInt("quantity_initial"),
            rs.getString("status"),
            rs.getString("external_url"),
            rs.getString("external_listing_id"),
            rs.getString("owner_kind"),
            rs.getString("owner_ref"),
            userId,
            rs.getString("username"),
            expiresAt,
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
      };

  private static final String COLUMNS =
      """
			order_id, type_id, location_id, source_id, side, price, quantity,
			quantity_initial, status, external_url, external_listing_id, owner_kind, owner_ref,
			user_id,
			COALESCE(
			  (SELECT name FROM users WHERE id = market_orders.user_id),
			  owner_ref
			) AS username,
			expires_at, created_at, updated_at
			""";

  private static final String OPEN_SELECT =
      """
			SELECT %s
			FROM market_orders
			WHERE type_id = ? AND location_id = ?
			  AND side = ? AND status IN ('open', 'partial') AND quantity > 0
			  AND (expires_at IS NULL OR expires_at > ?)
			"""
          .formatted(COLUMNS);

  private final JdbcTemplate jdbc;

  public MarketOrderRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long insertBuy(
      int typeId, int locationId, BigDecimal price, int quantity, int userId, Instant expiresAt) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  """
					INSERT INTO market_orders (
					    type_id, location_id, source_id, side, price, quantity, quantity_initial,
					    status, external_url, external_listing_id, owner_kind, owner_ref,
					    user_id, expires_at)
					VALUES (
					    ?, ?, ?, 'buy', ?, ?, ?, 'open', NULL, NULL, 'user',
					    (SELECT name FROM users WHERE id = ?), ?, ?)
					""",
                  new String[] {"order_id"});
          ps.setInt(1, typeId);
          ps.setInt(2, locationId);
          ps.setInt(3, SOURCE_APP);
          ps.setBigDecimal(4, price);
          ps.setInt(5, quantity);
          ps.setInt(6, quantity);
          ps.setInt(7, userId);
          ps.setInt(8, userId);
          ps.setTimestamp(9, Timestamp.from(expiresAt));
          return ps;
        },
        keys);
    Number id = keys.getKey();
    if (id == null) {
      throw new IllegalStateException("Failed to insert buy order");
    }
    return id.longValue();
  }

  /** Active external sell keyed by marketplace listing id. */
  public Optional<MarketOrderRecord> findActiveExternalSell(
      int sourceId, String externalListingId) {
    Instant now = Instant.now();
    cancelExpired(now);
    List<MarketOrderRecord> rows =
        jdbc.query(
            """
				SELECT %s
				FROM market_orders
				WHERE source_id = ?
				  AND external_listing_id = ?
				  AND side = 'sell'
				  AND owner_kind = 'external'
				  AND status IN ('open', 'partial')
				  AND quantity > 0
				  AND (expires_at IS NULL OR expires_at > ?)
				ORDER BY order_id DESC
				"""
                .formatted(COLUMNS),
            MAPPER,
            sourceId,
            externalListingId,
            Timestamp.from(now));
    return rows.stream().findFirst();
  }

  /** Insert or refresh an ingested external sell. Cancel listings not seen in the latest scrape. */
  public MarketOrderRecord upsertExternalSell(
      int typeId,
      int locationId,
      int sourceId,
      BigDecimal price,
      int quantity,
      String externalListingId) {
    return upsertExternalSell(
        typeId, locationId, sourceId, price, quantity, externalListingId, null);
  }

  public MarketOrderRecord upsertExternalSell(
      int typeId,
      int locationId,
      int sourceId,
      BigDecimal price,
      int quantity,
      String externalListingId,
      String externalUrl) {
    Timestamp now = Timestamp.from(Instant.now());
    Optional<MarketOrderRecord> existing = findActiveExternalSell(sourceId, externalListingId);
    if (existing.isPresent()) {
      MarketOrderRecord order = existing.get();
      int filled = order.quantityInitial() - order.quantity();
      int newInitial = filled + quantity;
      String status = filled > 0 ? "partial" : "open";
      jdbc.update(
          """
					UPDATE market_orders
					SET type_id = ?, location_id = ?, price = ?, quantity = ?,
					    quantity_initial = ?, status = ?, external_url = ?, updated_at = ?
					WHERE order_id = ?
					""",
          typeId,
          locationId,
          price,
          quantity,
          newInitial,
          status,
          externalUrl,
          now,
          order.orderId());
      return findActiveExternalSell(sourceId, externalListingId)
          .orElseThrow(() -> new IllegalStateException("Failed to reload upserted sell"));
    }

    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  """
					INSERT INTO market_orders (
					    type_id, location_id, source_id, side, price, quantity, quantity_initial,
					    status, external_url, external_listing_id, owner_kind, owner_ref,
					    user_id, expires_at, created_at, updated_at)
					VALUES (?, ?, ?, 'sell', ?, ?, ?, 'open', ?, ?, 'external', ?, NULL, NULL, ?, ?)
					""",
                  new String[] {"order_id"});
          ps.setInt(1, typeId);
          ps.setInt(2, locationId);
          ps.setInt(3, sourceId);
          ps.setBigDecimal(4, price);
          ps.setInt(5, quantity);
          ps.setInt(6, quantity);
          ps.setString(7, externalUrl);
          ps.setString(8, externalListingId);
          ps.setString(9, externalListingId);
          ps.setTimestamp(10, now);
          ps.setTimestamp(11, now);
          return ps;
        },
        keys);
    Number id = keys.getKey();
    if (id == null) {
      throw new IllegalStateException("Failed to insert sell order");
    }
    return findActiveExternalSell(sourceId, externalListingId)
        .orElseThrow(() -> new IllegalStateException("Failed to reload inserted sell"));
  }

  /** Cancel an ingested external sell by marketplace listing id. */
  public Optional<MarketOrderRecord> cancelExternalSell(int sourceId, String externalListingId) {
    Optional<MarketOrderRecord> existing = findActiveExternalSell(sourceId, externalListingId);
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    jdbc.update(
        """
				UPDATE market_orders
				SET status = 'cancelled', updated_at = CURRENT_TIMESTAMP
				WHERE order_id = ?
				""",
        existing.get().orderId());
    List<MarketOrderRecord> rows =
        jdbc.query(
            """
				SELECT %s
				FROM market_orders
				WHERE order_id = ?
				"""
                .formatted(COLUMNS),
            MAPPER,
            existing.get().orderId());
    return rows.stream().findFirst();
  }

  /**
   * Cancel open/partial external sells for a source whose updated_at is older than {@code
   * updatedBefore}.
   */
  public int cancelStaleExternalSells(int sourceId, Instant updatedBefore) {
    return jdbc.update(
        """
				UPDATE market_orders
				SET status = 'cancelled', updated_at = CURRENT_TIMESTAMP
				WHERE source_id = ?
				  AND side = 'sell'
				  AND owner_kind = 'external'
				  AND status IN ('open', 'partial')
				  AND updated_at < ?
				""",
        sourceId,
        Timestamp.from(updatedBefore));
  }

  public boolean sourceExists(int sourceId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sources WHERE source_id = ?", Integer.class, sourceId);
    return count != null && count > 0;
  }

  public void updateRemaining(long orderId, int quantity, String status) {
    jdbc.update(
        """
				UPDATE market_orders
				SET quantity = ?, status = ?, updated_at = CURRENT_TIMESTAMP
				WHERE order_id = ?
				""",
        quantity,
        status,
        orderId);
  }

  /** Sets open/partial orders past expires_at as cancelled. */
  public int cancelExpired(Instant now) {
    return jdbc.update(
        """
				UPDATE market_orders
				SET status = 'cancelled', updated_at = CURRENT_TIMESTAMP
				WHERE status IN ('open', 'partial')
				  AND expires_at IS NOT NULL AND expires_at <= ?
				""",
        Timestamp.from(now));
  }

  public List<MarketOrderRecord> findOpenBuys(int typeId, int locationId) {
    return jdbc.query(
        OPEN_SELECT + " ORDER BY price DESC, created_at ASC",
        MAPPER,
        typeId,
        locationId,
        "buy",
        Timestamp.from(Instant.now()));
  }

  public List<MarketOrderRecord> findOpenSells(int typeId, int locationId) {
    return jdbc.query(
        OPEN_SELECT + " ORDER BY price ASC, created_at ASC",
        MAPPER,
        typeId,
        locationId,
        "sell",
        Timestamp.from(Instant.now()));
  }

  public OrderBookRecord findOpenBook(int typeId, int locationId) {
    return new OrderBookRecord(
        typeId, locationId, findOpenBuys(typeId, locationId), findOpenSells(typeId, locationId));
  }

  public List<MarketOrderRecord> findOpenByUserId(int userId) {
    Instant now = Instant.now();
    cancelExpired(now);
    return jdbc.query(
        """
				SELECT %s
				FROM market_orders
				WHERE user_id = ?
				  AND status IN ('open', 'partial')
				  AND quantity > 0
				  AND (expires_at IS NULL OR expires_at > ?)
				ORDER BY created_at DESC, order_id DESC
				"""
            .formatted(COLUMNS),
        MAPPER,
        userId,
        Timestamp.from(now));
  }

  /** Transaction history. */
  public List<MarketOrderRecord> findHistoricalByUserId(int userId) {
    Instant now = Instant.now();
    cancelExpired(now);
    return jdbc.query(
        """
				SELECT %s
				FROM market_orders
				WHERE user_id = ?
				  AND (
				    status IN ('filled', 'cancelled')
				    OR (expires_at IS NOT NULL AND expires_at <= ?)
				  )
				ORDER BY updated_at DESC, order_id DESC
				"""
            .formatted(COLUMNS),
        MAPPER,
        userId,
        Timestamp.from(now));
  }

  /** User's buy orders. */
  public List<MyOrderRecord> findUserBuyOrders(int userId) {
    Instant now = Instant.now();
    cancelExpired(now);
    return jdbc.query(
        """
				SELECT o.order_id, o.type_id, t.type_name, o.price,
				       o.quantity, o.quantity_initial, o.created_at,
				       CASE
				         WHEN o.status IN ('open', 'partial')
				              AND o.expires_at IS NOT NULL
				              AND o.expires_at <= ?
				         THEN 'cancelled'
				         ELSE o.status
				       END AS status
				FROM market_orders o
				JOIN inv_types t ON t.type_id = o.type_id
				WHERE o.user_id = ?
				  AND o.side = 'buy'
				ORDER BY o.created_at DESC, o.order_id DESC
				""",
        (rs, rowNum) ->
            new MyOrderRecord(
                rs.getLong("order_id"),
                rs.getInt("type_id"),
                rs.getString("type_name"),
                rs.getBigDecimal("price"),
                rs.getInt("quantity"),
                rs.getInt("quantity_initial"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant().toString()),
        Timestamp.from(now),
        userId);
  }

  /** Open/partial order owned by the user. */
  public Optional<MarketOrderRecord> findOwnedActive(long orderId, int userId) {
    Instant now = Instant.now();
    cancelExpired(now);
    List<MarketOrderRecord> rows =
        jdbc.query(
            """
				SELECT %s
				FROM market_orders
				WHERE order_id = ?
				  AND user_id = ?
				  AND status IN ('open', 'partial')
				  AND quantity > 0
				  AND (expires_at IS NULL OR expires_at > ?)
				"""
                .formatted(COLUMNS),
            MAPPER,
            orderId,
            userId,
            Timestamp.from(now));
    return rows.stream().findFirst();
  }

  /** Cancel an owned open/partial order. Returns true if a row was updated. */
  public boolean cancelOwned(long orderId, int userId) {
    Instant now = Instant.now();
    cancelExpired(now);
    int updated =
        jdbc.update(
            """
				UPDATE market_orders
				SET status = 'cancelled', updated_at = CURRENT_TIMESTAMP
				WHERE order_id = ?
				  AND user_id = ?
				  AND status IN ('open', 'partial')
				  AND quantity > 0
				  AND (expires_at IS NULL OR expires_at > ?)
				""",
            orderId,
            userId,
            Timestamp.from(now));
    return updated == 1;
  }

  /** Edit price and remaining quantity on an owned open/partial order. */
  public Optional<MarketOrderRecord> editOwned(
      long orderId, int userId, BigDecimal price, int newRemaining) {
    Optional<MarketOrderRecord> existing = findOwnedActive(orderId, userId);
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    MarketOrderRecord order = existing.get();
    int filled = order.quantityInitial() - order.quantity();
    int newInitial = filled + newRemaining;
    String status = filled > 0 ? "partial" : "open";
    jdbc.update(
        """
				UPDATE market_orders
				SET price = ?, quantity = ?, quantity_initial = ?, status = ?,
				    updated_at = CURRENT_TIMESTAMP
				WHERE order_id = ?
				""",
        price,
        newRemaining,
        newInitial,
        status,
        orderId);
    return findOwnedActive(orderId, userId);
  }
}

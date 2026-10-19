package xyz.negativeone.app.support;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts market rows for tests. */
public final class MarketTestFixtures {

  public static final int SOURCE_EBAY = 1;
  public static final int SOURCE_VINTED = 2;
  public static final int SOURCE_APP = 6;

  public static final int LOCATION_ID = 1;
  public static final int LOCATION_LONDON = 2;
  public static final int GROUP_ROOT = 10;
  public static final int GROUP_PHONES = 11;
  public static final int GROUP_APPLE = 12;
  public static final int TYPE_ID = 1001;

  private MarketTestFixtures() {}

  /** Clears market rows so fixtures can use stable IDs. */
  public static void clearMarketData(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM market_fills");
    jdbc.update("DELETE FROM market_history");
    jdbc.update("DELETE FROM market_orders");
    jdbc.update("DELETE FROM market_prices");
    jdbc.update("DELETE FROM inv_types");
    jdbc.update("DELETE FROM market_groups");
    jdbc.update("DELETE FROM locations");
    jdbc.update("DELETE FROM sources");
  }

  /** Catalogue tree */
  public static void seedCatalogue(JdbcTemplate jdbc) {
    jdbc.update(
        "INSERT INTO sources (source_id, source_code, source_name, base_url) VALUES (?, ?, ?, ?)",
        SOURCE_EBAY,
        "ebay",
        "eBay",
        "https://www.ebay.co.uk");
    jdbc.update(
        "INSERT INTO sources (source_id, source_code, source_name, base_url) VALUES (?, ?, ?, ?)",
        SOURCE_VINTED,
        "vinted",
        "Vinted",
        "https://www.vinted.co.uk");
    jdbc.update(
        "INSERT INTO sources (source_id, source_code, source_name, base_url) VALUES (?, ?, ?, NULL)",
        SOURCE_APP,
        "mock_mm",
        "Market Maker");

    jdbc.update(
        "INSERT INTO locations (location_id, parent_location_id, location_name, description) VALUES (?, NULL, ?, ?)",
        LOCATION_ID,
        "UK",
        "Test location");
    jdbc.update(
        "INSERT INTO locations (location_id, parent_location_id, location_name, description) VALUES (?, ?, ?, ?)",
        LOCATION_LONDON,
        LOCATION_ID,
        "London",
        "Greater London");

    jdbc.update(
        """
				INSERT INTO market_groups
				    (market_group_id, parent_group_id, market_group_name, description, has_types)
				VALUES (?, NULL, ?, ?, FALSE)
				""",
        GROUP_ROOT,
        "Electronics",
        "Root");
    jdbc.update(
        """
				INSERT INTO market_groups
				    (market_group_id, parent_group_id, market_group_name, description, has_types)
				VALUES (?, ?, ?, ?, FALSE)
				""",
        GROUP_PHONES,
        GROUP_ROOT,
        "Phones",
        "Phones");
    jdbc.update(
        """
				INSERT INTO market_groups
				    (market_group_id, parent_group_id, market_group_name, description, has_types)
				VALUES (?, ?, ?, ?, TRUE)
				""",
        GROUP_APPLE,
        GROUP_PHONES,
        "Apple",
        "Apple");

    jdbc.update(
        """
				INSERT INTO inv_types
				    (type_id, type_name, description, base_price, published, market_group_id)
				VALUES (?, ?, ?, ?, TRUE, ?)
				""",
        TYPE_ID,
        "iPhone 13 128GB",
        "Fixture type",
        new BigDecimal("180.00"),
        GROUP_APPLE);
  }

  /** Open book: two buys (185, 170) and two sells (195, 210). */
  public static void seedOrderBook(JdbcTemplate jdbc) {
    insertOrder(
        jdbc, "sell", new BigDecimal("210.00"), SOURCE_EBAY, "external", "fixture-sell-high", null);
    insertOrder(
        jdbc,
        "sell",
        new BigDecimal("195.00"),
        SOURCE_VINTED,
        "external",
        "fixture-sell-ask",
        null);
    insertOrder(
        jdbc, "buy", new BigDecimal("170.00"), SOURCE_APP, "market_maker", "fixture-buy-low", null);
    insertOrder(
        jdbc,
        "buy",
        new BigDecimal("185.00"),
        SOURCE_APP,
        "market_maker",
        "fixture-buy-best",
        null);
  }

  public static void seedHistory(JdbcTemplate jdbc) {
    insertHistory(jdbc, LocalDate.of(2026, 10, 14), "175.00", "205.00", "190.00", 12, 8);
    insertHistory(jdbc, LocalDate.of(2026, 10, 15), "178.00", "200.00", "188.50", 9, 6);
    insertHistory(jdbc, LocalDate.of(2026, 10, 16), "172.00", "198.00", "185.00", 15, 11);
  }

  public static void seedBrowseMarket(JdbcTemplate jdbc) {
    clearMarketData(jdbc);
    seedCatalogue(jdbc);
    seedOrderBook(jdbc);
    seedHistory(jdbc);
  }

  /** Catalogue only */
  public static void seedCatalogueOnly(JdbcTemplate jdbc) {
    clearMarketData(jdbc);
    seedCatalogue(jdbc);
  }

  public static void seedOpenSell(JdbcTemplate jdbc, BigDecimal price, String listingId) {
    insertOrder(jdbc, "sell", price, SOURCE_VINTED, "external", listingId, null);
  }

  /** Open sell that is already past expires_at */
  public static void seedExpiredSell(JdbcTemplate jdbc, BigDecimal price, String listingId) {
    insertOrder(
        jdbc,
        "sell",
        price,
        SOURCE_VINTED,
        "external",
        listingId,
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)));
  }

  private static void insertOrder(
      JdbcTemplate jdbc,
      String side,
      BigDecimal price,
      int sourceId,
      String ownerKind,
      String ownerRef,
      java.sql.Timestamp expiresAt) {
    jdbc.update(
        """
				INSERT INTO market_orders (
				    type_id, location_id, source_id, side, price, quantity, quantity_initial,
				    status, external_url, external_listing_id, owner_kind, owner_ref,
				    user_id, expires_at)
				VALUES (?, ?, ?, ?, ?, 1, 1, 'open', NULL, ?, ?, ?, NULL, ?)
				""",
        TYPE_ID,
        LOCATION_ID,
        sourceId,
        side,
        price,
        ownerRef,
        ownerKind,
        ownerRef,
        expiresAt);
  }

  private static void insertHistory(
      JdbcTemplate jdbc,
      LocalDate date,
      String low,
      String high,
      String avg,
      long volume,
      long orders) {
    jdbc.update(
        """
				INSERT INTO market_history
				    (location_id, type_id, date, low_price, high_price, avg_price, volume, orders)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""",
        LOCATION_ID,
        TYPE_ID,
        Date.valueOf(date),
        new BigDecimal(low),
        new BigDecimal(high),
        new BigDecimal(avg),
        volume,
        orders);
  }
}

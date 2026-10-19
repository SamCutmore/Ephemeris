package xyz.negativeone.app.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xyz.negativeone.app.support.MarketTestFixtures.LOCATION_ID;
import static xyz.negativeone.app.support.MarketTestFixtures.SOURCE_VINTED;
import static xyz.negativeone.app.support.MarketTestFixtures.TYPE_ID;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.support.MarketTestFixtures;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SellOrderIngestSolverTest {

  private static final String INGEST = "dev-ingest-token";

  @Autowired private SolverDispatcher dispatcher;

  @Autowired private JsonMapper jsonMapper;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    MarketTestFixtures.seedCatalogueOnly(jdbc);
  }

  @Test
  void placeSellOrderCreatesExternalSellWithLastUpdated() {
    String listing = "vinted-" + UUID.randomUUID();
    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve(
                "placeSellOrder: "
                    + TYPE_ID
                    + ";"
                    + LOCATION_ID
                    + ";"
                    + SOURCE_VINTED
                    + ";195.50;2;"
                    + listing
                    + ";"
                    + INGEST));

    assertTrue(body.get("orderId").asLong() > 0);
    assertEquals(SOURCE_VINTED, body.get("sourceId").asInt());
    assertEquals(listing, body.get("externalListingId").asString());
    assertEquals(195.50, body.get("price").asDouble());
    assertEquals(2, body.get("quantity").asInt());
    assertEquals("open", body.get("status").asString());
    assertEquals(0, body.get("fills").asInt());
    assertTrue(body.get("lastUpdated").asString().length() > 0);
    Instant.parse(body.get("lastUpdated").asString());

    String ownerKind =
        jdbc.queryForObject(
            "SELECT owner_kind FROM market_orders WHERE order_id = ?",
            String.class,
            body.get("orderId").asLong());
    assertEquals("external", ownerKind);
  }

  @Test
  void placeSellOrderUpsertsSameListingAndTouchesLastUpdated() throws InterruptedException {
    String listing = "vinted-upsert";
    JsonNode first =
        jsonMapper.readTree(
            dispatcher.solve(
                "placeSellOrder: "
                    + TYPE_ID
                    + ";"
                    + LOCATION_ID
                    + ";"
                    + SOURCE_VINTED
                    + ";200;1;"
                    + listing
                    + ";"
                    + INGEST));
    long orderId = first.get("orderId").asLong();
    Instant firstUpdated = Instant.parse(first.get("lastUpdated").asString());

    Thread.sleep(5);

    JsonNode second =
        jsonMapper.readTree(
            dispatcher.solve(
                "placeSellOrder: "
                    + TYPE_ID
                    + ";"
                    + LOCATION_ID
                    + ";"
                    + SOURCE_VINTED
                    + ";180;3;"
                    + listing
                    + ";"
                    + INGEST));

    assertEquals(orderId, second.get("orderId").asLong());
    assertEquals(180.0, second.get("price").asDouble());
    assertEquals(3, second.get("quantity").asInt());
    Instant secondUpdated = Instant.parse(second.get("lastUpdated").asString());
    assertTrue(secondUpdated.equals(firstUpdated) || secondUpdated.isAfter(firstUpdated));

    Integer openCount =
        jdbc.queryForObject(
            """
				SELECT COUNT(*) FROM market_orders
				WHERE source_id = ? AND external_listing_id = ?
				  AND status IN ('open', 'partial')
				""",
            Integer.class,
            SOURCE_VINTED,
            listing);
    assertEquals(1, openCount);
  }

  @Test
  void placeSellOrderMatchesRestingBuy() {
    String token = registerAndLogin();
    dispatcher.solve("placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";195;1;" + token);

    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve(
                "placeSellOrder: "
                    + TYPE_ID
                    + ";"
                    + LOCATION_ID
                    + ";"
                    + SOURCE_VINTED
                    + ";195;1;ask-match;"
                    + INGEST));

    assertEquals(1, body.get("fills").asInt());
  }

  @Test
  void placeSellOrderRejectsBadIngestionToken() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                dispatcher.solve(
                    "placeSellOrder: "
                        + TYPE_ID
                        + ";"
                        + LOCATION_ID
                        + ";"
                        + SOURCE_VINTED
                        + ";100;1;listing;wrong-token"));
    assertEquals("Unauthorized", ex.getMessage());
  }

  @Test
  void editSellOrderUpdatesPriceAndLastUpdated() {
    dispatcher.solve(
        "placeSellOrder: "
            + TYPE_ID
            + ";"
            + LOCATION_ID
            + ";"
            + SOURCE_VINTED
            + ";200;2;edit-me;"
            + INGEST);

    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve("editSellOrder: " + SOURCE_VINTED + ";edit-me;175.25;1;" + INGEST));

    assertEquals(175.25, body.get("price").asDouble());
    assertEquals(1, body.get("quantity").asInt());
    assertTrue(body.hasNonNull("lastUpdated"));
    Instant.parse(body.get("lastUpdated").asString());
  }

  @Test
  void cancelSellOrderMarksCancelledAndReturnsLastUpdated() {
    dispatcher.solve(
        "placeSellOrder: "
            + TYPE_ID
            + ";"
            + LOCATION_ID
            + ";"
            + SOURCE_VINTED
            + ";200;1;cancel-me;"
            + INGEST);

    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve("cancelSellOrder: " + SOURCE_VINTED + ";cancel-me;" + INGEST));

    assertEquals("cancelled", body.get("status").asString());
    assertEquals("cancel-me", body.get("externalListingId").asString());
    Instant.parse(body.get("lastUpdated").asString());

    assertThrows(
        IllegalArgumentException.class,
        () -> dispatcher.solve("cancelSellOrder: " + SOURCE_VINTED + ";cancel-me;" + INGEST));
  }

  @Test
  void cancelStaleSellOrdersRemovesListingsNotTouchedInLatestIngest() {
    dispatcher.solve(
        "placeSellOrder: "
            + TYPE_ID
            + ";"
            + LOCATION_ID
            + ";"
            + SOURCE_VINTED
            + ";210;1;stale-gone;"
            + INGEST);
    jdbc.update(
        """
				UPDATE market_orders
				SET updated_at = ?
				WHERE external_listing_id = ?
				""",
        java.sql.Timestamp.from(Instant.now().minusSeconds(120)),
        "stale-gone");

    Instant syncStart = Instant.now();
    JsonNode kept =
        jsonMapper.readTree(
            dispatcher.solve(
                "placeSellOrder: "
                    + TYPE_ID
                    + ";"
                    + LOCATION_ID
                    + ";"
                    + SOURCE_VINTED
                    + ";200;1;kept-live;"
                    + INGEST));
    assertTrue(Instant.parse(kept.get("lastUpdated").asString()).compareTo(syncStart) >= 0);

    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve(
                "cancelStaleSellOrders: "
                    + SOURCE_VINTED
                    + ";"
                    + syncStart.toEpochMilli()
                    + ";"
                    + INGEST));

    assertEquals(1, body.get("cancelled").asInt());

    String keptStatus =
        jdbc.queryForObject(
            "SELECT status FROM market_orders WHERE order_id = ?",
            String.class,
            kept.get("orderId").asLong());
    assertEquals("open", keptStatus);

    String staleStatus =
        jdbc.queryForObject(
            "SELECT status FROM market_orders WHERE external_listing_id = ?",
            String.class,
            "stale-gone");
    assertEquals("cancelled", staleStatus);
  }

  @Test
  void placeSellOrderRejectsUnknownTypeAndSource() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            dispatcher.solve(
                "placeSellOrder: 99999;"
                    + LOCATION_ID
                    + ";"
                    + SOURCE_VINTED
                    + ";100;1;x;"
                    + INGEST));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            dispatcher.solve(
                "placeSellOrder: " + TYPE_ID + ";" + LOCATION_ID + ";99999;100;1;x;" + INGEST));
  }

  @Test
  void getOrderBookExposesUpdatedAtForSells() {
    dispatcher.solve(
        "placeSellOrder: "
            + TYPE_ID
            + ";"
            + LOCATION_ID
            + ";"
            + SOURCE_VINTED
            + ";199;1;book-sell;"
            + INGEST);

    JsonNode book =
        jsonMapper.readTree(dispatcher.solve("getOrderBook: " + TYPE_ID + ";" + LOCATION_ID));
    assertEquals(1, book.get("sells").size());
    assertTrue(book.get("sells").get(0).hasNonNull("updatedAt"));
    assertEquals("book-sell", book.get("sells").get(0).get("externalListingId").asString());
    Instant.parse(book.get("sells").get(0).get("updatedAt").asString());
  }

  private String registerAndLogin() {
    String user = "buyer-" + UUID.randomUUID();
    assertEquals("OK", dispatcher.solve("register: " + user + ";secret;secret"));
    JsonNode login = jsonMapper.readTree(dispatcher.solve("login: " + user + ";secret"));
    return login.get("token").asString();
  }
}

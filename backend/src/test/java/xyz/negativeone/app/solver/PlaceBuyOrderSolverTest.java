package xyz.negativeone.app.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xyz.negativeone.app.support.MarketTestFixtures.LOCATION_ID;
import static xyz.negativeone.app.support.MarketTestFixtures.TYPE_ID;

import java.math.BigDecimal;
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
class PlaceBuyOrderSolverTest {

  @Autowired private SolverDispatcher dispatcher;

  @Autowired private JsonMapper jsonMapper;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    MarketTestFixtures.seedCatalogueOnly(jdbc);
  }

  @Test
  void placeBuyMatchesOpenSellAtAsk() {
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("195.00"), "ask-195");
    String token = registerAndLogin();

    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve("placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";195;1;" + token));

    assertTrue(body.get("orderId").asLong() > 0);
    assertEquals(1, body.get("fills").asInt());

    Integer openSells =
        jdbc.queryForObject(
            """
				SELECT COUNT(*) FROM market_orders
				WHERE type_id = ? AND location_id = ? AND side = 'sell'
				  AND status IN ('open', 'partial') AND quantity > 0
				""",
            Integer.class,
            TYPE_ID,
            LOCATION_ID);
    assertEquals(0, openSells);

    Integer fillRows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM market_fills WHERE type_id = ? AND location_id = ?",
            Integer.class,
            TYPE_ID,
            LOCATION_ID);
    assertEquals(1, fillRows);
  }

  @Test
  void placeBuyRestsWhenBelowBestAsk() {
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("195.00"), "ask-195");
    String token = registerAndLogin();

    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve("placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";180;1;" + token));

    assertEquals(0, body.get("fills").asInt());

    JsonNode book =
        jsonMapper.readTree(dispatcher.solve("getOrderBook: " + TYPE_ID + ";" + LOCATION_ID));
    assertEquals(1, book.get("buys").size());
    assertEquals(180.0, book.get("buys").get(0).get("price").asDouble());
    assertEquals(1, book.get("sells").size());
    assertEquals(195.0, book.get("sells").get(0).get("price").asDouble());
  }

  @Test
  void placeBuyRejectsMissingSession() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            dispatcher.solve(
                "placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";195;1;" + "a".repeat(64)));
  }

  @Test
  void placeBuyRejectsUnknownType() {
    String token = registerAndLogin();
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> dispatcher.solve("placeBuyOrder: 99999;" + LOCATION_ID + ";195;1;" + token));
    assertEquals("Unknown typeId", ex.getMessage());
  }

  @Test
  void placeBuySetsUserIdAndExpiry() {
    String token = registerAndLogin();
    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve("placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";100;1;" + token));

    assertTrue(body.get("userId").asInt() > 0);
    assertTrue(body.get("expiresAt").asString().length() > 0);

    Integer storedUser =
        jdbc.queryForObject(
            "SELECT user_id FROM market_orders WHERE order_id = ?",
            Integer.class,
            body.get("orderId").asLong());
    assertEquals(body.get("userId").asInt(), storedUser);

    Integer withExpiry =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM market_orders WHERE order_id = ? AND expires_at IS NOT NULL",
            Integer.class,
            body.get("orderId").asLong());
    assertEquals(1, withExpiry);
  }

  @Test
  void placeBuyDoesNotMatchExpiredSell() {
    MarketTestFixtures.seedExpiredSell(jdbc, new BigDecimal("195.00"), "expired-ask");
    String token = registerAndLogin();

    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve("placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";195;1;" + token));

    assertEquals(0, body.get("fills").asInt());
    Integer cancelled =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM market_orders WHERE external_listing_id = ? AND status = 'cancelled'",
            Integer.class,
            "expired-ask");
    assertEquals(1, cancelled);
  }

  private String registerAndLogin() {
    String user = "buyer-" + UUID.randomUUID();
    assertEquals("OK", dispatcher.solve("register: " + user + ";secret;secret"));
    JsonNode login = jsonMapper.readTree(dispatcher.solve("login: " + user + ";secret"));
    return login.get("token").asString();
  }
}

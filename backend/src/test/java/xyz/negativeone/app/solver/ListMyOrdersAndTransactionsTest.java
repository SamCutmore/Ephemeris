package xyz.negativeone.app.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class ListMyOrdersAndTransactionsTest {

  @Autowired private SolverDispatcher dispatcher;

  @Autowired private JsonMapper jsonMapper;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    MarketTestFixtures.seedCatalogueOnly(jdbc);
  }

  @Test
  void openBuyAppearsInMyOrdersNotTransactions() {
    String token = registerAndLogin();
    dispatcher.solve("placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";100;1;" + token);

    JsonNode open = jsonMapper.readTree(dispatcher.solve("listMyOrders: " + token));
    assertEquals(1, open.size());
    assertEquals("open", open.get(0).get("status").asString());

    JsonNode tx = jsonMapper.readTree(dispatcher.solve("listMyTransactions: " + token));
    assertEquals(0, tx.size());
  }

  @Test
  void filledBuyAppearsInTransactionsWithFill() {
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("195.00"), "ask-195");
    String token = registerAndLogin();
    dispatcher.solve("placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";195;1;" + token);

    JsonNode open = jsonMapper.readTree(dispatcher.solve("listMyOrders: " + token));
    assertEquals(0, open.size());

    JsonNode tx = jsonMapper.readTree(dispatcher.solve("listMyTransactions: " + token));
    assertEquals(1, tx.size());
    assertEquals("filled", tx.get(0).get("order").get("status").asString());
    assertEquals(1, tx.get(0).get("fills").size());
    assertEquals(195.0, tx.get(0).get("fills").get(0).get("price").asDouble());
  }

  @Test
  void cancelledExpiredBuyAppearsInTransactions() {
    String token = registerAndLogin();
    JsonNode placed =
        jsonMapper.readTree(
            dispatcher.solve("placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";100;1;" + token));
    long orderId = placed.get("orderId").asLong();

    jdbc.update(
        "UPDATE market_orders SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' HOUR WHERE order_id = ?",
        orderId);

    JsonNode open = jsonMapper.readTree(dispatcher.solve("listMyOrders: " + token));
    assertEquals(0, open.size());

    JsonNode tx = jsonMapper.readTree(dispatcher.solve("listMyTransactions: " + token));
    assertEquals(1, tx.size());
    assertEquals("cancelled", tx.get(0).get("order").get("status").asString());
    assertTrue(tx.get(0).get("fills").isEmpty());
  }

  private String registerAndLogin() {
    String user = "hist-" + UUID.randomUUID();
    assertEquals("OK", dispatcher.solve("register: " + user + ";secret;secret"));
    return jsonMapper
        .readTree(dispatcher.solve("login: " + user + ";secret"))
        .get("token")
        .asString();
  }
}

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
class CancelAndEditOrderSolverTest {

  @Autowired private SolverDispatcher dispatcher;

  @Autowired private JsonMapper jsonMapper;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    MarketTestFixtures.seedCatalogueOnly(jdbc);
  }

  @Test
  void cancelOrderMarksOwnedOpenAsCancelled() {
    String token = registerAndLogin();
    long orderId = placeRestingBuy(token, "100", 2);

    JsonNode body = jsonMapper.readTree(dispatcher.solve("cancelOrder: " + orderId + ";" + token));
    assertEquals(orderId, body.get("orderId").asLong());
    assertEquals("cancelled", body.get("status").asString());

    String status =
        jdbc.queryForObject(
            "SELECT status FROM market_orders WHERE order_id = ?", String.class, orderId);
    assertEquals("cancelled", status);
  }

  @Test
  void cancelOrderRejectsOtherUsersOrder() {
    String owner = registerAndLogin();
    long orderId = placeRestingBuy(owner, "100", 1);
    String other = registerAndLogin();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> dispatcher.solve("cancelOrder: " + orderId + ";" + other));
    assertEquals("Order not found or not cancellable", ex.getMessage());
  }

  @Test
  void cancelOrderRejectsAlreadyCancelled() {
    String token = registerAndLogin();
    long orderId = placeRestingBuy(token, "100", 1);
    dispatcher.solve("cancelOrder: " + orderId + ";" + token);

    assertThrows(
        IllegalArgumentException.class,
        () -> dispatcher.solve("cancelOrder: " + orderId + ";" + token));
  }

  @Test
  void editOrderUpdatesPriceAndRemaining() {
    String token = registerAndLogin();
    long orderId = placeRestingBuy(token, "100", 5);

    JsonNode body =
        jsonMapper.readTree(dispatcher.solve("editOrder: " + orderId + ";120;3;" + token));

    assertEquals(orderId, body.get("orderId").asLong());
    assertEquals(120.0, body.get("price").asDouble());
    assertEquals(3, body.get("quantity").asInt());
    assertEquals(3, body.get("quantityInitial").asInt());
    assertEquals(0, body.get("fills").asInt());

    JsonNode book =
        jsonMapper.readTree(dispatcher.solve("getOrderBook: " + TYPE_ID + ";" + LOCATION_ID));
    assertEquals(1, book.get("buys").size());
    assertEquals(120.0, book.get("buys").get(0).get("price").asDouble());
    assertEquals(3, book.get("buys").get(0).get("quantity").asInt());
  }

  @Test
  void editOrderPreservesFilledAmount() {
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("100.00"), "ask-partial");
    String token = registerAndLogin();

    JsonNode placed =
        jsonMapper.readTree(
            dispatcher.solve("placeBuyOrder: " + TYPE_ID + ";" + LOCATION_ID + ";100;3;" + token));
    long orderId = placed.get("orderId").asLong();
    assertEquals(1, placed.get("fills").asInt());

    Integer remainingBefore =
        jdbc.queryForObject(
            "SELECT quantity FROM market_orders WHERE order_id = ?", Integer.class, orderId);
    assertEquals(2, remainingBefore);

    JsonNode edited =
        jsonMapper.readTree(dispatcher.solve("editOrder: " + orderId + ";100;4;" + token));

    assertEquals(4, edited.get("quantity").asInt());
    assertEquals(5, edited.get("quantityInitial").asInt());
    assertEquals("partial", edited.get("status").asString());
  }

  @Test
  void editOrderCanMatchAfterPriceIncrease() {
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("195.00"), "ask-195");
    String token = registerAndLogin();
    long orderId = placeRestingBuy(token, "100", 1);

    JsonNode body =
        jsonMapper.readTree(dispatcher.solve("editOrder: " + orderId + ";195;1;" + token));

    assertEquals(1, body.get("fills").asInt());
    String status =
        jdbc.queryForObject(
            "SELECT status FROM market_orders WHERE order_id = ?", String.class, orderId);
    assertEquals("filled", status);
  }

  @Test
  void editOrderRejectsMissingSession() {
    assertThrows(
        IllegalArgumentException.class,
        () -> dispatcher.solve("editOrder: 1;100;1;" + "a".repeat(64)));
  }

  private long placeRestingBuy(String token, String price, int qty) {
    JsonNode body =
        jsonMapper.readTree(
            dispatcher.solve(
                "placeBuyOrder: "
                    + TYPE_ID
                    + ";"
                    + LOCATION_ID
                    + ";"
                    + price
                    + ";"
                    + qty
                    + ";"
                    + token));
    assertTrue(body.get("orderId").asLong() > 0);
    assertEquals(0, body.get("fills").asInt());
    return body.get("orderId").asLong();
  }

  private String registerAndLogin() {
    String user = "trader-" + UUID.randomUUID();
    assertEquals("OK", dispatcher.solve("register: " + user + ";secret;secret"));
    JsonNode login = jsonMapper.readTree(dispatcher.solve("login: " + user + ";secret"));
    return login.get("token").asString();
  }
}

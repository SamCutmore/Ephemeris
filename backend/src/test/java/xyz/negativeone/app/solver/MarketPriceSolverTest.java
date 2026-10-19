package xyz.negativeone.app.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xyz.negativeone.app.support.MarketTestFixtures.LOCATION_ID;
import static xyz.negativeone.app.support.MarketTestFixtures.TYPE_ID;

import java.math.BigDecimal;
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
class MarketPriceSolverTest {

  @Autowired private SolverDispatcher dispatcher;

  @Autowired private JsonMapper jsonMapper;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    MarketTestFixtures.seedCatalogueOnly(jdbc);
  }

  @Test
  void getMarketPriceEmptyWhenNeverRefreshed() {
    JsonNode body =
        jsonMapper.readTree(dispatcher.solve("getMarketPrice: " + TYPE_ID + ";" + LOCATION_ID));
    assertEquals(TYPE_ID, body.get("typeId").asInt());
    assertEquals(0, body.get("sampleSize").asInt());
    assertTrue(body.get("medianPrice").isNull());
    assertTrue(body.get("lowestPrice").isNull());
  }

  @Test
  void refreshMarketPriceComputesMedianAndLowestFromOpenSells() {
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("100.00"), "p1");
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("200.00"), "p2");
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("300.00"), "p3");

    JsonNode refreshed =
        jsonMapper.readTree(dispatcher.solve("refreshMarketPrice: " + TYPE_ID + ";" + LOCATION_ID));

    assertEquals(3, refreshed.get("sampleSize").asInt());
    assertEquals(200.0, refreshed.get("medianPrice").asDouble());
    assertEquals(100.0, refreshed.get("lowestPrice").asDouble());

    JsonNode cached =
        jsonMapper.readTree(dispatcher.solve("getMarketPrice: " + TYPE_ID + ";" + LOCATION_ID));
    assertEquals(200.0, cached.get("medianPrice").asDouble());
    assertEquals(100.0, cached.get("lowestPrice").asDouble());
  }

  @Test
  void refreshMarketPriceEvenSampleUsesMidpointMedian() {
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("100.00"), "a");
    MarketTestFixtures.seedOpenSell(jdbc, new BigDecimal("200.00"), "b");

    JsonNode body =
        jsonMapper.readTree(dispatcher.solve("refreshMarketPrice: " + TYPE_ID + ";" + LOCATION_ID));

    assertEquals(2, body.get("sampleSize").asInt());
    assertEquals(150.0, body.get("medianPrice").asDouble());
    assertEquals(100.0, body.get("lowestPrice").asDouble());
  }

  @Test
  void refreshMarketPriceWithNoSellsStoresEmptySample() {
    JsonNode body =
        jsonMapper.readTree(dispatcher.solve("refreshMarketPrice: " + TYPE_ID + ";" + LOCATION_ID));
    assertEquals(0, body.get("sampleSize").asInt());
    assertTrue(body.get("medianPrice").isNull());
    assertTrue(body.get("lowestPrice").isNull());
  }

  @Test
  void refreshMarketPriceRejectsUnknownType() {
    assertThrows(
        IllegalArgumentException.class,
        () -> dispatcher.solve("refreshMarketPrice: 99999;" + LOCATION_ID));
  }
}

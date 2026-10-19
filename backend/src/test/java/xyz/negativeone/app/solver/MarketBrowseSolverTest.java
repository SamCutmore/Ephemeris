package xyz.negativeone.app.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xyz.negativeone.app.support.MarketTestFixtures.GROUP_APPLE;
import static xyz.negativeone.app.support.MarketTestFixtures.GROUP_PHONES;
import static xyz.negativeone.app.support.MarketTestFixtures.GROUP_ROOT;
import static xyz.negativeone.app.support.MarketTestFixtures.LOCATION_ID;
import static xyz.negativeone.app.support.MarketTestFixtures.LOCATION_LONDON;
import static xyz.negativeone.app.support.MarketTestFixtures.TYPE_ID;

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
class MarketBrowseSolverTest {

  @Autowired private SolverDispatcher dispatcher;

  @Autowired private JsonMapper jsonMapper;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    MarketTestFixtures.seedBrowseMarket(jdbc);
  }

  @Test
  void listMarketGroupsRootReturnsElectronics() {
    JsonNode groups = jsonMapper.readTree(dispatcher.solve("listMarketGroups: root"));
    assertEquals(1, groups.size());
    assertEquals(GROUP_ROOT, groups.get(0).get("marketGroupId").asInt());
    assertEquals("Electronics", groups.get(0).get("marketGroupName").asString());
  }

  @Test
  void listMarketGroupsDrillsIntoPhonesThenApple() {
    JsonNode phones = jsonMapper.readTree(dispatcher.solve("listMarketGroups: " + GROUP_ROOT));
    assertEquals(1, phones.size());
    assertEquals(GROUP_PHONES, phones.get(0).get("marketGroupId").asInt());
    assertEquals("Phones", phones.get(0).get("marketGroupName").asString());

    JsonNode apple = jsonMapper.readTree(dispatcher.solve("listMarketGroups: " + GROUP_PHONES));
    assertEquals(1, apple.size());
    assertEquals(GROUP_APPLE, apple.get(0).get("marketGroupId").asInt());
    assertEquals("Apple", apple.get(0).get("marketGroupName").asString());
    assertTrue(apple.get(0).get("hasTypes").asBoolean());
  }

  @Test
  void listLocationsRootReturnsUk() {
    JsonNode roots = jsonMapper.readTree(dispatcher.solve("listLocations: root"));
    assertEquals(1, roots.size());
    assertEquals(LOCATION_ID, roots.get(0).get("locationId").asInt());
    assertEquals("UK", roots.get(0).get("locationName").asString());
  }

  @Test
  void listLocationsDrillsIntoLondon() {
    JsonNode children = jsonMapper.readTree(dispatcher.solve("listLocations: " + LOCATION_ID));
    assertEquals(1, children.size());
    assertEquals(LOCATION_LONDON, children.get(0).get("locationId").asInt());
    assertEquals("London", children.get(0).get("locationName").asString());
    assertEquals(LOCATION_ID, children.get(0).get("parentLocationId").asInt());
  }

  @Test
  void listTypesReturnsFixtureType() {
    JsonNode types = jsonMapper.readTree(dispatcher.solve("listTypes: " + GROUP_APPLE));
    assertEquals(1, types.size());
    assertEquals(TYPE_ID, types.get(0).get("typeId").asInt());
    assertEquals("iPhone 13 128GB", types.get(0).get("typeName").asString());
  }

  @Test
  void listTypesUnknownGroupReturnsEmptyArray() {
    JsonNode types = jsonMapper.readTree(dispatcher.solve("listTypes: 99999"));
    assertTrue(types.isArray());
    assertEquals(0, types.size());
  }

  @Test
  void searchTypesFindsByNameSubstring() {
    JsonNode types = jsonMapper.readTree(dispatcher.solve("searchTypes: iphone"));
    assertTrue(types.size() >= 1);
    assertEquals(TYPE_ID, types.get(0).get("typeId").asInt());
  }

  @Test
  void searchTypesRejectsShortQuery() {
    assertThrows(IllegalArgumentException.class, () -> dispatcher.solve("searchTypes: a"));
  }

  @Test
  void getOrderBookReturnsFixtureBuysAndSellsSorted() {
    JsonNode book =
        jsonMapper.readTree(dispatcher.solve("getOrderBook: " + TYPE_ID + ";" + LOCATION_ID));
    assertEquals(TYPE_ID, book.get("typeId").asInt());
    assertEquals(LOCATION_ID, book.get("locationId").asInt());

    JsonNode buys = book.get("buys");
    JsonNode sells = book.get("sells");
    assertEquals(2, buys.size());
    assertEquals(2, sells.size());

    assertEquals(185.0, buys.get(0).get("price").asDouble());
    assertEquals(170.0, buys.get(1).get("price").asDouble());
    assertEquals(195.0, sells.get(0).get("price").asDouble());
    assertEquals(210.0, sells.get(1).get("price").asDouble());
  }

  @Test
  void getHistoryReturnsFixtureBars() {
    JsonNode history =
        jsonMapper.readTree(dispatcher.solve("getHistory: " + TYPE_ID + ";" + LOCATION_ID));
    assertEquals(3, history.size());
    assertEquals(TYPE_ID, history.get(0).get("typeId").asInt());
    assertEquals(LOCATION_ID, history.get(0).get("locationId").asInt());
    assertEquals(190.0, history.get(0).get("avgPrice").asDouble());
  }

  @Test
  void getOrderBookEmptyForUnknownType() {
    JsonNode book = jsonMapper.readTree(dispatcher.solve("getOrderBook: 99999;" + LOCATION_ID));
    assertEquals(0, book.get("buys").size());
    assertEquals(0, book.get("sells").size());
  }
}

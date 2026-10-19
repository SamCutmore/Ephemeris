package xyz.negativeone.app.fixtures;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.persistence.InvTypeRepository;
import xyz.negativeone.app.persistence.MarketGroupRepository;
import xyz.negativeone.app.persistence.MarketHistoryRepository;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.persistence.MarketPriceRepository;

/** Loads classpath JSON fixtures into the database: catalogue → history → sells → prices. */
@Component
@ConditionalOnProperty(name = "negativeone.fixtures.load", havingValue = "true")
public class MarketFixtureLoader implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(MarketFixtureLoader.class);

  private static final String BASE = "fixtures/market/";

  private final JsonMapper jsonMapper;
  private final MarketGroupRepository groups;
  private final InvTypeRepository types;
  private final MarketHistoryRepository history;
  private final MarketOrderRepository orders;
  private final MarketPriceRepository prices;
  private final EbayListingNormalizer ebay;
  private final AmazonListingNormalizer amazon;

  public MarketFixtureLoader(
      JsonMapper jsonMapper,
      MarketGroupRepository groups,
      InvTypeRepository types,
      MarketHistoryRepository history,
      MarketOrderRepository orders,
      MarketPriceRepository prices,
      EbayListingNormalizer ebay,
      AmazonListingNormalizer amazon) {
    this.jsonMapper = jsonMapper;
    this.groups = groups;
    this.types = types;
    this.history = history;
    this.orders = orders;
    this.prices = prices;
    this.ebay = ebay;
    this.amazon = amazon;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    logger.info("Loading market fixtures from classpath {}", BASE);
    loadCatalogue(readTree(BASE + "catalogue.json"));
    int historyRows = loadHistory(readTree(BASE + "history.json"));
    List<ExternalListing> listings = ebay.normalize(readTree(BASE + "sources/ebay/listings.json"));
    listings.addAll(amazon.normalize(readTree(BASE + "sources/amazon/listings.json")));
    Set<String> books = new HashSet<>();
    for (ExternalListing listing : listings) {
      orders.upsertExternalSell(
          listing.typeId(),
          listing.locationId(),
          listing.sourceId(),
          listing.price(),
          listing.quantity(),
          listing.externalListingId(),
          listing.externalUrl());
      books.add(listing.typeId() + ":" + listing.locationId());
    }
    for (String key : books) {
      String[] parts = key.split(":");
      prices.refreshFromOpenSells(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
    logger.info(
        "Market fixtures loaded: historyRows={} listings={} priceBooks={}",
        historyRows,
        listings.size(),
        books.size());
  }

  private void loadCatalogue(JsonNode root) {
    JsonNode groupNodes = root.path("groups");
    if (!groupNodes.isArray()) {
      throw new IllegalArgumentException("catalogue.json: missing groups array");
    }
    for (JsonNode g : groupNodes) {
      Integer parent =
          g.path("parentGroupId").isNull() || g.path("parentGroupId").isMissingNode()
              ? null
              : g.path("parentGroupId").asInt();
      groups.upsert(
          g.path("marketGroupId").asInt(),
          parent,
          g.path("name").asString(),
          g.path("description").asString(null),
          g.path("hasTypes").asBoolean(true));
    }
    JsonNode typeNodes = root.path("types");
    if (!typeNodes.isArray()) {
      throw new IllegalArgumentException("catalogue.json: missing types array");
    }
    for (JsonNode t : typeNodes) {
      BigDecimal base =
          t.path("basePrice").isMissingNode() || t.path("basePrice").isNull()
              ? null
              : t.path("basePrice").decimalValue();
      Integer groupId =
          t.path("marketGroupId").isMissingNode() || t.path("marketGroupId").isNull()
              ? null
              : t.path("marketGroupId").asInt();
      types.upsert(
          t.path("typeId").asInt(),
          t.path("typeName").asString(),
          t.path("description").asString(null),
          base,
          t.path("published").asBoolean(true),
          groupId);
    }
  }

  private int loadHistory(JsonNode root) {
    if (!root.isArray()) {
      throw new IllegalArgumentException("history.json: expected array");
    }
    int n = 0;
    for (JsonNode row : root) {
      history.upsert(
          row.path("locationId").asInt(),
          row.path("typeId").asInt(),
          LocalDate.parse(row.path("date").asString()),
          row.path("lowPrice").decimalValue(),
          row.path("highPrice").decimalValue(),
          row.path("avgPrice").decimalValue(),
          row.path("volume").asLong(),
          row.path("orders").asLong());
      n++;
    }
    return n;
  }

  private JsonNode readTree(String classpath) throws IOException {
    ClassPathResource resource = new ClassPathResource(classpath);
    if (!resource.exists()) {
      throw new IllegalStateException("Missing fixture resource: " + classpath);
    }
    try (InputStream in = resource.getInputStream()) {
      return jsonMapper.readTree(in);
    }
  }
}

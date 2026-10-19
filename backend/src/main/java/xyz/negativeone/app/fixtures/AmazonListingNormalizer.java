package xyz.negativeone.app.fixtures;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Maps Amazon-shaped listing JSON into {@link ExternalListing}. */
@Component
public class AmazonListingNormalizer {

  public static final int SOURCE_ID = 5;

  public List<ExternalListing> normalize(JsonNode root) {
    JsonNode items = root.path("items");
    if (!items.isArray()) {
      throw new IllegalArgumentException("amazon listings.json: missing items array");
    }
    List<ExternalListing> out = new ArrayList<>();
    for (JsonNode item : items) {
      String asin = text(item, "asin");
      int typeId = item.path("typeId").asInt(0);
      int locationId = item.path("locationId").asInt(1);
      JsonNode offers = item.path("offers");
      BigDecimal price = offers.path("price").decimalValue();
      int quantity = offers.path("count").asInt(1);
      String url = text(item, "detailPageURL");
      if (typeId <= 0 || asin.isBlank()) {
        throw new IllegalArgumentException("amazon listing missing typeId or asin");
      }
      if (price == null || price.signum() <= 0 || quantity <= 0) {
        throw new IllegalArgumentException("amazon listing invalid price/count: " + asin);
      }
      out.add(new ExternalListing(SOURCE_ID, typeId, locationId, price, quantity, asin, url));
    }
    return out;
  }

  private static String text(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isMissingNode() || v.isNull() ? "" : v.asString();
  }
}

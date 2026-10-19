package xyz.negativeone.app.fixtures;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Maps eBay-shaped listing JSON into {@link ExternalListing}. */
@Component
public class EbayListingNormalizer {

  public static final int SOURCE_ID = 1;

  public List<ExternalListing> normalize(JsonNode root) {
    JsonNode items = root.path("items");
    if (!items.isArray()) {
      throw new IllegalArgumentException("ebay listings.json: missing items array");
    }
    List<ExternalListing> out = new ArrayList<>();
    for (JsonNode item : items) {
      String itemId = text(item, "itemId");
      int typeId = item.path("typeId").asInt(0);
      int locationId = item.path("locationId").asInt(1);
      JsonNode priceNode = item.path("price");
      BigDecimal price = new BigDecimal(text(priceNode, "value"));
      int quantity = item.path("quantity").asInt(1);
      String url = text(item, "itemWebUrl");
      if (typeId <= 0 || itemId.isBlank()) {
        throw new IllegalArgumentException("ebay listing missing typeId or itemId");
      }
      if (price.signum() <= 0 || quantity <= 0) {
        throw new IllegalArgumentException("ebay listing invalid price/quantity: " + itemId);
      }
      out.add(new ExternalListing(SOURCE_ID, typeId, locationId, price, quantity, itemId, url));
    }
    return out;
  }

  private static String text(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isMissingNode() || v.isNull() ? "" : v.asString();
  }
}

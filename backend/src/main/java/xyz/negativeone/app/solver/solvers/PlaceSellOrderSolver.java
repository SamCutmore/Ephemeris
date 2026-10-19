package xyz.negativeone.app.solver.solvers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.IngestTokenService;
import xyz.negativeone.app.market.MarketMaker;
import xyz.negativeone.app.persistence.InvTypeRepository;
import xyz.negativeone.app.persistence.MarketOrderRecord;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.solver.Query;

/**
 * Ingest / upsert an external sell listing. q=placeSellOrder:
 * typeId;locationId;sourceId;price;qty;externalListingId;ingestionToken
 */
public class PlaceSellOrderSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(PlaceSellOrderSolver.class);

  private final IngestTokenService ingestTokens;
  private final InvTypeRepository types;
  private final MarketOrderRepository orders;
  private final MarketMaker marketMaker;
  private final JsonMapper jsonMapper;

  public PlaceSellOrderSolver(
      IngestTokenService ingestTokens,
      InvTypeRepository types,
      MarketOrderRepository orders,
      MarketMaker marketMaker,
      JsonMapper jsonMapper) {
    super("^(\\d+);(\\d+);(\\d+);([0-9]+(?:\\.[0-9]+)?);(\\d+);([^;]+);([^;]+)$");
    this.ingestTokens = ingestTokens;
    this.types = types;
    this.orders = orders;
    this.marketMaker = marketMaker;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"placeSellOrder".equalsIgnoreCase(query.token())) {
      return null;
    }
    return super.match(query);
  }

  @Override
  public String sanitizeForLog(Query query) {
    return ProtectedLog.redactLastField(query.token(), query.question());
  }

  @Override
  public String solve(Matcher m) {
    int typeId = Integer.parseInt(m.group(1));
    int locationId = Integer.parseInt(m.group(2));
    int sourceId = Integer.parseInt(m.group(3));
    BigDecimal price = new BigDecimal(m.group(4));
    int quantity = Integer.parseInt(m.group(5));
    String listingId = m.group(6).trim();
    ingestTokens.requireValid(m.group(7));

    if (price.signum() <= 0 || quantity <= 0) {
      throw new IllegalArgumentException("price and quantity must be positive");
    }
    if (listingId.isEmpty()) {
      throw new IllegalArgumentException("externalListingId is required");
    }
    if (types.findById(typeId).isEmpty()) {
      throw new IllegalArgumentException("Unknown typeId");
    }
    if (!orders.sourceExists(sourceId)) {
      throw new IllegalArgumentException("Unknown sourceId");
    }

    MarketOrderRecord order =
        orders.upsertExternalSell(typeId, locationId, sourceId, price, quantity, listingId);
    int fills = marketMaker.match(typeId, locationId);
    logger.info(
        "placeSellOrder orderId={} sourceId={} listingId={} fills={}",
        order.orderId(),
        sourceId,
        listingId,
        fills);

    return toJson(order, fills);
  }

  private String toJson(MarketOrderRecord order, int fills) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orderId", order.orderId());
    body.put("typeId", order.typeId());
    body.put("locationId", order.locationId());
    body.put("sourceId", order.sourceId());
    body.put("externalListingId", order.externalListingId());
    body.put("price", order.price());
    body.put("quantity", order.quantity());
    body.put("quantityInitial", order.quantityInitial());
    body.put("status", order.status());
    body.put("fills", fills);
    body.put("lastUpdated", order.updatedAt().toString());
    body.put("createdAt", order.createdAt().toString());
    try {
      return jsonMapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize placeSellOrder response", e);
    }
  }
}

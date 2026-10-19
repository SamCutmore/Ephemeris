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
import xyz.negativeone.app.persistence.MarketOrderRecord;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.solver.Query;

/**
 * Update price/qty on an ingested external sell. q=editSellOrder:
 * sourceId;externalListingId;price;qty;ingestionToken
 */
public class EditSellOrderSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(EditSellOrderSolver.class);

  private final IngestTokenService ingestTokens;
  private final MarketOrderRepository orders;
  private final MarketMaker marketMaker;
  private final JsonMapper jsonMapper;

  public EditSellOrderSolver(
      IngestTokenService ingestTokens,
      MarketOrderRepository orders,
      MarketMaker marketMaker,
      JsonMapper jsonMapper) {
    super("^(\\d+);([^;]+);([0-9]+(?:\\.[0-9]+)?);(\\d+);([^;]+)$");
    this.ingestTokens = ingestTokens;
    this.orders = orders;
    this.marketMaker = marketMaker;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"editSellOrder".equalsIgnoreCase(query.token())) {
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
    int sourceId = Integer.parseInt(m.group(1));
    String listingId = m.group(2).trim();
    BigDecimal price = new BigDecimal(m.group(3));
    int quantity = Integer.parseInt(m.group(4));
    ingestTokens.requireValid(m.group(5));

    if (price.signum() <= 0 || quantity <= 0) {
      throw new IllegalArgumentException("price and quantity must be positive");
    }
    if (listingId.isEmpty()) {
      throw new IllegalArgumentException("externalListingId is required");
    }

    MarketOrderRecord existing =
        orders
            .findActiveExternalSell(sourceId, listingId)
            .orElseThrow(
                () -> new IllegalArgumentException("Sell order not found or not editable"));

    MarketOrderRecord order =
        orders.upsertExternalSell(
            existing.typeId(), existing.locationId(), sourceId, price, quantity, listingId);
    int fills = marketMaker.match(order.typeId(), order.locationId());
    logger.info(
        "editSellOrder orderId={} sourceId={} listingId={} fills={}",
        order.orderId(),
        sourceId,
        listingId,
        fills);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orderId", order.orderId());
    body.put("sourceId", order.sourceId());
    body.put("externalListingId", order.externalListingId());
    body.put("price", order.price());
    body.put("quantity", order.quantity());
    body.put("quantityInitial", order.quantityInitial());
    body.put("status", order.status());
    body.put("fills", fills);
    body.put("lastUpdated", order.updatedAt().toString());
    try {
      return jsonMapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize editSellOrder response", e);
    }
  }
}

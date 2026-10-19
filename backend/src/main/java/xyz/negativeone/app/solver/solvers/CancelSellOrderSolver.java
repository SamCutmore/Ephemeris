package xyz.negativeone.app.solver.solvers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.IngestTokenService;
import xyz.negativeone.app.persistence.MarketOrderRecord;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.solver.Query;

/**
 * Cancel an ingested external sell by marketplace listing id. q=cancelSellOrder:
 * sourceId;externalListingId;ingestionToken
 */
public class CancelSellOrderSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(CancelSellOrderSolver.class);

  private final IngestTokenService ingestTokens;
  private final MarketOrderRepository orders;
  private final JsonMapper jsonMapper;

  public CancelSellOrderSolver(
      IngestTokenService ingestTokens, MarketOrderRepository orders, JsonMapper jsonMapper) {
    super("^(\\d+);([^;]+);([^;]+)$");
    this.ingestTokens = ingestTokens;
    this.orders = orders;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"cancelSellOrder".equalsIgnoreCase(query.token())) {
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
    ingestTokens.requireValid(m.group(3));
    if (listingId.isEmpty()) {
      throw new IllegalArgumentException("externalListingId is required");
    }

    MarketOrderRecord order =
        orders
            .cancelExternalSell(sourceId, listingId)
            .orElseThrow(
                () -> new IllegalArgumentException("Sell order not found or not cancellable"));

    logger.info(
        "cancelSellOrder orderId={} sourceId={} listingId={}",
        order.orderId(),
        sourceId,
        listingId);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orderId", order.orderId());
    body.put("sourceId", order.sourceId());
    body.put("externalListingId", order.externalListingId());
    body.put("status", order.status());
    body.put("lastUpdated", order.updatedAt().toString());
    try {
      return jsonMapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize cancelSellOrder response", e);
    }
  }
}

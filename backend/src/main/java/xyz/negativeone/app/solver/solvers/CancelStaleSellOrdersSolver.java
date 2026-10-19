package xyz.negativeone.app.solver.solvers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.IngestTokenService;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.solver.Query;

/**
 * After an ingest pass, cancel external sells for a source not touched since {@code
 * updatedBeforeEpochMs}. q=cancelStaleSellOrders: sourceId;updatedBeforeEpochMs;ingestionToken
 */
public class CancelStaleSellOrdersSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(CancelStaleSellOrdersSolver.class);

  private final IngestTokenService ingestTokens;
  private final MarketOrderRepository orders;
  private final JsonMapper jsonMapper;

  public CancelStaleSellOrdersSolver(
      IngestTokenService ingestTokens, MarketOrderRepository orders, JsonMapper jsonMapper) {
    super("^(\\d+);(\\d+);([^;]+)$");
    this.ingestTokens = ingestTokens;
    this.orders = orders;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"cancelStaleSellOrders".equalsIgnoreCase(query.token())) {
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
    long epochMs = Long.parseLong(m.group(2));
    ingestTokens.requireValid(m.group(3));
    if (!orders.sourceExists(sourceId)) {
      throw new IllegalArgumentException("Unknown sourceId");
    }

    Instant updatedBefore = Instant.ofEpochMilli(epochMs);
    int cancelled = orders.cancelStaleExternalSells(sourceId, updatedBefore);
    logger.info(
        "cancelStaleSellOrders sourceId={} before={} cancelled={}",
        sourceId,
        updatedBefore,
        cancelled);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sourceId", sourceId);
    body.put("updatedBefore", updatedBefore.toString());
    body.put("cancelled", cancelled);
    try {
      return jsonMapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize cancelStaleSellOrders response", e);
    }
  }
}

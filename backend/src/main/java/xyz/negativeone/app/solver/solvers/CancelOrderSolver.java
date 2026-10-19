package xyz.negativeone.app.solver.solvers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.solver.Query;

/** q=cancelOrder: orderId;token */
public class CancelOrderSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(CancelOrderSolver.class);

  private final SessionService sessions;
  private final MarketOrderRepository orders;
  private final JsonMapper jsonMapper;

  public CancelOrderSolver(
      SessionService sessions, MarketOrderRepository orders, JsonMapper jsonMapper) {
    super("^(\\d+);([A-Fa-f0-9]+)$");
    this.sessions = sessions;
    this.orders = orders;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"cancelOrder".equalsIgnoreCase(query.token())) {
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
    long orderId = Long.parseLong(m.group(1));
    String token = m.group(2);

    int userId =
        sessions
            .resolveUserId(token)
            .orElseThrow(() -> new IllegalArgumentException("Unauthorized"));

    if (!orders.cancelOwned(orderId, userId)) {
      throw new IllegalArgumentException("Order not found or not cancellable");
    }

    logger.info("cancelOrder orderId={} userId={}", orderId, userId);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orderId", orderId);
    body.put("status", "cancelled");
    try {
      return jsonMapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize cancelOrder response", e);
    }
  }
}

package xyz.negativeone.app.solver.solvers;

import java.util.List;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.persistence.MarketOrderRecord;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.solver.Query;

/** q=listMyOrders: &lt;token&gt; */
public class ListMyOrdersSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(ListMyOrdersSolver.class);

  private final SessionService sessions;
  private final MarketOrderRepository orders;
  private final JsonMapper jsonMapper;

  public ListMyOrdersSolver(
      SessionService sessions, MarketOrderRepository orders, JsonMapper jsonMapper) {
    super("^([A-Fa-f0-9]+)$");
    this.sessions = sessions;
    this.orders = orders;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"listMyOrders".equalsIgnoreCase(query.token())) return null;
    return super.match(query);
  }

  @Override
  public String sanitizeForLog(Query query) {
    return ProtectedLog.redactAll(query.token());
  }

  @Override
  public String solve(Matcher m) {
    int userId =
        sessions
            .resolveUserId(m.group(1))
            .orElseThrow(() -> new IllegalArgumentException("Unauthorized"));
    List<MarketOrderRecord> rows = orders.findOpenByUserId(userId);
    logger.info("listMyOrders userId={} → {} row(s)", userId, rows.size());
    try {
      return jsonMapper.writeValueAsString(rows);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize orders", e);
    }
  }
}

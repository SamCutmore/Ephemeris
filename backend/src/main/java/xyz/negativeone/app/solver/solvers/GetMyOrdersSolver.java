package xyz.negativeone.app.solver.solvers;

import java.util.regex.Matcher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.solver.Query;

public class GetMyOrdersSolver extends RegexSolver implements ProtectedSolver {
  private final SessionService sessions;
  private final MarketOrderRepository orders;
  private final JsonMapper jsonMapper;

  public GetMyOrdersSolver(
      SessionService sessions, MarketOrderRepository orders, JsonMapper jsonMapper) {
    super("^([A-Fa-f0-9]{64})$");
    this.sessions = sessions;
    this.orders = orders;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"getMyOrders".equalsIgnoreCase(query.token())) {
      return null;
    }
    return super.match(query);
  }

  @Override
  public String sanitizeForLog(Query query) {
    return ProtectedLog.redactAll(query.token());
  }

  @Override
  public String solve(Matcher matcher) {
    int userId =
        sessions
            .resolveUserId(matcher.group(1))
            .orElseThrow(() -> new IllegalArgumentException("Unauthorized"));

    try {
      return jsonMapper.writeValueAsString(orders.findUserBuyOrders(userId));
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize orders", e);
    }
  }
}

package xyz.negativeone.app.solver.solvers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.market.MarketMaker;
import xyz.negativeone.app.persistence.MarketOrderRecord;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.solver.Query;

/**
 * q=editOrder: orderId;price;quantity;token quantity is the new remaining size (filled amount is
 * preserved).
 */
public class EditOrderSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(EditOrderSolver.class);

  private final SessionService sessions;
  private final MarketOrderRepository orders;
  private final MarketMaker marketMaker;
  private final JsonMapper jsonMapper;

  public EditOrderSolver(
      SessionService sessions,
      MarketOrderRepository orders,
      MarketMaker marketMaker,
      JsonMapper jsonMapper) {
    super("^(\\d+);([0-9]+(?:\\.[0-9]+)?);(\\d+);([A-Fa-f0-9]+)$");
    this.sessions = sessions;
    this.orders = orders;
    this.marketMaker = marketMaker;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"editOrder".equalsIgnoreCase(query.token())) {
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
    BigDecimal price = new BigDecimal(m.group(2));
    int quantity = Integer.parseInt(m.group(3));
    String token = m.group(4);

    int userId =
        sessions
            .resolveUserId(token)
            .orElseThrow(() -> new IllegalArgumentException("Unauthorized"));

    if (price.signum() <= 0 || quantity <= 0) {
      throw new IllegalArgumentException("price and quantity must be positive");
    }

    MarketOrderRecord updated =
        orders
            .editOwned(orderId, userId, price, quantity)
            .orElseThrow(() -> new IllegalArgumentException("Order not found or not editable"));

    int fills = marketMaker.match(updated.typeId(), updated.locationId());
    logger.info("editOrder orderId={} userId={} fills={}", orderId, userId, fills);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orderId", orderId);
    body.put("price", updated.price());
    body.put("quantity", updated.quantity());
    body.put("quantityInitial", updated.quantityInitial());
    body.put("status", updated.status());
    body.put("fills", fills);
    try {
      return jsonMapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize editOrder response", e);
    }
  }
}

package xyz.negativeone.app.solver.solvers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.market.MarketMaker;
import xyz.negativeone.app.persistence.InvTypeRepository;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.solver.Query;

/** q=placeBuyOrder: typeId;locationId;price;qty;token */
public class PlaceBuyOrderSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(PlaceBuyOrderSolver.class);
  private static final Duration ORDER_TTL = Duration.ofDays(7);

  private final SessionService sessions;
  private final InvTypeRepository types;
  private final MarketOrderRepository orders;
  private final MarketMaker marketMaker;
  private final JsonMapper jsonMapper;

  public PlaceBuyOrderSolver(
      SessionService sessions,
      InvTypeRepository types,
      MarketOrderRepository orders,
      MarketMaker marketMaker,
      JsonMapper jsonMapper) {
    super("^(\\d+);(\\d+);([0-9]+(?:\\.[0-9]+)?);(\\d+);([A-Fa-f0-9]+)$");
    this.sessions = sessions;
    this.types = types;
    this.orders = orders;
    this.marketMaker = marketMaker;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"placeBuyOrder".equalsIgnoreCase(query.token())) return null;
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
    BigDecimal price = new BigDecimal(m.group(3));
    int quantity = Integer.parseInt(m.group(4));
    String token = m.group(5);

    int userId =
        sessions
            .resolveUserId(token)
            .orElseThrow(() -> new IllegalArgumentException("Unauthorized"));

    if (price.signum() <= 0 || quantity <= 0) {
      throw new IllegalArgumentException("price and quantity must be positive");
    }
    if (types.findById(typeId).isEmpty()) {
      throw new IllegalArgumentException("Unknown typeId");
    }

    Instant expiresAt = Instant.now().plus(ORDER_TTL);
    long orderId = orders.insertBuy(typeId, locationId, price, quantity, userId, expiresAt);
    int fills = marketMaker.match(typeId, locationId);
    logger.info("placeBuyOrder orderId={} userId={} fills={}", orderId, userId, fills);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orderId", orderId);
    body.put("userId", userId);
    body.put("expiresAt", expiresAt.toString());
    body.put("fills", fills);
    try {
      return jsonMapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize placeBuyOrder response", e);
    }
  }
}

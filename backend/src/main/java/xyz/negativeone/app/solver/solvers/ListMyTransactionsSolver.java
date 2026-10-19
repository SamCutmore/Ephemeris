package xyz.negativeone.app.solver.solvers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.persistence.MarketFillRecord;
import xyz.negativeone.app.persistence.MarketFillRepository;
import xyz.negativeone.app.persistence.MarketOrderRecord;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.persistence.MyTransactionRecord;
import xyz.negativeone.app.solver.Query;

/** q=listMyTransactions: &lt;token&gt; Returns order history */
public class ListMyTransactionsSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(ListMyTransactionsSolver.class);

  private final SessionService sessions;
  private final MarketOrderRepository orders;
  private final MarketFillRepository fills;
  private final JsonMapper jsonMapper;

  public ListMyTransactionsSolver(
      SessionService sessions,
      MarketOrderRepository orders,
      MarketFillRepository fills,
      JsonMapper jsonMapper) {
    super("^([A-Fa-f0-9]+)$");
    this.sessions = sessions;
    this.orders = orders;
    this.fills = fills;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"listMyTransactions".equalsIgnoreCase(query.token())) return null;
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

    List<MarketOrderRecord> historical = orders.findHistoricalByUserId(userId);
    Map<Long, List<MarketFillRecord>> fillsByBuy =
        fills.findByUserBuyOrders(userId).stream()
            .collect(
                Collectors.groupingBy(
                    MarketFillRecord::buyOrderId, LinkedHashMap::new, Collectors.toList()));

    List<MyTransactionRecord> rows = new ArrayList<>(historical.size());
    for (MarketOrderRecord order : historical) {
      rows.add(new MyTransactionRecord(order, fillsByBuy.getOrDefault(order.orderId(), List.of())));
    }

    logger.info("listMyTransactions userId={} → {} row(s)", userId, rows.size());
    try {
      return jsonMapper.writeValueAsString(rows);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize transactions", e);
    }
  }
}

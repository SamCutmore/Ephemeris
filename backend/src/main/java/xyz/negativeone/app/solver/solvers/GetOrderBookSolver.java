package xyz.negativeone.app.solver.solvers;

import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.persistence.OrderBookRecord;
import xyz.negativeone.app.solver.Query;

/** q=getOrderBook: &lt;typeId&gt;;&lt;locationId&gt; */
public class GetOrderBookSolver extends RegexSolver {
  private static final Logger logger = LoggerFactory.getLogger(GetOrderBookSolver.class);

  private final MarketOrderRepository orders;
  private final JsonMapper jsonMapper;

  public GetOrderBookSolver(MarketOrderRepository orders, JsonMapper jsonMapper) {
    super("^(\\d+);(\\d+)$");
    this.orders = orders;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"getOrderBook".equalsIgnoreCase(query.token())) return null;
    return super.match(query);
  }

  @Override
  public String solve(Matcher m) {
    int typeId = Integer.parseInt(m.group(1));
    int locationId = Integer.parseInt(m.group(2));
    OrderBookRecord book = orders.findOpenBook(typeId, locationId);
    logger.info(
        "getOrderBook typeId={} locationId={} → {} buy(s), {} sell(s)",
        typeId,
        locationId,
        book.buys().size(),
        book.sells().size());
    try {
      return jsonMapper.writeValueAsString(book);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize order book", e);
    }
  }
}

package xyz.negativeone.app.solver.solvers;

import java.util.List;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.persistence.MarketHistoryRecord;
import xyz.negativeone.app.persistence.MarketHistoryRepository;
import xyz.negativeone.app.solver.Query;

/** q=getHistory: &lt;typeId&gt;;&lt;locationId&gt; */
public class GetHistorySolver extends RegexSolver {
  private static final Logger logger = LoggerFactory.getLogger(GetHistorySolver.class);

  private final MarketHistoryRepository history;
  private final JsonMapper jsonMapper;

  public GetHistorySolver(MarketHistoryRepository history, JsonMapper jsonMapper) {
    super("^(\\d+);(\\d+)$");
    this.history = history;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"getHistory".equalsIgnoreCase(query.token())) return null;
    return super.match(query);
  }

  @Override
  public String solve(Matcher m) {
    int typeId = Integer.parseInt(m.group(1));
    int locationId = Integer.parseInt(m.group(2));
    List<MarketHistoryRecord> rows = history.findByTypeAndLocation(typeId, locationId);
    logger.info("getHistory typeId={} locationId={} → {} row(s)", typeId, locationId, rows.size());
    try {
      return jsonMapper.writeValueAsString(rows);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize market history", e);
    }
  }
}

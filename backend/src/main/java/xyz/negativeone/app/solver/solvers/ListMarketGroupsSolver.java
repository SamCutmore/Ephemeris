package xyz.negativeone.app.solver.solvers;

import java.util.List;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.persistence.MarketGroupRecord;
import xyz.negativeone.app.persistence.MarketGroupRepository;
import xyz.negativeone.app.solver.Query;

/** q=listMarketGroups: root or q=listMarketGroups: &lt;parentId&gt; */
public class ListMarketGroupsSolver extends RegexSolver {
  private static final Logger logger = LoggerFactory.getLogger(ListMarketGroupsSolver.class);

  private final MarketGroupRepository groups;
  private final JsonMapper jsonMapper;

  public ListMarketGroupsSolver(MarketGroupRepository groups, JsonMapper jsonMapper) {
    super("^(?i)root$|^(\\d+)$");
    this.groups = groups;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"listMarketGroups".equalsIgnoreCase(query.token())) return null;
    return super.match(query);
  }

  @Override
  public String solve(Matcher m) {
    List<MarketGroupRecord> rows;
    if (m.group(1) == null) {
      rows = groups.findRoots();
    } else {
      rows = groups.findByParent(Integer.parseInt(m.group(1)));
    }
    logger.info("listMarketGroups → {} row(s)", rows.size());
    try {
      return jsonMapper.writeValueAsString(rows);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize market groups", e);
    }
  }
}

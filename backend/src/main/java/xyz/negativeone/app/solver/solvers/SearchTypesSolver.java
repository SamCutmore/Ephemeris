package xyz.negativeone.app.solver.solvers;

import java.util.List;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.persistence.InvTypeRecord;
import xyz.negativeone.app.persistence.InvTypeRepository;
import xyz.negativeone.app.solver.Query;

/** Catalogue search across published types. */
public class SearchTypesSolver extends RegexSolver {
  private static final Logger logger = LoggerFactory.getLogger(SearchTypesSolver.class);

  private static final int MIN_QUERY_LEN = 2;
  private static final int MAX_QUERY_LEN = 64;

  private final InvTypeRepository types;
  private final JsonMapper jsonMapper;

  public SearchTypesSolver(InvTypeRepository types, JsonMapper jsonMapper) {
    super("(?s)^(.+)$");
    this.types = types;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"searchTypes".equalsIgnoreCase(query.token())) {
      return null;
    }
    return super.match(query);
  }

  @Override
  public String solve(Matcher m) {
    String q = m.group(1).trim();
    if (q.length() < MIN_QUERY_LEN) {
      throw new IllegalArgumentException("query must be at least " + MIN_QUERY_LEN + " characters");
    }
    if (q.length() > MAX_QUERY_LEN) {
      throw new IllegalArgumentException("query must be at most " + MAX_QUERY_LEN + " characters");
    }
    List<InvTypeRecord> rows = types.search(q);
    logger.info("searchTypes q={} → {} row(s)", q, rows.size());
    try {
      return jsonMapper.writeValueAsString(rows);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize types", e);
    }
  }
}

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

/** q=listTypes: &lt;groupId&gt; */
public class ListTypesSolver extends RegexSolver {
  private static final Logger logger = LoggerFactory.getLogger(ListTypesSolver.class);

  private final InvTypeRepository types;
  private final JsonMapper jsonMapper;

  public ListTypesSolver(InvTypeRepository types, JsonMapper jsonMapper) {
    super("^(\\d+)$");
    this.types = types;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"listTypes".equalsIgnoreCase(query.token())) return null;
    return super.match(query);
  }

  @Override
  public String solve(Matcher m) {
    int groupId = Integer.parseInt(m.group(1));
    List<InvTypeRecord> rows = types.findByMarketGroup(groupId);
    logger.info("listTypes groupId={} → {} row(s)", groupId, rows.size());
    try {
      return jsonMapper.writeValueAsString(rows);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize types", e);
    }
  }
}

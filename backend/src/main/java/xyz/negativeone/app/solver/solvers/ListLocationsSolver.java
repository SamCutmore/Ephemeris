package xyz.negativeone.app.solver.solvers;

import java.util.List;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.persistence.LocationRecord;
import xyz.negativeone.app.persistence.LocationRepository;
import xyz.negativeone.app.solver.Query;

/** q=listLocations: root or q=listLocations: &lt;parentId&gt; */
public class ListLocationsSolver extends RegexSolver {
  private static final Logger logger = LoggerFactory.getLogger(ListLocationsSolver.class);

  private final LocationRepository locations;
  private final JsonMapper jsonMapper;

  public ListLocationsSolver(LocationRepository locations, JsonMapper jsonMapper) {
    super("^(?i)root$|^(\\d+)$");
    this.locations = locations;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"listLocations".equalsIgnoreCase(query.token())) {
      return null;
    }
    return super.match(query);
  }

  @Override
  public String solve(Matcher m) {
    List<LocationRecord> rows;
    if (m.group(1) == null) {
      rows = locations.findRoots();
    } else {
      rows = locations.findByParent(Integer.parseInt(m.group(1)));
    }
    logger.info("listLocations → {} row(s)", rows.size());
    try {
      return jsonMapper.writeValueAsString(rows);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize locations", e);
    }
  }
}

package xyz.negativeone.app.solver.solvers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.persistence.MarketPriceRecord;
import xyz.negativeone.app.persistence.MarketPriceRepository;
import xyz.negativeone.app.solver.Query;

/**
 * Cached median ask and lowest ask for a type at a location. q=getMarketPrice: typeId;locationId
 */
public class GetMarketPriceSolver extends RegexSolver {
  private static final Logger logger = LoggerFactory.getLogger(GetMarketPriceSolver.class);

  private final MarketPriceRepository prices;
  private final JsonMapper jsonMapper;

  public GetMarketPriceSolver(MarketPriceRepository prices, JsonMapper jsonMapper) {
    super("^(\\d+);(\\d+)$");
    this.prices = prices;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"getMarketPrice".equalsIgnoreCase(query.token())) {
      return null;
    }
    return super.match(query);
  }

  @Override
  public String solve(Matcher m) {
    int typeId = Integer.parseInt(m.group(1));
    int locationId = Integer.parseInt(m.group(2));
    MarketPriceRecord row =
        prices
            .find(typeId, locationId)
            .orElse(new MarketPriceRecord(typeId, locationId, null, null, 0));
    logger.info(
        "getMarketPrice typeId={} locationId={} sampleSize={}",
        typeId,
        locationId,
        row.sampleSize());
    return toJson(row);
  }

  private String toJson(MarketPriceRecord row) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("typeId", row.typeId());
    body.put("locationId", row.locationId());
    body.put("medianPrice", row.medianPrice());
    body.put("lowestPrice", row.lowestPrice());
    body.put("sampleSize", row.sampleSize());
    try {
      return jsonMapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize market price", e);
    }
  }
}

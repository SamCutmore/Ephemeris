package xyz.negativeone.app.solver.solvers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.persistence.InvTypeRepository;
import xyz.negativeone.app.persistence.MarketPriceRecord;
import xyz.negativeone.app.persistence.MarketPriceRepository;
import xyz.negativeone.app.solver.Query;

/**
 * Recompute median + lowest ask from current open sells and store in market_prices.
 * q=refreshMarketPrice: typeId;locationId
 */
public class RefreshMarketPriceSolver extends RegexSolver {
  private static final Logger logger = LoggerFactory.getLogger(RefreshMarketPriceSolver.class);

  private final InvTypeRepository types;
  private final MarketPriceRepository prices;
  private final JsonMapper jsonMapper;

  public RefreshMarketPriceSolver(
      InvTypeRepository types, MarketPriceRepository prices, JsonMapper jsonMapper) {
    super("^(\\d+);(\\d+)$");
    this.types = types;
    this.prices = prices;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"refreshMarketPrice".equalsIgnoreCase(query.token())) {
      return null;
    }
    return super.match(query);
  }

  @Override
  public String solve(Matcher m) {
    int typeId = Integer.parseInt(m.group(1));
    int locationId = Integer.parseInt(m.group(2));
    if (types.findById(typeId).isEmpty()) {
      throw new IllegalArgumentException("Unknown typeId");
    }

    MarketPriceRecord row = prices.refreshFromOpenSells(typeId, locationId);
    logger.info(
        "refreshMarketPrice typeId={} locationId={} sampleSize={} median={} lowest={}",
        typeId,
        locationId,
        row.sampleSize(),
        row.medianPrice(),
        row.lowestPrice());

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

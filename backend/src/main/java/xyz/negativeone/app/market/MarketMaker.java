package xyz.negativeone.app.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.negativeone.app.persistence.MarketFillRepository;
import xyz.negativeone.app.persistence.MarketOrderRecord;
import xyz.negativeone.app.persistence.MarketOrderRepository;

/** Highest buy vs lowest sell; trade at the ask while buy ≥ sell. */
@Component
public class MarketMaker {
  private static final Logger logger = LoggerFactory.getLogger(MarketMaker.class);

  private final MarketOrderRepository orders;
  private final MarketFillRepository fills;

  public MarketMaker(MarketOrderRepository orders, MarketFillRepository fills) {
    this.orders = orders;
    this.fills = fills;
  }

  @Transactional
  public int match(int typeId, int locationId) {
    orders.cancelExpired(Instant.now());
    int fillCount = 0;
    while (true) {
      List<MarketOrderRecord> buys = orders.findOpenBuys(typeId, locationId);
      List<MarketOrderRecord> sells = orders.findOpenSells(typeId, locationId);
      if (buys.isEmpty() || sells.isEmpty()) {
        break;
      }
      MarketOrderRecord buy = buys.get(0);
      MarketOrderRecord sell = sells.get(0);
      if (buy.price().compareTo(sell.price()) < 0) {
        break;
      }
      int qty = Math.min(buy.quantity(), sell.quantity());
      BigDecimal price = sell.price();
      fills.insert(typeId, locationId, buy.orderId(), sell.orderId(), price, qty);
      applyFill(buy, qty);
      applyFill(sell, qty);
      fillCount++;
      logger.info(
          "Matched typeId={} locationId={} qty={} @ {} (buy={} sell={})",
          typeId,
          locationId,
          qty,
          price,
          buy.orderId(),
          sell.orderId());
    }
    return fillCount;
  }

  private void applyFill(MarketOrderRecord order, int qty) {
    int remaining = order.quantity() - qty;
    String status = remaining == 0 ? "filled" : "partial";
    orders.updateRemaining(order.orderId(), remaining, status);
  }
}

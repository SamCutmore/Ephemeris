package xyz.negativeone.app.persistence;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketFillRecord(
    long fillId,
    int typeId,
    int locationId,
    long buyOrderId,
    long sellOrderId,
    BigDecimal price,
    int quantity,
    Instant filledAt) {}

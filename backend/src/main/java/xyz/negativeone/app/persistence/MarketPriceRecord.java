package xyz.negativeone.app.persistence;

import java.math.BigDecimal;

/** Cached reference prices for a type at a location. */
public record MarketPriceRecord(
    int typeId, int locationId, BigDecimal medianPrice, BigDecimal lowestPrice, int sampleSize) {}

package xyz.negativeone.app.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketHistoryRecord(
    int locationId,
    int typeId,
    LocalDate date,
    BigDecimal lowPrice,
    BigDecimal highPrice,
    BigDecimal avgPrice,
    long volume,
    long orders) {}

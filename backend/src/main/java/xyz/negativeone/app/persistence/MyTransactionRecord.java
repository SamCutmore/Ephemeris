package xyz.negativeone.app.persistence;

import java.util.List;

/** Closed orders */
public record MyTransactionRecord(MarketOrderRecord order, List<MarketFillRecord> fills) {}

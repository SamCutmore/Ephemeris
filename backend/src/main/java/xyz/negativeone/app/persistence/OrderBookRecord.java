package xyz.negativeone.app.persistence;

import java.util.List;

public record OrderBookRecord(
    int typeId, int locationId, List<MarketOrderRecord> buys, List<MarketOrderRecord> sells) {}

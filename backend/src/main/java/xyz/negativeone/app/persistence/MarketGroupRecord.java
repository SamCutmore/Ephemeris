package xyz.negativeone.app.persistence;

public record MarketGroupRecord(
    int marketGroupId,
    Integer parentGroupId,
    String marketGroupName,
    String description,
    boolean hasTypes) {}

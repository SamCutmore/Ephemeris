package xyz.negativeone.app.persistence;

import java.math.BigDecimal;

public record InvTypeRecord(
    int typeId,
    String typeName,
    String description,
    BigDecimal basePrice,
    boolean published,
    Integer marketGroupId) {}

package xyz.negativeone.app.persistence;

import java.math.BigDecimal;

public record MyOrderRecord(
    long orderId,
    int typeId,
    String typeName,
    BigDecimal price,
    int quantity,
    int quantityInitial,
    String status,
    String createdAt) {}

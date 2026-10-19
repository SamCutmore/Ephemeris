package xyz.negativeone.app.persistence;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketOrderRecord(
    long orderId,
    int typeId,
    int locationId,
    int sourceId,
    String side,
    BigDecimal price,
    int quantity,
    int quantityInitial,
    String status,
    String externalUrl,
    String externalListingId,
    String ownerKind,
    String ownerRef,
    Integer userId,
    String username,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt) {}

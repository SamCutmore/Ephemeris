package xyz.negativeone.app.fixtures;

import java.math.BigDecimal;

/** Normalised sell listing produced by a source-specific normaliser. */
public record ExternalListing(
    int sourceId,
    int typeId,
    int locationId,
    BigDecimal price,
    int quantity,
    String externalListingId,
    String externalUrl) {}

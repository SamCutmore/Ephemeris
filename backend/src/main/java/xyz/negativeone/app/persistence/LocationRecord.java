package xyz.negativeone.app.persistence;

public record LocationRecord(
    int locationId, Integer parentLocationId, String locationName, String description) {}

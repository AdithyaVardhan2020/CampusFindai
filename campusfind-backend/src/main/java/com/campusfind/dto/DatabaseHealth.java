package com.campusfind.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Diagnostic health information for the persistence database layer.
 * Contains sanitized metadata only; never exposes passwords, hostnames,
 * or secret connection strings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatabaseHealth(
    String status,
    String databaseProductName,
    String databaseProductVersion,
    String driverName,
    boolean isAivenCloud,
    String message
) {
    public static DatabaseHealth up(String productName, String productVersion, String driverName, boolean isAiven) {
        return new DatabaseHealth("UP", productName, productVersion, driverName, isAiven, null);
    }

    public static DatabaseHealth down(String errorMessage) {
        return new DatabaseHealth("DOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", false, errorMessage);
    }
}

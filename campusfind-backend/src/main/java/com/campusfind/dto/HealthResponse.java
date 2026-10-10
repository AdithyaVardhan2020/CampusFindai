package com.campusfind.dto;

import java.time.Instant;

/**
 * Top-level health check response for the CampusFind Spring Boot backend.
 */
public record HealthResponse(
    String status,
    String service,
    Instant timestamp,
    DatabaseHealth database
) {
    public static HealthResponse up(DatabaseHealth dbHealth) {
        return new HealthResponse("UP", "campusfind-backend", Instant.now(), dbHealth);
    }

    public static HealthResponse down(DatabaseHealth dbHealth) {
        return new HealthResponse("DOWN", "campusfind-backend", Instant.now(), dbHealth);
    }
}

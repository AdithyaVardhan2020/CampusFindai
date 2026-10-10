package com.campusfind.controller;

import com.campusfind.dto.HealthResponse;
import com.campusfind.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check REST controller exposing application and persistence layer status.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * GET /api/v1/health
     * Returns HTTP 200 with service and database metadata if healthy,
     * or HTTP 503 Service Unavailable if database connectivity fails.
     */
    @GetMapping
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse health = healthService.checkHealth();

        if ("UP".equalsIgnoreCase(health.status())) {
            return ResponseEntity.ok(health);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }
}

package com.campusfind.controller;

import com.campusfind.dto.CreateReportRequest;
import com.campusfind.dto.ReportResponse;
import com.campusfind.dto.UpdateReportStatusRequest;
import com.campusfind.entity.ReportStatus;
import com.campusfind.entity.ReportType;
import com.campusfind.dto.AiHealthResponse;
import com.campusfind.dto.ReportMatchResponse;
import com.campusfind.exception.BadRequestException;
import com.campusfind.service.AiServiceClient;
import com.campusfind.service.ItemReportService;
import com.campusfind.service.ReportMatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ItemReportController {

    private final ItemReportService reportService;
    private final ReportMatchingService matchingService;
    private final AiServiceClient aiServiceClient;

    public ItemReportController(
            ItemReportService reportService,
            ReportMatchingService matchingService,
            AiServiceClient aiServiceClient
    ) {
        this.reportService = reportService;
        this.matchingService = matchingService;
        this.aiServiceClient = aiServiceClient;
    }

    /**
     * POST /api/v1/reports
     * Create a new lost or found item report (requires authenticated student or admin).
     */
    @PostMapping
    public ResponseEntity<ReportResponse> createReport(
            @Valid @RequestBody CreateReportRequest request,
            Principal principal
    ) {
        ReportResponse response = reportService.createReport(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/reports
     * List all item reports, optionally filtered by type (LOST/FOUND) and/or status (OPEN/RESOLVED/CANCELLED).
     */
    @GetMapping
    public ResponseEntity<List<ReportResponse>> getReports(
            @RequestParam(required = false) ReportType type,
            @RequestParam(required = false) ReportStatus status
    ) {
        List<ReportResponse> reports = reportService.getReports(type, status);
        return ResponseEntity.ok(reports);
    }

    /**
     * GET /api/v1/reports/{id}
     * Get details of a single report by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getReportById(@PathVariable Long id) {
        ReportResponse report = reportService.getReportById(id);
        return ResponseEntity.ok(report);
    }

    /**
     * PATCH /api/v1/reports/{id}/status
     * Update report lifecycle status (allowed for author or admin).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ReportResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReportStatusRequest request,
            Principal principal
    ) {
        ReportResponse response = reportService.updateReportStatus(id, request, principal.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/reports/{id}
     * Delete report (allowed for author or admin).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id, Principal principal) {
        reportService.deleteReport(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/reports/ai-health
     * Check diagnostic health and availability of the OpenCLIP AI service.
     */
    @GetMapping("/ai-health")
    public ResponseEntity<AiHealthResponse> getAiHealth() {
        AiHealthResponse health = aiServiceClient.checkHealth();
        return ResponseEntity.ok(health);
    }

    /**
     * GET /api/v1/reports/{id}/matches
     * Finds and ranks candidate matches for the specified report using OpenCLIP visual embeddings.
     */
    @GetMapping("/{id}/matches")
    public ResponseEntity<List<ReportMatchResponse>> getReportMatches(@PathVariable Long id) {
        List<ReportMatchResponse> matches = matchingService.findMatchesForReport(id);
        return ResponseEntity.ok(matches);
    }

    /**
     * POST /api/v1/reports/{id}/embedding
     * Generates or attaches visual embedding vector to a report.
     * Accepts JSON: {"image_base64": "..."} or {"embedding": [...]}
     */
    @PostMapping("/{id}/embedding")
    public ResponseEntity<ReportResponse> attachEmbedding(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        if (payload.containsKey("image_base64")) {
            String base64 = (String) payload.get("image_base64");
            ReportResponse response = matchingService.generateAndAttachEmbedding(id, base64);
            return ResponseEntity.ok(response);
        } else if (payload.containsKey("embedding")) {
            @SuppressWarnings("unchecked")
            List<Double> embedding = (List<Double>) payload.get("embedding");
            ReportResponse response = matchingService.attachEmbedding(id, embedding);
            return ResponseEntity.ok(response);
        } else {
            throw new BadRequestException("Request body must contain 'image_base64' or 'embedding'.");
        }
    }
}

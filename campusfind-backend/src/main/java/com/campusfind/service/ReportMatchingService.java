package com.campusfind.service;

import com.campusfind.dto.AiCandidateDto;
import com.campusfind.dto.AiMatchItemDto;
import com.campusfind.dto.AiRankResponse;
import com.campusfind.dto.ReportMatchResponse;
import com.campusfind.dto.ReportResponse;
import com.campusfind.entity.ItemReport;
import com.campusfind.entity.ReportStatus;
import com.campusfind.entity.ReportType;
import com.campusfind.exception.BadRequestException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.repository.ItemReportRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ReportMatchingService {

    private static final Logger log = LoggerFactory.getLogger(ReportMatchingService.class);

    private static final Set<String> BROAD_CATEGORIES = Set.of("electronics", "other", "general");

    private final ItemReportRepository reportRepository;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${campusfind.ai.similarity-threshold:0.60}")
    private double similarityThreshold;

    @Value("${campusfind.ai.match-limit:6}")
    private int matchLimit;

    public ReportMatchingService(
            ItemReportRepository reportRepository,
            AiServiceClient aiServiceClient,
            ObjectMapper objectMapper
    ) {
        this.reportRepository = reportRepository;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds and ranks candidate matches for a given report using OpenCLIP visual embeddings.
     * Note: A high similarity score is an AI-suggested candidate match only, NOT definitive proof.
     * Reports are never automatically marked as RESOLVED.
     */
    @Transactional
    public List<ReportMatchResponse> findMatchesForReport(Long reportId) {
        ItemReport targetReport = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + reportId));

        // Self-heal: If report has an image URL but no embedding yet, generate and attach on-demand
        String embeddingJson = targetReport.getEmbeddingJson();
        if ((embeddingJson == null || embeddingJson.isBlank())
                && targetReport.getImageUrl() != null && !targetReport.getImageUrl().isBlank()) {
            log.info("Report {} has imageUrl but missing visual embedding; generating on-demand...", reportId);
            try {
                generateAndAttachEmbedding(targetReport.getId(), targetReport.getImageUrl());
                targetReport = reportRepository.findById(reportId).orElse(targetReport);
                embeddingJson = targetReport.getEmbeddingJson();
            } catch (Exception ex) {
                log.warn("On-demand embedding generation failed for target report {}: {}", reportId, ex.getMessage());
            }
        }

        if (embeddingJson == null || embeddingJson.isBlank()) {
            log.info("Report {} has no visual embedding; returning empty matches.", reportId);
            return Collections.emptyList();
        }

        List<Double> targetEmbedding = parseEmbedding(embeddingJson);
        if (targetEmbedding == null || targetEmbedding.isEmpty()) {
            return Collections.emptyList();
        }

        // Compare LOST with FOUND, or FOUND with LOST
        ReportType candidateType = (targetReport.getType() == ReportType.LOST)
                ? ReportType.FOUND
                : ReportType.LOST;

        // Retrieve active OPEN candidate reports of the opposite type
        List<ItemReport> candidates = reportRepository.findByTypeAndStatus(
                candidateType,
                ReportStatus.OPEN
        );

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<AiCandidateDto> candidateDtos = new ArrayList<>();
        Map<Long, ItemReport> candidateMap = new HashMap<>();

        for (ItemReport candidate : candidates) {
            // Exclude the report itself if by any chance same ID exists
            if (candidate.getId().equals(targetReport.getId())) {
                continue;
            }

            // Category compatibility filter: exclude incompatible specific categories (e.g. Phones vs Laptops)
            if (!isCategoryCompatible(targetReport.getCategory(), candidate.getCategory())) {
                continue;
            }

            // Self-heal candidate embedding if it has an image but embedding was not yet computed
            if ((candidate.getEmbeddingJson() == null || candidate.getEmbeddingJson().isBlank())
                    && candidate.getImageUrl() != null && !candidate.getImageUrl().isBlank()) {
                log.info("Candidate report {} has imageUrl but missing embedding; generating on-demand...", candidate.getId());
                try {
                    generateAndAttachEmbedding(candidate.getId(), candidate.getImageUrl());
                    candidate = reportRepository.findById(candidate.getId()).orElse(candidate);
                } catch (Exception ex) {
                    log.warn("On-demand embedding generation failed for candidate {}: {}", candidate.getId(), ex.getMessage());
                }
            }

            List<Double> candidateVec = parseEmbedding(candidate.getEmbeddingJson());
            if (candidateVec != null && !candidateVec.isEmpty()) {
                candidateDtos.add(new AiCandidateDto(candidate.getId(), candidateVec));
                candidateMap.put(candidate.getId(), candidate);
            }
        }

        if (candidateDtos.isEmpty()) {
            return Collections.emptyList();
        }

        AiRankResponse rankResponse = aiServiceClient.rankCandidates(
                targetEmbedding,
                candidateDtos,
                similarityThreshold,
                matchLimit
        );

        if (rankResponse == null || rankResponse.matches() == null || rankResponse.matches().isEmpty()) {
            return Collections.emptyList();
        }

        List<ReportMatchResponse> matches = new ArrayList<>();
        for (AiMatchItemDto matchItem : rankResponse.matches()) {
            ItemReport matchedEntity = candidateMap.get(matchItem.id());
            if (matchedEntity != null) {
                matches.add(ReportMatchResponse.of(
                        ReportResponse.fromEntity(matchedEntity),
                        matchItem.similarity(),
                        matchItem.percentage()
                ));
            }
        }

        return matches;
    }

    /**
     * Attaches an embedding vector to a report.
     */
    public ReportResponse attachEmbedding(Long reportId, List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new BadRequestException("Embedding cannot be empty.");
        }

        ItemReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + reportId));

        try {
            String json = objectMapper.writeValueAsString(embedding);
            report.setEmbeddingJson(json);
            ItemReport saved = reportRepository.save(report);
            return ReportResponse.fromEntity(saved);
        } catch (Exception ex) {
            throw new BadRequestException("Failed to serialize embedding: " + ex.getMessage());
        }
    }

    /**
     * Generates an OpenCLIP embedding from base64 image data and attaches it to the report.
     */
    public ReportResponse generateAndAttachEmbedding(Long reportId, String imageBase64) {
        List<Double> embedding = aiServiceClient.generateEmbeddingFromBase64(imageBase64);
        return attachEmbedding(reportId, embedding);
    }

    private List<Double> parseEmbedding(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse embedding JSON: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Determines whether two report categories are compatible for matching.
     * - Case-insensitive after trimming surrounding whitespace.
     * - Exact category matches are compatible (e.g. "Phones" vs "Phones").
     * - Broad categories ("Electronics", "Other", "General") are compatible with other categories.
     * - Distinct specific categories are incompatible (e.g. "Phones" vs "Laptops").
     * - Null or blank categories are safely treated as compatible (unclassified).
     */
    public boolean isCategoryCompatible(String targetCategory, String candidateCategory) {
        if (targetCategory == null || targetCategory.isBlank() ||
                candidateCategory == null || candidateCategory.isBlank()) {
            return true;
        }

        String target = targetCategory.trim().toLowerCase();
        String candidate = candidateCategory.trim().toLowerCase();

        if (target.equals(candidate)) {
            return true;
        }

        return BROAD_CATEGORIES.contains(target) || BROAD_CATEGORIES.contains(candidate);
    }
}

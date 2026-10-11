package com.campusfind.service;

import com.campusfind.dto.*;
import com.campusfind.exception.AiServiceUnavailableException;
import com.campusfind.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient aiRestClient;
    private final LocalOnnxEmbeddingService localOnnxService;

    @Autowired
    public AiServiceClient(
            @Qualifier("aiRestClient") RestClient aiRestClient,
            @Autowired(required = false) LocalOnnxEmbeddingService localOnnxService
    ) {
        this.aiRestClient = aiRestClient;
        this.localOnnxService = localOnnxService;
    }

    public AiServiceClient(@Qualifier("aiRestClient") RestClient aiRestClient) {
        this(aiRestClient, null);
    }

    /**
     * Checks health and readiness of the AI service (prioritizing in-process ONNX engine).
     */
    public AiHealthResponse checkHealth() {
        if (localOnnxService != null && localOnnxService.isAvailable()) {
            return localOnnxService.getHealth();
        }

        try {
            AiHealthResponse response = aiRestClient.get()
                    .uri("/api/v1/ai/health")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AiHealthResponse.class);

            if (response != null) {
                return response;
            }
        } catch (Exception ex) {
            log.warn("Remote AI service unreachable during health check: {}", ex.getMessage());
        }

        return new AiHealthResponse(
                "DOWN",
                "campusfind-ai",
                Map.of(
                        "available", false,
                        "error", "AI service is offline or unreachable"
                )
        );
    }

    /**
     * Requests a 512-dimensional embedding vector from base64 image data.
     */
    public List<Double> generateEmbeddingFromBase64(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new BadRequestException("Image base64 content cannot be empty.");
        }

        if (localOnnxService != null && localOnnxService.isAvailable()) {
            return localOnnxService.generateEmbeddingFromBase64(imageBase64);
        }

        String rawBase64 = imageBase64.trim();
        if (rawBase64.startsWith("data:") && rawBase64.contains(",")) {
            rawBase64 = rawBase64.substring(rawBase64.indexOf(',') + 1).trim();
        }

        try {
            Map<String, String> payload = Map.of("image_base64", rawBase64);
            AiEmbeddingResponse response = aiRestClient.post()
                    .uri("/api/v1/ai/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(AiEmbeddingResponse.class);

            if (response == null || response.embedding() == null || response.embedding().isEmpty()) {
                throw new AiServiceUnavailableException("AI service returned an empty or malformed embedding response.");
            }

            return response.embedding();
        } catch (ResourceAccessException ex) {
            log.error("AI service timed out or connection refused: {}", ex.getMessage());
            throw new AiServiceUnavailableException("AI service is currently unavailable or timed out.", ex);
        } catch (RestClientResponseException ex) {
            log.error("AI service embedding generation returned error status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is4xxClientError()) {
                throw new BadRequestException("Invalid image content submitted to AI service.");
            }
            throw new AiServiceUnavailableException("AI service failed to process image: " + ex.getMessage(), ex);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error contacting AI service: {}", ex.getMessage());
            throw new AiServiceUnavailableException("Failed to communicate with AI service: " + ex.getMessage(), ex);
        }
    }

    /**
     * Computes cosine similarity between two float vectors.
     */
    public AiSimilarityResponse calculateSimilarity(List<Double> embedding1, List<Double> embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.isEmpty() || embedding2.isEmpty()) {
            throw new BadRequestException("Both embeddings are required for similarity calculation.");
        }

        if (localOnnxService != null && localOnnxService.isAvailable()) {
            return localOnnxService.calculateSimilarity(embedding1, embedding2);
        }

        try {
            AiSimilarityRequest request = new AiSimilarityRequest(embedding1, embedding2);
            return aiRestClient.post()
                    .uri("/api/v1/ai/similarity")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiSimilarityResponse.class);
        } catch (ResourceAccessException ex) {
            log.error("AI service timed out or connection refused: {}", ex.getMessage());
            throw new AiServiceUnavailableException("AI service is currently unavailable or timed out.", ex);
        } catch (RestClientResponseException ex) {
            log.error("AI service similarity calculation returned error status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AiServiceUnavailableException("AI service failed to compute similarity: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Unexpected error contacting AI service: {}", ex.getMessage());
            throw new AiServiceUnavailableException("Failed to communicate with AI service: " + ex.getMessage(), ex);
        }
    }

    /**
     * Ranks a collection of candidate embeddings against a target embedding.
     */
    public AiRankResponse rankCandidates(List<Double> targetEmbedding, List<AiCandidateDto> candidates, double threshold, int limit) {
        if (targetEmbedding == null || targetEmbedding.isEmpty() || candidates == null || candidates.isEmpty()) {
            return new AiRankResponse(0, Collections.emptyList());
        }

        if (localOnnxService != null && localOnnxService.isAvailable()) {
            return localOnnxService.rankCandidates(targetEmbedding, candidates, threshold, limit);
        }

        try {
            AiRankRequest request = new AiRankRequest(targetEmbedding, candidates, threshold, limit);
            AiRankResponse response = aiRestClient.post()
                    .uri("/api/v1/ai/rank")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiRankResponse.class);

            return response != null ? response : new AiRankResponse(0, Collections.emptyList());
        } catch (ResourceAccessException ex) {
            log.error("AI service timed out or connection refused during candidate ranking: {}", ex.getMessage());
            throw new AiServiceUnavailableException("AI service is currently unavailable or timed out.", ex);
        } catch (RestClientResponseException ex) {
            log.error("AI service rank returned error status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AiServiceUnavailableException("AI service failed to rank candidates: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Unexpected error contacting AI service: {}", ex.getMessage());
            throw new AiServiceUnavailableException("Failed to communicate with AI service: " + ex.getMessage(), ex);
        }
    }
}

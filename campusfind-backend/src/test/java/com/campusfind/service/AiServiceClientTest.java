package com.campusfind.service;

import com.campusfind.dto.*;
import com.campusfind.exception.AiServiceUnavailableException;
import com.campusfind.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiServiceClientTest {

    private MockRestServiceServer mockServer;
    private AiServiceClient aiServiceClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:5000");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        aiServiceClient = new AiServiceClient(builder.build());
    }

    @Test
    @DisplayName("checkHealth() returns UP when Python AI service is available")
    void checkHealth_WhenServiceUp_ReturnsUp() {
        mockServer.expect(requestTo("http://localhost:5000/api/v1/ai/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"UP\",\"service\":\"campusfind-ai\",\"ai\":{\"available\":true,\"model_name\":\"ViT-B-32\"}}",
                        MediaType.APPLICATION_JSON
                ));

        AiHealthResponse response = aiServiceClient.checkHealth();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.service()).isEqualTo("campusfind-ai");
        assertThat(response.ai()).containsKey("available");
        mockServer.verify();
    }

    @Test
    @DisplayName("checkHealth() returns DOWN safely when service returns 503 or error")
    void checkHealth_WhenServiceDown_ReturnsDownGracefully() {
        mockServer.expect(requestTo("http://localhost:5000/api/v1/ai/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        AiHealthResponse response = aiServiceClient.checkHealth();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("DOWN");
        mockServer.verify();
    }

    @Test
    @DisplayName("generateEmbeddingFromBase64() generates embedding vector from valid base64 payload")
    void generateEmbeddingFromBase64_Success() {
        mockServer.expect(requestTo("http://localhost:5000/api/v1/ai/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(
                        "{\"dimension\":3,\"embedding\":[0.12,0.34,0.56]}",
                        MediaType.APPLICATION_JSON
                ));

        List<Double> embedding = aiServiceClient.generateEmbeddingFromBase64("dGVzdA==");

        assertThat(embedding).isNotNull();
        assertThat(embedding).containsExactly(0.12, 0.34, 0.56);
        mockServer.verify();
    }

    @Test
    @DisplayName("generateEmbeddingFromBase64() strips data URL prefix before calling AI service")
    void generateEmbeddingFromBase64_WithDataUrlPrefix_StripsHeader() {
        mockServer.expect(requestTo("http://localhost:5000/api/v1/ai/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"image_base64\":\"dGVzdA==\"}"))
                .andRespond(withSuccess(
                        "{\"dimension\":3,\"embedding\":[0.12,0.34,0.56]}",
                        MediaType.APPLICATION_JSON
                ));

        List<Double> embedding = aiServiceClient.generateEmbeddingFromBase64("data:image/jpeg;base64,dGVzdA==");

        assertThat(embedding).isNotNull();
        assertThat(embedding).containsExactly(0.12, 0.34, 0.56);
        mockServer.verify();
    }

    @Test
    @DisplayName("generateEmbeddingFromBase64() rejects empty or blank base64")
    void generateEmbeddingFromBase64_EmptyInput_ThrowsBadRequest() {
        assertThatThrownBy(() -> aiServiceClient.generateEmbeddingFromBase64(""))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Image base64 content cannot be empty");
    }

    @Test
    @DisplayName("calculateSimilarity() sends vectors and returns similarity score and percentage")
    void calculateSimilarity_Success() {
        mockServer.expect(requestTo("http://localhost:5000/api/v1/ai/similarity"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"similarity\":0.8523,\"percentage\":85.2}",
                        MediaType.APPLICATION_JSON
                ));

        AiSimilarityResponse response = aiServiceClient.calculateSimilarity(
                List.of(0.1, 0.2, 0.3),
                List.of(0.1, 0.2, 0.4)
        );

        assertThat(response).isNotNull();
        assertThat(response.similarity()).isEqualTo(0.8523);
        assertThat(response.percentage()).isEqualTo(85.2);
        mockServer.verify();
    }

    @Test
    @DisplayName("rankCandidates() returns ranked candidates with similarity scores")
    void rankCandidates_Success() {
        mockServer.expect(requestTo("http://localhost:5000/api/v1/ai/rank"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"count\":1,\"matches\":[{\"id\":42,\"similarity\":0.7891,\"percentage\":78.9}]}",
                        MediaType.APPLICATION_JSON
                ));

        List<Double> target = List.of(0.1, 0.2);
        List<AiCandidateDto> candidates = List.of(new AiCandidateDto(42L, List.of(0.1, 0.2)));

        AiRankResponse response = aiServiceClient.rankCandidates(target, candidates, 0.60, 6);

        assertThat(response).isNotNull();
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().get(0).id()).isEqualTo(42L);
        assertThat(response.matches().get(0).similarity()).isEqualTo(0.7891);
        mockServer.verify();
    }

    @Test
    @DisplayName("rankCandidates() safely throws AiServiceUnavailableException on server error")
    void rankCandidates_ServerError_ThrowsAiServiceUnavailable() {
        mockServer.expect(requestTo("http://localhost:5000/api/v1/ai/rank"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        List<Double> target = List.of(0.1, 0.2);
        List<AiCandidateDto> candidates = List.of(new AiCandidateDto(42L, List.of(0.1, 0.2)));

        assertThatThrownBy(() -> aiServiceClient.rankCandidates(target, candidates, 0.60, 6))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("AI service failed to rank candidates");
        mockServer.verify();
    }

    @Test
    @DisplayName("rankCandidates() returns empty response when candidates list is empty without HTTP call")
    void rankCandidates_EmptyCandidates_ReturnsEmptyWithoutHttpCall() {
        AiRankResponse response = aiServiceClient.rankCandidates(List.of(0.1, 0.2), List.of(), 0.60, 6);
        assertThat(response.count()).isZero();
        assertThat(response.matches()).isEmpty();
    }
}

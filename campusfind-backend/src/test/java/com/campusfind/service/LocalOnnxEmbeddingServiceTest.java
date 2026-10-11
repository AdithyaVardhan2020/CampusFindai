package com.campusfind.service;

import com.campusfind.dto.AiCandidateDto;
import com.campusfind.dto.AiHealthResponse;
import com.campusfind.dto.AiMatchItemDto;
import com.campusfind.dto.AiRankResponse;
import com.campusfind.exception.BadRequestException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalOnnxEmbeddingServiceTest {

    private static LocalOnnxEmbeddingService onnxService;
    private static String validSampleBase64Png;
    private static String validSampleBase64Jpeg;

    @BeforeAll
    static void setUp() throws Exception {
        // Find existing model file
        Path modelPath = Paths.get("models/openclip_vit_b32_int8.onnx");
        if (!Files.exists(modelPath)) {
            modelPath = Paths.get("../models/openclip_vit_b32_int8.onnx");
        }

        onnxService = new LocalOnnxEmbeddingService(modelPath.toString(), 3);
        onnxService.init();

        // Generate synthetic test PNG
        BufferedImage img1 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = img1.createGraphics();
        g1.setColor(Color.BLUE);
        g1.fillRect(0, 0, 32, 32);
        g1.setColor(Color.WHITE);
        g1.drawString("TEST", 5, 20);
        g1.dispose();

        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        ImageIO.write(img1, "png", baos1);
        validSampleBase64Png = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos1.toByteArray());

        // Generate synthetic test JPEG
        BufferedImage img2 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img2.createGraphics();
        g2.setColor(Color.RED);
        g2.fillRect(0, 0, 32, 32);
        g2.dispose();

        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        ImageIO.write(img2, "jpg", baos2);
        validSampleBase64Jpeg = Base64.getEncoder().encodeToString(baos2.toByteArray());
    }

    @AfterAll
    static void tearDown() {
        if (onnxService != null) {
            onnxService.close();
        }
    }

    @Test
    @DisplayName("ONNX model is loaded and available")
    void testModelAvailability() {
        assertThat(onnxService.isAvailable()).isTrue();
        AiHealthResponse health = onnxService.getHealth();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.service()).isEqualTo("campusfind-ai");
        assertThat(health.ai()).containsEntry("available", true);
        assertThat(health.ai()).containsEntry("dimension", 512);
    }

    @Test
    @DisplayName("Generates 512-dimensional normalized embedding for valid PNG with data URI")
    void testEmbeddingGenerationPng() {
        List<Double> embedding = onnxService.generateEmbeddingFromBase64(validSampleBase64Png);
        assertThat(embedding).isNotNull();
        assertThat(embedding).hasSize(512);

        // Verify L2 normalization: sum(x_i^2) == 1.0
        double normSq = embedding.stream().mapToDouble(v -> v * v).sum();
        assertThat(Math.sqrt(normSq)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));

        // Check for NaN or Infinite values
        for (Double val : embedding) {
            assertThat(val).isNotNull();
            assertThat(Double.isFinite(val)).isTrue();
        }
    }

    @Test
    @DisplayName("Generates 512-dimensional normalized embedding for raw Base64 JPEG")
    void testEmbeddingGenerationJpeg() {
        List<Double> embedding = onnxService.generateEmbeddingFromBase64(validSampleBase64Jpeg);
        assertThat(embedding).isNotNull();
        assertThat(embedding).hasSize(512);

        double normSq = embedding.stream().mapToDouble(v -> v * v).sum();
        assertThat(Math.sqrt(normSq)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("Rejects null or blank Base64 image")
    void testRejectsBlankImage() {
        assertThatThrownBy(() -> onnxService.generateEmbeddingFromBase64(null))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> onnxService.generateEmbeddingFromBase64("   "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Rejects corrupted Base64 image data")
    void testRejectsCorruptedImage() {
        assertThatThrownBy(() -> onnxService.generateEmbeddingFromBase64("data:image/png;base64,not-valid-base64-content!@#$"))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> onnxService.generateEmbeddingFromBase64(Base64.getEncoder().encodeToString("just text, not an image".getBytes())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Rejects oversized image (> 15MB)")
    void testRejectsOversizedImage() {
        // Create 16MB dummy string
        String oversized = "data:image/png;base64," + "A".repeat(16 * 1024 * 1024);
        assertThatThrownBy(() -> onnxService.generateEmbeddingFromBase64(oversized))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum allowed size");
    }

    @Test
    @DisplayName("Cosine similarity returns 1.0 for identical vectors and 0.0 for orthogonal")
    void testCosineSimilarityCalculation() {
        List<Double> vecA = List.of(1.0, 0.0, 0.0);
        List<Double> vecB = List.of(1.0, 0.0, 0.0);
        List<Double> vecC = List.of(0.0, 1.0, 0.0);

        assertThat(LocalOnnxEmbeddingService.calculateCosineSimilarity(vecA, vecB)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(LocalOnnxEmbeddingService.calculateCosineSimilarity(vecA, vecC)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Ranks candidate embeddings correctly and filters by threshold and limit")
    void testRankCandidates() {
        List<Double> queryVec = onnxService.generateEmbeddingFromBase64(validSampleBase64Png);

        // Candidate 1: synthetic embedding with lower similarity
        List<Double> candidateVec2 = new ArrayList<>(queryVec);
        for (int i = 0; i < 256; i++) {
            candidateVec2.set(i, -candidateVec2.get(i));
        }

        List<AiCandidateDto> candidates = List.of(
                new AiCandidateDto(101L, candidateVec2),
                new AiCandidateDto(102L, queryVec)
        );

        AiRankResponse response = onnxService.rankCandidates(queryVec, candidates, 0.30, 5);

        assertThat(response).isNotNull();
        assertThat(response.matches()).isNotEmpty();
        // Candidate 102 should be ranked first because its similarity is ~ 1.0
        assertThat(response.matches().get(0).id()).isEqualTo(102L);
        assertThat(response.matches().get(0).similarity()).isGreaterThan(0.95);
    }

    @Test
    @DisplayName("Handles concurrent inference without deadlocks or thread safety issues")
    void testConcurrentInference() throws Exception {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<List<Double>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String img = (i % 2 == 0) ? validSampleBase64Png : validSampleBase64Jpeg;
            futures.add(executor.submit(() -> onnxService.generateEmbeddingFromBase64(img)));
        }

        for (Future<List<Double>> future : futures) {
            List<Double> embedding = future.get(10, TimeUnit.SECONDS);
            assertThat(embedding).isNotNull();
            assertThat(embedding).hasSize(512);
        }

        executor.shutdown();
    }
}

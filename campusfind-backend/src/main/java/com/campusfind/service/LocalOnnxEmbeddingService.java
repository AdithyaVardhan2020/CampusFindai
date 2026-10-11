package com.campusfind.service;

import ai.onnxruntime.*;
import com.campusfind.dto.AiCandidateDto;
import com.campusfind.dto.AiHealthResponse;
import com.campusfind.dto.AiMatchItemDto;
import com.campusfind.dto.AiRankResponse;
import com.campusfind.dto.AiSimilarityResponse;
import com.campusfind.exception.AiServiceUnavailableException;
import com.campusfind.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * High-performance, in-process AI visual embedding engine using ONNX Runtime Java.
 * Runs quantized OpenCLIP ViT-B/32 (91.6 MB) directly inside Spring Boot with zero external
 * Python/Flask dependencies, fitting within Render Free's 512 MB RAM ceiling.
 */
@Service
public class LocalOnnxEmbeddingService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LocalOnnxEmbeddingService.class);

    // OpenCLIP ViT-B/32 normalization constants
    private static final float[] MEAN = {0.48145466f, 0.4578275f, 0.40821073f};
    private static final float[] STD  = {0.26862954f, 0.26130258f, 0.27577711f};

    private static final int TARGET_WIDTH = 224;
    private static final int TARGET_HEIGHT = 224;
    private static final int EXPECTED_DIMENSION = 512;
    private static final int MAX_BASE64_LENGTH = 15 * 1024 * 1024; // 15 MB max raw base64

    @Value("${campusfind.ai.onnx.enabled:true}")
    private boolean onnxEnabled = true;

    @Value("${campusfind.ai.onnx.model-path:models/openclip_vit_b32_int8.onnx}")
    private String configuredModelPath = "models/openclip_vit_b32_int8.onnx";

    @Value("${campusfind.ai.onnx.max-concurrent-inferences:3}")
    private int maxConcurrentInferences = 3;

    private OrtEnvironment env;
    private OrtSession session;
    private Semaphore concurrencyLimiter;
    private volatile boolean isAvailable = false;
    private String activeModelInfo = "uninitialized";
    private File tempExtractedModelFile = null;

    public LocalOnnxEmbeddingService() {
        this.onnxEnabled = true;
        this.configuredModelPath = "models/openclip_vit_b32_int8.onnx";
        this.maxConcurrentInferences = 3;
    }

    public LocalOnnxEmbeddingService(String configuredModelPath, int maxConcurrentInferences) {
        this.onnxEnabled = true;
        this.configuredModelPath = configuredModelPath;
        this.maxConcurrentInferences = maxConcurrentInferences;
    }

    @Override
    public void close() {
        cleanup();
    }

    @PostConstruct
    public void init() {
        if (!onnxEnabled) {
            log.info("Local ONNX embedding engine is disabled via configuration.");
            return;
        }

        concurrencyLimiter = new Semaphore(Math.max(1, maxConcurrentInferences));
        try {
            File modelFile = resolveModelFile();
            if (modelFile == null || !modelFile.exists() || modelFile.length() == 0) {
                log.warn("ONNX model file could not be located. Local AI embedding will be marked unavailable.");
                return;
            }

            log.info("Initializing ONNX Runtime Java with model: {} ({} MB)",
                    modelFile.getAbsolutePath(), modelFile.length() / (1024 * 1024));

            long t0 = System.currentTimeMillis();
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(1);
            opts.setInterOpNumThreads(1);
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            session = env.createSession(modelFile.getAbsolutePath(), opts);
            long loadMs = System.currentTimeMillis() - t0;

            isAvailable = true;
            activeModelInfo = String.format("OpenCLIP ViT-B/32 INT8 (%s, %d MB, loaded in %d ms)",
                    modelFile.getName(), modelFile.length() / (1024 * 1024), loadMs);
            log.info("Local ONNX embedding engine successfully initialized: {}", activeModelInfo);

        } catch (Exception ex) {
            log.error("Failed to initialize ONNX Runtime Java session: {}", ex.getMessage(), ex);
            isAvailable = false;
            activeModelInfo = "Initialization error: " + ex.getMessage();
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up ONNX Runtime resources...");
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
            if (tempExtractedModelFile != null && tempExtractedModelFile.exists()) {
                boolean deleted = tempExtractedModelFile.delete();
                log.debug("Temporary extracted model file deleted: {}", deleted);
            }
        } catch (Exception ex) {
            log.warn("Error during ONNX Runtime shutdown: {}", ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return isAvailable && session != null;
    }

    public AiHealthResponse getHealth() {
        if (isAvailable()) {
            return new AiHealthResponse(
                    "UP",
                    "campusfind-ai",
                    Map.of(
                            "available", true,
                            "engine", "in-process-onnx-runtime",
                            "model", "openclip_vit_b32_int8",
                            "dimension", EXPECTED_DIMENSION,
                            "modelInfo", activeModelInfo
                    )
            );
        } else {
            return new AiHealthResponse(
                    "DOWN",
                    "campusfind-ai",
                    Map.of(
                            "available", false,
                            "error", activeModelInfo
                    )
            );
        }
    }

    /**
     * Generates a normalized 512-dimensional visual embedding vector from a Base64-encoded image.
     */
    public List<Double> generateEmbeddingFromBase64(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new BadRequestException("Image base64 content cannot be empty.");
        }

        if (imageBase64.length() > MAX_BASE64_LENGTH) {
            throw new BadRequestException("Image payload exceeds maximum allowed size (15 MB).");
        }

        if (!isAvailable()) {
            throw new AiServiceUnavailableException("Local ONNX AI engine is not available: " + activeModelInfo);
        }

        String rawBase64 = imageBase64.trim();
        if (rawBase64.startsWith("data:") && rawBase64.contains(",")) {
            rawBase64 = rawBase64.substring(rawBase64.indexOf(',') + 1).trim();
        }

        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(rawBase64);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid Base64 image payload: " + ex.getMessage());
        }

        BufferedImage originalImage;
        try {
            originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception ex) {
            throw new BadRequestException("Failed to decode image data: " + ex.getMessage());
        }

        if (originalImage == null) {
            throw new BadRequestException("Unsupported or corrupt image format. Supported formats: JPEG, PNG, GIF, BMP.");
        }

        if (originalImage.getWidth() <= 0 || originalImage.getHeight() <= 0 ||
                originalImage.getWidth() > 8192 || originalImage.getHeight() > 8192) {
            throw new BadRequestException("Image dimensions are invalid or exceed 8192x8192 limit.");
        }

        // Limit concurrent heavy inferences to stay strictly within Render Free memory envelope
        try {
            concurrencyLimiter.acquire();
            try {
                return executeInference(originalImage);
            } finally {
                concurrencyLimiter.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiServiceUnavailableException("Inference request was interrupted.");
        } catch (BadRequestException | AiServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("ONNX inference forward pass error: {}", ex.getMessage(), ex);
            throw new AiServiceUnavailableException("Failed to generate visual embedding: " + ex.getMessage(), ex);
        }
    }

    private List<Double> executeInference(BufferedImage originalImage) throws Exception {
        // High-quality bilinear resize to 224x224 RGB
        BufferedImage resized = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(originalImage, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, null);
        } finally {
            g.dispose();
        }

        // Build CHW normalized float tensor matching OpenCLIP preprocessor
        float[][][][] tensorData = new float[1][3][TARGET_HEIGHT][TARGET_WIDTH];
        for (int y = 0; y < TARGET_HEIGHT; y++) {
            for (int x = 0; x < TARGET_WIDTH; x++) {
                int rgb = resized.getRGB(x, y);
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float gVal = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;

                tensorData[0][0][y][x] = (r - MEAN[0]) / STD[0];
                tensorData[0][1][y][x] = (gVal - MEAN[1]) / STD[1];
                tensorData[0][2][y][x] = (b - MEAN[2]) / STD[2];
            }
        }

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, tensorData);
        float[] embedding;
        try (OrtSession.Result result = session.run(Map.of("image", inputTensor))) {
            inputTensor.close(); // Immediate native resource release
            float[][] out = (float[][]) result.get(0).getValue();
            embedding = out[0];
        }

        if (embedding == null || embedding.length != EXPECTED_DIMENSION) {
            throw new AiServiceUnavailableException("ONNX model produced invalid embedding dimension: "
                    + (embedding == null ? 0 : embedding.length));
        }

        // Verify and apply L2 normalization: vector / sqrt(sum(x^2))
        double sumSq = 0.0;
        for (float v : embedding) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 0.0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) (embedding[i] / norm);
            }
        }

        List<Double> doubleList = new ArrayList<>(embedding.length);
        for (float v : embedding) {
            doubleList.add((double) v);
        }
        return doubleList;
    }

    /**
     * Computes standard cosine similarity between two float vectors directly in-process.
     */
    public AiSimilarityResponse calculateSimilarity(List<Double> embedding1, List<Double> embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.isEmpty() || embedding2.isEmpty()) {
            throw new BadRequestException("Both embeddings are required for similarity calculation.");
        }

        double sim = calculateCosineSimilarity(embedding1, embedding2);
        double percentage = Math.round(Math.max(0.0, sim) * 1000.0) / 10.0;
        return new AiSimilarityResponse(
                Math.round(sim * 10000.0) / 10000.0,
                percentage
        );
    }

    public static double calculateCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1 == null || vec2 == null || vec1.isEmpty() || vec2.isEmpty()) {
            return 0.0;
        }
        if (vec1.size() != vec2.size()) {
            log.warn("Embedding dimension mismatch: {} vs {}", vec1.size(), vec2.size());
            return 0.0;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vec1.size(); i++) {
            double a = vec1.get(i);
            double b = vec2.get(i);
            dot += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }

        double sim = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return Math.max(-1.0, Math.min(1.0, sim));
    }

    /**
     * Ranks candidate report embeddings against a target report embedding.
     */
    public AiRankResponse rankCandidates(List<Double> targetEmbedding, List<AiCandidateDto> candidates,
                                         double threshold, int limit) {
        if (targetEmbedding == null || targetEmbedding.isEmpty() || candidates == null || candidates.isEmpty()) {
            return new AiRankResponse(0, Collections.emptyList());
        }

        List<AiMatchItemDto> matches = new ArrayList<>();
        for (AiCandidateDto candidate : candidates) {
            if (candidate.embedding() == null || candidate.embedding().isEmpty()) {
                continue;
            }
            double sim = calculateCosineSimilarity(targetEmbedding, candidate.embedding());
            if (sim >= threshold) {
                double roundedSim = Math.round(sim * 10000.0) / 10000.0;
                double percentage = Math.round(Math.max(0.0, sim) * 1000.0) / 10.0;
                matches.add(new AiMatchItemDto(candidate.id(), roundedSim, percentage));
            }
        }

        matches.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
        if (limit > 0 && matches.size() > limit) {
            matches = matches.subList(0, limit);
        }

        return new AiRankResponse(matches.size(), matches);
    }

    private File resolveModelFile() {
        // 1. Check environment variable AI_MODEL_PATH
        String envPath = System.getenv("AI_MODEL_PATH");
        if (envPath != null && !envPath.isBlank()) {
            File f = new File(envPath);
            if (f.exists()) return f;
        }

        // 2. Check Spring configured property
        if (configuredModelPath != null && !configuredModelPath.isBlank()) {
            File f = new File(configuredModelPath);
            if (f.exists()) return f;
        }

        // 3. Check known filesystem relative paths
        String[] candidatePaths = {
                "models/openclip_vit_b32_int8.onnx",
                "campusfind-backend/models/openclip_vit_b32_int8.onnx",
                "../experiments/lightweight-ai/models/openclip_vit_b32_int8.onnx",
                "experiments/lightweight-ai/models/openclip_vit_b32_int8.onnx"
        };
        for (String cp : candidatePaths) {
            File f = new File(cp);
            if (f.exists()) return f;
        }

        // 4. Check classpath resource
        try (InputStream is = getClass().getResourceAsStream("/models/openclip_vit_b32_int8.onnx")) {
            if (is != null) {
                tempExtractedModelFile = File.createTempFile("openclip_vit_b32_int8_", ".onnx");
                tempExtractedModelFile.deleteOnExit();
                Files.copy(is, tempExtractedModelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Extracted classpath ONNX model to temporary file: {}", tempExtractedModelFile.getAbsolutePath());
                return tempExtractedModelFile;
            }
        } catch (IOException ex) {
            log.warn("Failed extracting classpath model: {}", ex.getMessage());
        }

        return null;
    }
}

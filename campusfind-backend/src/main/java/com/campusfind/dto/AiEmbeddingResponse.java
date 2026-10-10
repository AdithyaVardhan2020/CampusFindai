package com.campusfind.dto;

import java.util.List;

public record AiEmbeddingResponse(
        Integer dimension,
        List<Double> embedding
) {
}

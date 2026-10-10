package com.campusfind.dto;

import java.util.List;

public record AiSimilarityRequest(
        List<Double> embedding1,
        List<Double> embedding2
) {
}

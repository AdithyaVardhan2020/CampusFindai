package com.campusfind.dto;

import java.util.List;

public record AiRankRequest(
        List<Double> target_embedding,
        List<AiCandidateDto> candidates,
        double threshold,
        int limit
) {
}

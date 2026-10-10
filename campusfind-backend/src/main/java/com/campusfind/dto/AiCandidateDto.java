package com.campusfind.dto;

import java.util.List;

public record AiCandidateDto(
        Long id,
        List<Double> embedding
) {
}

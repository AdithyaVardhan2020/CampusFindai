package com.campusfind.dto;

public record AiMatchItemDto(
        Long id,
        Double similarity,
        Double percentage
) {
}

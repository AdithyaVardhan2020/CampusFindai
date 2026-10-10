package com.campusfind.dto;

import java.util.List;

public record AiRankResponse(
        int count,
        List<AiMatchItemDto> matches
) {
}

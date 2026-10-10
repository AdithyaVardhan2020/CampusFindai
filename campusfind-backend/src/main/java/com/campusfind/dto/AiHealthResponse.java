package com.campusfind.dto;

import java.util.Map;

public record AiHealthResponse(
        String status,
        String service,
        Map<String, Object> ai
) {
}

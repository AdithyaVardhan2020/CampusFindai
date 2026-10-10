package com.campusfind.dto;

import java.util.List;

public record HfChatRequest(
        String model,
        List<HfMessageDto> messages,
        Integer max_tokens,
        Double temperature
) {
}

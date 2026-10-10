package com.campusfind.dto;

import java.util.List;

public record HfChatResponse(
        String id,
        List<HfChoiceDto> choices
) {
}

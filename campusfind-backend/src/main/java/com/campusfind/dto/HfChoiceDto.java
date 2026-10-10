package com.campusfind.dto;

public record HfChoiceDto(
        Integer index,
        HfMessageDto message,
        String finish_reason
) {
}

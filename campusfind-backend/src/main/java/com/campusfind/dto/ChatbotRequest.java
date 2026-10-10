package com.campusfind.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatbotRequest(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 1000, message = "Message cannot exceed 1000 characters")
        String message,

        @Size(max = 10, message = "Conversation history cannot exceed 10 messages")
        List<@Valid ChatMessageDto> history
) {
}

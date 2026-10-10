package com.campusfind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageDto(
        @NotBlank(message = "Role is required")
        @Size(max = 20, message = "Role cannot exceed 20 characters")
        String role,

        @NotBlank(message = "Message content is required")
        @Size(max = 1000, message = "Message content cannot exceed 1000 characters")
        String content
) {
}

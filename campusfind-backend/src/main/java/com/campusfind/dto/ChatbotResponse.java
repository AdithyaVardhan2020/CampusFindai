package com.campusfind.dto;

public record ChatbotResponse(
        String reply,
        String model,
        boolean success
) {
    public static ChatbotResponse success(String reply, String model) {
        return new ChatbotResponse(reply, model, true);
    }

    public static ChatbotResponse unavailable(String message) {
        return new ChatbotResponse(message, "none", false);
    }
}

package com.campusfind.controller;

import com.campusfind.dto.ChatbotRequest;
import com.campusfind.dto.ChatbotResponse;
import com.campusfind.service.ChatbotService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/message")
    public ResponseEntity<ChatbotResponse> sendMessage(@Valid @RequestBody ChatbotRequest request) {
        ChatbotResponse response = chatbotService.generateResponse(request);
        return ResponseEntity.ok(response);
    }
}

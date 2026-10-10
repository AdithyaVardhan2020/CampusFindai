package com.campusfind.controller;

import com.campusfind.dto.ChatMessageDto;
import com.campusfind.dto.ChatbotRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ChatbotControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/v1/chatbot/message is publicly accessible without authentication and returns 200")
    void testSendMessagePubliclyAccessibleWithoutAuthentication() throws Exception {
        ChatbotRequest request = new ChatbotRequest("How do I report a lost wallet?", null);

        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reply").isNotEmpty())
                .andExpect(jsonPath("$.model").exists())
                .andExpect(jsonPath("$.success").value(false)); // False because token is unconfigured in test properties
    }

    @Test
    @DisplayName("POST /api/v1/chatbot/message with empty message returns 400 Bad Request")
    void testSendMessageEmptyMessageReturnsBadRequest() throws Exception {
        ChatbotRequest request = new ChatbotRequest("", null);

        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/chatbot/message with whitespace-only message returns 400 Bad Request")
    void testSendMessageWhitespaceMessageReturnsBadRequest() throws Exception {
        ChatbotRequest request = new ChatbotRequest("     ", null);

        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/chatbot/message with message exceeding 1000 characters returns 400 Bad Request")
    void testSendMessageOversizedMessageReturnsBadRequest() throws Exception {
        String longMessage = "A".repeat(1001);
        ChatbotRequest request = new ChatbotRequest(longMessage, null);

        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.message").value("Message cannot exceed 1000 characters"));
    }

    @Test
    @DisplayName("POST /api/v1/chatbot/message with conversation history exceeding 10 items returns 400 Bad Request")
    void testSendMessageOversizedHistoryReturnsBadRequest() throws Exception {
        List<ChatMessageDto> oversizedHistory = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            oversizedHistory.add(new ChatMessageDto("user", "Question " + i));
        }
        ChatbotRequest request = new ChatbotRequest("Can you help me?", oversizedHistory);

        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.history").value("Conversation history cannot exceed 10 messages"));
    }

    @Test
    @DisplayName("POST /api/v1/chatbot/message with valid conversation history returns 200")
    void testSendMessageWithValidHistoryReturns200() throws Exception {
        List<ChatMessageDto> history = List.of(
                new ChatMessageDto("user", "Hello"),
                new ChatMessageDto("assistant", "Hi! How can I help?")
        );
        ChatbotRequest request = new ChatbotRequest("Where is the security office?", history);

        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty());
    }
}

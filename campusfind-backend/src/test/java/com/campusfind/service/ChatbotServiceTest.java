package com.campusfind.service;

import com.campusfind.dto.ChatMessageDto;
import com.campusfind.dto.ChatbotRequest;
import com.campusfind.dto.ChatbotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ChatbotServiceTest {

    private MockRestServiceServer mockServer;
    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        chatbotService = new ChatbotService(builder.build());

        ReflectionTestUtils.setField(chatbotService, "apiUrl", "https://api.groq.com/openai/v1/chat/completions");
        ReflectionTestUtils.setField(chatbotService, "apiToken", "gsk_test_secret_token_12345");
        ReflectionTestUtils.setField(chatbotService, "modelId", "llama-3.1-8b-instant");
        ReflectionTestUtils.setField(chatbotService, "maxTokens", 500);
        ReflectionTestUtils.setField(chatbotService, "maxHistory", 6);
    }

    @Test
    @DisplayName("generateResponse() returns model reply when AI provider returns chat completion")
    void generateResponse_Success() {
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer gsk_test_secret_token_12345"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("llama-3.1-8b-instant"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("How do I report a lost phone?"))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "chatcmpl-123",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "To report a lost phone, navigate to 'Report Lost' from the top navigation..."
                              },
                              "finish_reason": "stop"
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                    ));

        ChatbotRequest request = new ChatbotRequest("How do I report a lost phone?", null);
        ChatbotResponse response = chatbotService.generateResponse(request);

        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.reply()).contains("To report a lost phone");
        assertThat(response.model()).isEqualTo("llama-3.1-8b-instant");
        mockServer.verify();
    }

    @Test
    @DisplayName("generateResponse() includes recent conversation history up to maxHistory limit")
    void generateResponse_WithHistory_LimitsToMaxHistory() {
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Hello"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].content").value("Hi there!"))
                .andExpect(jsonPath("$.messages[3].role").value("user"))
                .andExpect(jsonPath("$.messages[3].content").value("Where is security?"))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "The Campus Security desk is located in Hall B."
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                    ));

        List<ChatMessageDto> history = List.of(
                new ChatMessageDto("user", "Hello"),
                new ChatMessageDto("assistant", "Hi there!")
        );
        ChatbotRequest request = new ChatbotRequest("Where is security?", history);
        ChatbotResponse response = chatbotService.generateResponse(request);

        assertThat(response.success()).isTrue();
        assertThat(response.reply()).contains("Hall B");
        mockServer.verify();
    }

    @Test
    @DisplayName("generateResponse() returns friendly offline message when API token is missing or blank")
    void generateResponse_MissingToken_ReturnsOfflineMessage() {
        ReflectionTestUtils.setField(chatbotService, "apiToken", "");

        ChatbotRequest request = new ChatbotRequest("Can you help me?", null);
        ChatbotResponse response = chatbotService.generateResponse(request);

        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.reply()).contains("offline because the AI provider API key is not configured");
        // No HTTP request should be sent
        mockServer.verify();
    }

    @Test
    @DisplayName("generateResponse() returns safe message when AI provider returns 401 Unauthorized")
    void generateResponse_WhenUnauthorized_ReturnsSafeMessageWithoutLeakingToken() {
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Invalid token\"}"));

        ChatbotRequest request = new ChatbotRequest("Hello", null);
        ChatbotResponse response = chatbotService.generateResponse(request);

        assertThat(response.success()).isFalse();
        assertThat(response.reply()).contains("authorization issue with the AI provider");
        assertThat(response.reply()).doesNotContain("gsk_test_secret_token_12345");
        mockServer.verify();
    }

    @Test
    @DisplayName("generateResponse() returns friendly rate-limit message when AI provider returns 429 Too Many Requests")
    void generateResponse_WhenRateLimited_ReturnsRateLimitNotice() {
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":\"Rate limit reached\"}"));

        ChatbotRequest request = new ChatbotRequest("Hello", null);
        ChatbotResponse response = chatbotService.generateResponse(request);

        assertThat(response.success()).isFalse();
        assertThat(response.reply()).contains("experiencing high demand or rate limits");
        mockServer.verify();
    }

    @Test
    @DisplayName("generateResponse() returns clear notice when account is out of credits (402 Payment Required)")
    void generateResponse_WhenOutOfCredits_ReturnsCreditsNotice() {
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED).body("{\"error\":\"You have no remaining credits.\"}"));

        ChatbotRequest request = new ChatbotRequest("Hello", null);
        ChatbotResponse response = chatbotService.generateResponse(request);

        assertThat(response.success()).isFalse();
        assertThat(response.reply()).contains("no remaining credits");
        mockServer.verify();
    }

    @Test
    @DisplayName("generateResponse() returns friendly unavailable message when server error occurs")
    void generateResponse_When500ServerError_ReturnsUnavailableNotice() {
        mockServer.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withServerError());

        ChatbotRequest request = new ChatbotRequest("Help", null);
        ChatbotResponse response = chatbotService.generateResponse(request);

        assertThat(response.success()).isFalse();
        assertThat(response.reply()).contains("currently unavailable");
        mockServer.verify();
    }

    @Test
    @DisplayName("generateResponse() safely handles empty or blank message without making HTTP call")
    void generateResponse_BlankMessage_ReturnsValidationNotice() {
        ChatbotRequest request = new ChatbotRequest("   ", null);
        ChatbotResponse response = chatbotService.generateResponse(request);

        assertThat(response.success()).isFalse();
        assertThat(response.reply()).contains("valid question or message");
        mockServer.verify();
    }
}

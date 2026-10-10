package com.campusfind.controller;

import com.campusfind.dto.DatabaseHealth;
import com.campusfind.dto.HealthResponse;
import com.campusfind.service.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class HealthControllerTest {

    @Autowired
    private HealthController healthController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(healthController).build();
    }

    @Test
    @DisplayName("GET /api/v1/health returns HTTP 200 and UP status with verified database metadata")
    void testHealthEndpointReturns200WhenDatabaseIsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("campusfind-backend"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.database.databaseProductName").value("H2"))
                .andExpect(jsonPath("$.database.isAivenCloud").value(false));
    }

    @Test
    @DisplayName("Health response payload must never expose passwords or connection strings")
    void testHealthResponseDoesNotExposeSecrets() throws Exception {
        String responseBody = mockMvc.perform(get("/api/v1/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody.toLowerCase()).doesNotContain("password");
        assertThat(responseBody.toLowerCase()).doesNotContain("secret");
        assertThat(responseBody).doesNotContain("jdbc:");
        assertThat(responseBody).doesNotContain("campusfinddb");
    }

    @Test
    @DisplayName("HealthController returns HTTP 503 Service Unavailable when database check fails")
    void testHealthControllerReturns503WhenDatabaseIsDown() throws Exception {
        HealthService mockHealthService = mock(HealthService.class);
        DatabaseHealth dbDown = DatabaseHealth.down("Database connection failed validity check.");
        when(mockHealthService.checkHealth()).thenReturn(HealthResponse.down(dbDown));

        HealthController controllerWithDownDb = new HealthController(mockHealthService);
        MockMvc downMockMvc = MockMvcBuilders.standaloneSetup(controllerWithDownDb).build();

        downMockMvc.perform(get("/api/v1/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database.status").value("DOWN"))
                .andExpect(jsonPath("$.database.message").value("Database connection failed validity check."));

        ResponseEntity<HealthResponse> response = controllerWithDownDb.getHealth();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DOWN");
    }
}

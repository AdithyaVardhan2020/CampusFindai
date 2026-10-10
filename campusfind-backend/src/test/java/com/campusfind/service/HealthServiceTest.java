package com.campusfind.service;

import com.campusfind.dto.HealthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class HealthServiceTest {

    @Autowired
    private HealthService healthService;

    @Test
    @DisplayName("Live HealthService checks active database and returns UP status with valid metadata")
    void testLiveHealthCheckReturnsUp() {
        HealthResponse response = healthService.checkHealth();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.service()).isEqualTo("campusfind-backend");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.database()).isNotNull();
        assertThat(response.database().status()).isEqualTo("UP");
        assertThat(response.database().databaseProductName()).isEqualToIgnoringCase("H2");
        assertThat(response.database().isAivenCloud()).isFalse();
        assertThat(response.database().message()).isNull();
    }

    @Test
    @DisplayName("HealthService handles SQLException gracefully and returns DOWN without leaking secrets")
    void testHealthCheckHandlesSqlExceptionGracefully() throws Exception {
        DataSource mockDataSource = mock(DataSource.class);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Access denied for user 'admin' using password"));

        HealthService failingService = new HealthService(mockDataSource);
        HealthResponse response = failingService.checkHealth();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.database().status()).isEqualTo("DOWN");
        // Ensure raw exception message containing credentials or user info is NOT exposed in the DTO
        assertThat(response.database().message()).doesNotContain("admin");
        assertThat(response.database().message()).doesNotContain("password");
        assertThat(response.database().message()).isEqualTo("Database connectivity error: SQLException");
    }

    @Test
    @DisplayName("HealthService returns DOWN when JDBC isValid reports false")
    void testHealthCheckReturnsDownWhenConnectionInvalid() throws Exception {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.isValid(3)).thenReturn(false);

        HealthService failingService = new HealthService(mockDataSource);
        HealthResponse response = failingService.checkHealth();

        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.database().status()).isEqualTo("DOWN");
        assertThat(response.database().message()).isEqualTo("Database connection failed validity check.");
    }
}

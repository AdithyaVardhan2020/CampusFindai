package com.campusfind.service;

import com.campusfind.dto.HealthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import java.sql.DatabaseMetaData;
import java.sql.Statement;

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

    @Test
    @DisplayName("HealthService init does not crash startup if database is temporarily unavailable")
    void testInitDoesNotThrowWhenDatabaseUnavailableAtStartup() throws Exception {
        DataSource mockDataSource = mock(DataSource.class);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection refused / timeout"));

        HealthService service = new HealthService(mockDataSource);
        // init() must handle exception gracefully and not throw
        service.init();

        HealthResponse response = service.checkHealth();
        assertThat(response.status()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("HealthService recovers from temporary database delays once connection succeeds")
    void testRecoveryFromTemporaryConnectionDelay() throws Exception {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);

        when(mockConnection.isValid(3)).thenReturn(true);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getDatabaseProductName()).thenReturn("MySQL");
        when(mockMetaData.getDatabaseProductVersion()).thenReturn("8.4.8");
        when(mockMetaData.getDriverName()).thenReturn("MySQL Connector/J");
        when(mockMetaData.getURL()).thenReturn("jdbc:mysql://mysql-c9bf15d.aivencloud.com:11859/defaultdb");

        // First call fails with timeout/delay, second call succeeds
        when(mockDataSource.getConnection())
                .thenThrow(new SQLException("Communications link failure: The driver has not received any packets from the server"))
                .thenReturn(mockConnection);

        HealthService resilientService = new HealthService(mockDataSource);

        // 1. Initial attempt fails
        HealthResponse firstResponse = resilientService.checkHealth();
        assertThat(firstResponse.status()).isEqualTo("DOWN");

        // 2. Subsequent attempt recovers immediately to UP
        HealthResponse recoveredResponse = resilientService.checkHealth();
        assertThat(recoveredResponse.status()).isEqualTo("UP");
        assertThat(recoveredResponse.database().isAivenCloud()).isTrue();
        assertThat(recoveredResponse.database().databaseProductName()).isEqualTo("MySQL");
    }
}

package com.campusfind.service;

import com.campusfind.dto.DatabaseHealth;
import com.campusfind.dto.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Service that inspects actual connectivity and health of the persistence database.
 * Does not assume the database is healthy simply because the application started;
 * executes an active query round-trip and reports sanitized diagnostics.
 */
@Service
public class HealthService {

    private static final Logger logger = LoggerFactory.getLogger(HealthService.class);
    private static final int TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Actively checks database connectivity by acquiring a connection from the pool,
     * validating it with the driver, and executing a lightweight query.
     */
    public HealthResponse checkHealth() {
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(TIMEOUT_SECONDS);

            if (!isValid) {
                logger.warn("Database connection acquired but reported invalid by JDBC driver.");
                return HealthResponse.down(DatabaseHealth.down("Database connection failed validity check."));
            }

            // Execute test statement to confirm query execution capability
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }

            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            String productVersion = metaData.getDatabaseProductVersion();
            String driverName = metaData.getDriverName();
            String rawUrl = metaData.getURL();

            // Detect whether connected to an Aiven Cloud managed instance
            boolean isAivenCloud = rawUrl != null && (rawUrl.toLowerCase().contains("aiven") || rawUrl.toLowerCase().contains("aivencloud.com"));

            DatabaseHealth dbHealth = DatabaseHealth.up(
                    productName != null ? productName : "UNKNOWN",
                    productVersion != null ? productVersion : "UNKNOWN",
                    driverName != null ? driverName : "UNKNOWN",
                    isAivenCloud
            );

            return HealthResponse.up(dbHealth);

        } catch (SQLException exc) {
            logger.error("Active database connectivity check failed: {}", exc.getClass().getSimpleName());
            return HealthResponse.down(DatabaseHealth.down("Database connectivity error: " + exc.getClass().getSimpleName()));
        } catch (Exception exc) {
            logger.error("Unexpected error during health evaluation: {}", exc.getClass().getSimpleName());
            return HealthResponse.down(DatabaseHealth.down("System health verification failed."));
        }
    }
}

package com.campusfind.service;

import com.campusfind.dto.DatabaseHealth;
import com.campusfind.dto.HealthResponse;
import jakarta.annotation.PostConstruct;
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
 * Employs metadata caching, pre-warming, and short-TTL health caching to guarantee
 * fast (<1ms) response times for container health probes without CPU starvation or
 * blocking under cloud deployment environments (such as Render Free).
 */
@Service
public class HealthService {

    private static final Logger logger = LoggerFactory.getLogger(HealthService.class);
    private static final int TIMEOUT_SECONDS = 3;
    private static final long CACHE_TTL_MS = 5000; // 5 seconds cache

    private final DataSource dataSource;

    private volatile HealthResponse cachedResponse;
    private volatile long lastCheckTimeMs = 0;
    private volatile DatabaseHealth cachedMetadata;

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Pre-warms the database connection and caches static metadata at application startup
     * so incoming health check probes receive an immediate, non-blocking response.
     */
    @PostConstruct
    public void init() {
        try {
            logger.info("Pre-warming database health check and metadata cache...");
            checkHealthLive();
        } catch (Exception ex) {
            logger.warn("Initial database health check pre-warm encountered an issue (will retry on demand): {}", ex.getMessage());
        }
    }

    /**
     * Checks database connectivity. Returns cached UP health response if within TTL,
     * otherwise delegates to active live database check.
     */
    public HealthResponse checkHealth() {
        long now = System.currentTimeMillis();
        HealthResponse currentCached = this.cachedResponse;
        if (currentCached != null && "UP".equalsIgnoreCase(currentCached.status()) && (now - lastCheckTimeMs) < CACHE_TTL_MS) {
            return currentCached;
        }

        return checkHealthLive();
    }

    /**
     * Actively checks database connectivity by acquiring a connection from the pool,
     * validating it with the driver, and executing a lightweight query.
     */
    private synchronized HealthResponse checkHealthLive() {
        long now = System.currentTimeMillis();
        HealthResponse currentCached = this.cachedResponse;
        if (currentCached != null && "UP".equalsIgnoreCase(currentCached.status()) && (now - lastCheckTimeMs) < CACHE_TTL_MS) {
            return currentCached;
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(TIMEOUT_SECONDS);

            if (!isValid) {
                logger.warn("Database connection acquired but reported invalid by JDBC driver.");
                HealthResponse down = HealthResponse.down(DatabaseHealth.down("Database connection failed validity check."));
                this.cachedResponse = null;
                return down;
            }

            // Execute test statement to confirm query execution capability
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(TIMEOUT_SECONDS);
                statement.execute("SELECT 1");
            }

            DatabaseHealth dbHealth = this.cachedMetadata;
            if (dbHealth == null) {
                DatabaseMetaData metaData = connection.getMetaData();
                String productName = metaData.getDatabaseProductName();
                String productVersion = metaData.getDatabaseProductVersion();
                String driverName = metaData.getDriverName();
                String rawUrl = metaData.getURL();

                // Detect whether connected to an Aiven Cloud managed instance
                boolean isAivenCloud = rawUrl != null && (rawUrl.toLowerCase().contains("aiven") || rawUrl.toLowerCase().contains("aivencloud.com"));

                dbHealth = DatabaseHealth.up(
                        productName != null ? productName : "UNKNOWN",
                        productVersion != null ? productVersion : "UNKNOWN",
                        driverName != null ? driverName : "UNKNOWN",
                        isAivenCloud
                );
                this.cachedMetadata = dbHealth;
            }

            HealthResponse upResponse = HealthResponse.up(dbHealth);
            this.cachedResponse = upResponse;
            this.lastCheckTimeMs = System.currentTimeMillis();
            return upResponse;

        } catch (SQLException exc) {
            logger.error("Active database connectivity check failed: {}", exc.getClass().getSimpleName());
            HealthResponse down = HealthResponse.down(DatabaseHealth.down("Database connectivity error: " + exc.getClass().getSimpleName()));
            this.cachedResponse = null;
            return down;
        } catch (Exception exc) {
            logger.error("Unexpected error during health evaluation: {}", exc.getClass().getSimpleName());
            HealthResponse down = HealthResponse.down(DatabaseHealth.down("System health verification failed."));
            this.cachedResponse = null;
            return down;
        }
    }
}

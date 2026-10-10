package com.campusfind.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Spring Boot database configuration accurately initializes,
 * provides valid database connections, and uses H2 as the zero-config local default.
 */
@SpringBootTest
class DatabaseConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("DataSource bean is present and properly injected")
    void testDataSourceBeanIsPresent() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    @DisplayName("Active database connection is valid and can execute round-trip query")
    void testActualDatabaseConnectionIsValidAndExecutesQuery() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection).isNotNull();
            assertThat(connection.isValid(2)).isTrue();

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("Default local database is H2 in-memory when no cloud credentials are provided")
    void testDefaultDatabaseIsH2() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();

            assertThat(productName).isNotNull();
            assertThat(productName).isEqualToIgnoringCase("H2");
        }
    }
}
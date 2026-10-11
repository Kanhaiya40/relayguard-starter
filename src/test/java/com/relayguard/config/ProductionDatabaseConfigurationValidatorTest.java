package com.relayguard.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.SpringApplication;

class ProductionDatabaseConfigurationValidatorTest {
    @Test
    void rejectsMissingProductionDatabaseSettings() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        var validator = new ProductionDatabaseConfigurationValidator();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> validator.postProcessEnvironment(environment, new SpringApplication()));

        assertTrue(exception.getMessage().contains("DATABASE_URL"));
        assertTrue(exception.getMessage().contains("DATABASE_USERNAME"));
        assertTrue(exception.getMessage().contains("DATABASE_PASSWORD"));
    }

    @Test
    void acceptsCompleteProductionDatabaseSettings() {
        var environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "jdbc:postgresql://db.example.test/relayguard")
                .withProperty("DATABASE_USERNAME", "relayguard")
                .withProperty("DATABASE_PASSWORD", "test-secret");
        environment.setActiveProfiles("production");
        var validator = new ProductionDatabaseConfigurationValidator();

        assertDoesNotThrow(() -> validator.postProcessEnvironment(environment, new SpringApplication()));
    }
}

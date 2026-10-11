package com.relayguard.config;

import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

public class ProductionDatabaseConfigurationValidator implements EnvironmentPostProcessor {
    private static final List<String> JDBC_SETTINGS =
            List.of("DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD");
    private static final List<String> POSTGRES_SETTINGS =
            List.of("PGHOST", "PGDATABASE", "PGUSER", "PGPASSWORD");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (List.of(environment.getActiveProfiles()).stream().noneMatch("production"::equals)) return;
        boolean jdbcSettingsPresent = hasAllSettings(environment, JDBC_SETTINGS);
        boolean postgresSettingsPresent = hasAllSettings(environment, POSTGRES_SETTINGS);
        if (!jdbcSettingsPresent && !postgresSettingsPresent) {
            throw new IllegalStateException(
                    "Production profile requires either nonblank DATABASE_URL, DATABASE_USERNAME, and "
                            + "DATABASE_PASSWORD settings or nonblank PGHOST, PGDATABASE, PGUSER, and PGPASSWORD settings.");
        }
    }

    private boolean hasAllSettings(ConfigurableEnvironment environment, List<String> names) {
        return names.stream().allMatch(name -> StringUtils.hasText(environment.getProperty(name)));
    }
}

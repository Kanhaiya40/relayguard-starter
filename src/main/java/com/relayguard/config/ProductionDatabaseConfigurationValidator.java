package com.relayguard.config;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

public class ProductionDatabaseConfigurationValidator implements EnvironmentPostProcessor {
    private static final List<String> REQUIRED_SETTINGS =
            List.of("DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (List.of(environment.getActiveProfiles()).stream().noneMatch("production"::equals)) return;
        String missing = REQUIRED_SETTINGS.stream()
                .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
                .collect(Collectors.joining(", "));
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Production profile requires nonblank database settings: " + missing);
        }
    }
}

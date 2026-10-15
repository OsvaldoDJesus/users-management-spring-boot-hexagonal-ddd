package com.jcaa.usersmanagement.infrastructure.adapter.email;

import java.time.Duration;

public record BrevoConfig(
        String baseUrl,
        String apiKey,
        String fromAddress,
        String fromName,
        Duration connectTimeout,
        Duration readTimeout,
        int retryMaxAttempts,
        Duration retryInitialDelay,
        double retryMultiplier,
        int circuitFailureThreshold,
        int circuitSlidingWindowSize,
        int circuitMinimumCalls,
        Duration circuitOpenDuration) {

    public BrevoConfig {
        requireText(baseUrl, "brevo.base-url");
        requireText(apiKey, "brevo.api-key / BREVO_API_KEY");
        requireText(fromAddress, "brevo.from.address / BREVO_FROM_ADDRESS");
        if (!apiKey.startsWith("xkeysib-")) {
            throw new IllegalArgumentException(
                    "BREVO_API_KEY debe ser una clave API REST de Brevo con prefijo 'xkeysib-'.");
        }
    }

    private static void requireText(final String value, final String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "La configuracion requerida '" + propertyName + "' no puede estar vacia.");
        }
    }
}

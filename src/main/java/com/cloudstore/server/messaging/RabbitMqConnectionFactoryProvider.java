package com.cloudstore.server.messaging;

import com.rabbitmq.client.ConnectionFactory;

public final class RabbitMqConnectionFactoryProvider {

    private RabbitMqConnectionFactoryProvider() {
    }

    public static ConnectionFactory create() {
        ConnectionFactory factory = new ConnectionFactory();

        boolean tlsEnabled = getBooleanEnv("RABBITMQ_TLS_ENABLED", false);
        factory.setHost(getRequiredEnv("RABBITMQ_HOST"));
        factory.setPort(getIntEnv("RABBITMQ_PORT", tlsEnabled ? 5671 : 5672));
        factory.setUsername(getRequiredEnv("RABBITMQ_USERNAME"));
        factory.setPassword(getRequiredEnv("RABBITMQ_PASSWORD"));

        String vhost = System.getenv("RABBITMQ_VHOST");
        if (vhost != null && !vhost.isBlank()) {
            factory.setVirtualHost(vhost);
        }

        if (tlsEnabled) {
            try {
                factory.useSslProtocol();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to enable RabbitMQ TLS", e);
            }
        }

        return factory;
    }

    private static String getRequiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private static int getIntEnv(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer environment variable: " + key, e);
        }
    }

    private static boolean getBooleanEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}

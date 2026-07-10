package com.cloudstore.server.service.auth;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;

/**
 * Manages a Redis-backed blacklist of revoked JWT IDs (JTI).
 */
public class TokenBlacklistService {

    private final RedisClient redisClient;
    private static final String PREFIX = "blacklist:";

    /**
         * Reads Redis connection settings from environment variables.
         * Connection timeout: 2 s; socket timeout: 2 s; pool size: 8 connections.
     */
    public TokenBlacklistService() {    
        String host = getRequiredEnv("REDIS_HOST");
        int port = getIntEnv("REDIS_PORT", 6379);
        boolean tlsEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("REDIS_TLS_ENABLED", "false"));
        String password = System.getenv("REDIS_PASSWORD");

        // Pool configuration
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxWait(Duration.ofSeconds(2));

        // Client configuration (timeouts)
        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(2000)
                .socketTimeoutMillis(2000)
                .ssl(tlsEnabled);

        if (password != null && !password.isBlank()) {
            clientConfigBuilder.password(password);
        }

        DefaultJedisClientConfig clientConfig = clientConfigBuilder.build();

        // Build RedisClient using the recommended builder
        this.redisClient = RedisClient.builder()
                .hostAndPort(host, port)
                .poolConfig(poolConfig)              
                .clientConfig(clientConfig)
                .build();

        // Shutdown hook to close the client when the application exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                redisClient.close();
            } catch (Exception e) {
                // ignore
            }
        }));
    }

    private String getRequiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private int getIntEnv(String key, int defaultValue) {
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

    /**
         * Marks a JTI as revoked in Redis.
         * @param jti        The JWT ID claim to blacklist
         * @param ttlSeconds Seconds until the token expires (exp - now).
    **/
    public void revoke(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return; // Token already expired
        }
        try {
            redisClient.set(PREFIX + jti, "1", SetParams.setParams().ex(ttlSeconds));
        } catch (Exception e) {
            System.err.println("[WARN] TokenBlacklistService.revoke: Redis unavailable, skip blacklist for JTI="
                    + jti + " — " + e.getMessage());
        }
    }

    /**
         * Returns true if the given JTI has been blacklisted (i.e. the token was revoked).
         * @param jti The JWT ID to check
         * @return true if revoked or Redis is unreachable (fail-closed), false if valid
    **/
    public boolean isRevoked(String jti) {
        try {
            return redisClient.exists(PREFIX + jti);
        } catch (Exception e) {
            System.err.println("[WARN] TokenBlacklistService.isRevoked: Redis unavailable for JTI="
                    + jti + " — " + e.getMessage());
            return true;
        }
    }
}

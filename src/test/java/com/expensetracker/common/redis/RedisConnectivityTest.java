package com.expensetracker.common.redis;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Round-trips a value through whatever spring.data.redis.url resolves to
 * (Upstash, via config/application.properties).
 *
 * Skips itself when no real Redis is configured -- which is the case in CI,
 * where config/ is gitignored and the URL falls back to localhost. That keeps
 * CI green without ever needing the Upstash credential.
 */
@SpringBootTest
class RedisConnectivityTest {

    @Autowired
    RedisService redisService;

    @Autowired
    Environment environment;

    @Test
    void writesAndReadsBackAValue() {
        String url = environment.getProperty("spring.data.redis.url", "");

        Assumptions.assumeFalse(
                url.isEmpty() || url.contains("localhost"),
                "No remote Redis configured -- skipping connectivity check"
        );

        String key = "connectivity-check::" + System.nanoTime();

        redisService.setValue(key, "ok", 30);
        assertEquals("ok", redisService.get(key));

        redisService.delete(key);
        assertNull(redisService.get(key));
    }
}

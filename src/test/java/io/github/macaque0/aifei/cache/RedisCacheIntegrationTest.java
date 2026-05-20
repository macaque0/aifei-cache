package io.github.macaque0.aifei.cache;

import io.github.macaque0.aifei.cache.backend.RedisCache;
import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RedisCacheIntegrationTest {

    @Test
    public void redisBackendWorks() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("redis.integration"));

        String prefix = "aifei-cache-it-" + System.currentTimeMillis();
        Cache cache = new RedisCache(new CacheConfig()
                .setType("redis")
                .setDefaultName("default")
                .setDefaultTtlSeconds(1)
                .setKeyPrefix(prefix)
                .setRedisHost(required("redis.host"))
                .setRedisPort(Integer.getInteger("redis.port", 6379))
                .setRedisUser(blankToNull(System.getProperty("redis.user")))
                .setRedisPassword(blankToNull(System.getProperty("redis.password")))
                .setRedisDatabase(Integer.getInteger("redis.database", 0))
                .setRedisSsl(Boolean.getBoolean("redis.ssl"))
                .setRedisTimeoutMillis(Integer.getInteger("redis.timeoutMillis", 8000)));
        try {
            cache.put("s", "hello");
            assertEquals("hello", cache.get("s"));
            assertTrue(cache.exists("s"));
            cache.remove("s");
            assertNull(cache.get("s"));

            cache.put("ttl", "gone", 1);
            assertEquals("gone", cache.get("ttl"));
            Thread.sleep(1300);
            assertNull(cache.get("ttl"));

            AtomicInteger n = new AtomicInteger();
            assertEquals("v", cache.getOrSet("gos", 30, () -> {
                n.incrementAndGet();
                return "v";
            }));
            assertEquals("v", cache.getOrSet("gos", 30, () -> {
                n.incrementAndGet();
                return "x";
            }));
            assertEquals(1, n.get());

            assertEquals(1L, cache.incr("counter"));
            Thread.sleep(1300);
            assertFalse(cache.exists("counter"));

            assertEquals(6L, cache.incr("default", "counter2", 6));
            assertEquals(4L, cache.decr("default", "counter2", 2));

            cache.put("num", Integer.valueOf(2));
            Object before = cache.get("num");
            assertTrue(before instanceof Integer);
            assertEquals(2, ((Integer) before).intValue());
            assertEquals(5L, cache.incr("default", "num", 3));
            Object after = cache.get("num");
            assertTrue(after instanceof Long);
            assertEquals(5L, ((Long) after).longValue());

            cache.put("badCounter", "nope");
            try {
                cache.incr("badCounter");
                org.junit.Assert.fail("redis should reject non-numeric counters");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("not an integer counter"));
            }

            cache.clear("default");
            assertFalse(cache.exists("gos"));
            assertFalse(cache.exists("counter"));
        } finally {
            try {
                cache.clearAll();
            } finally {
                cache.close();
            }
        }
    }

    private String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required system property: " + name);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

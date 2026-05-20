package io.github.macaque0.aifei.cache.backend;

import io.github.macaque0.aifei.cache.CacheConfig;
import io.github.macaque0.aifei.cache.serializer.CacheSerializer;
import io.github.macaque0.aifei.cache.serializer.JdkCacheSerializer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RedisCache extends AbstractCache {

    private static final String INCR_SCRIPT =
            "local current = redis.call('get', KEYS[1]);" +
            "if (not current) or string.match(current, '^%-?%d+$') then " +
            "local next = redis.call('incrby', KEYS[1], ARGV[1]);" +
            "local ttl = tonumber(ARGV[2]);" +
            "if ttl > 0 then redis.call('expire', KEYS[1], ttl); else redis.call('persist', KEYS[1]); end;" +
            "return {1, next};" +
            "end;" +
            "return {0};";

    private final JedisPool pool;
    private final CacheSerializer serializer;

    public RedisCache(CacheConfig config) {
        this(config, new JdkCacheSerializer());
    }

    public RedisCache(CacheConfig config, CacheSerializer serializer) {
        super(config);
        if (serializer == null) {
            throw new IllegalArgumentException("serializer can not be null");
        }
        validateRedisConfig(config);
        this.serializer = serializer;
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        if (hasText(config.getRedisUser())) {
            this.pool = new JedisPool(poolConfig, redisUri(config), config.getRedisTimeoutMillis());
        } else {
            this.pool = new JedisPool(poolConfig,
                    config.getRedisHost().trim(),
                    config.getRedisPort(),
                    config.getRedisTimeoutMillis(),
                    trimToNull(config.getRedisPassword()),
                    config.getRedisDatabase(),
                    config.isRedisSsl());
        }
    }

    @Override
    protected Object getValue(String cacheName, String key) {
        try (Jedis jedis = pool.getResource()) {
            byte[] bytes = jedis.get(realKey(cacheName, key));
            if (bytes == null) {
                return null;
            }
            Long counterValue = tryParseLong(bytes);
            return counterValue != null ? counterValue : serializer.deserialize(bytes);
        }
    }

    @Override
    protected void putValue(String cacheName, String key, Object value, long ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            byte[] realKey = realKey(cacheName, key);
            byte[] bytes = serializer.serialize(value);
            if (ttlSeconds > 0) {
                jedis.setex(realKey, ttlSeconds, bytes);
            } else {
                jedis.set(realKey, bytes);
            }
        }
    }

    @Override
    public boolean exists(String cacheName, String key) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(realKey(cacheName, key));
        }
    }

    @Override
    public void remove(String cacheName, String key) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        try (Jedis jedis = pool.getResource()) {
            jedis.del(realKey(cacheName, key));
        }
    }

    @Override
    public void clear(String cacheName) {
        cacheName = requireCacheName(cacheName);
        deleteByPattern(globEscape(realPrefix(cacheName)) + "*");
    }

    @Override
    public void clearAll() {
        String prefix = trimToNull(config.getKeyPrefix());
        if (prefix == null) {
            throw new IllegalStateException("Redis clearAll requires non-empty cache.keyPrefix");
        }
        deleteByPattern(globEscape(prefix) + ":*");
    }

    @Override
    public long incr(String cacheName, String key, long delta) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        try (Jedis jedis = pool.getResource()) {
            String realKey = realKeyString(cacheName, key);
            Object result = jedis.eval(INCR_SCRIPT,
                    Collections.singletonList(realKey),
                    Arrays.asList(String.valueOf(delta), String.valueOf(config.getDefaultTtlSeconds())));
            List<?> values = (List<?>) result;
            if (((Number) values.get(0)).longValue() == 1L) {
                return ((Number) values.get(1)).longValue();
            }

            return incrSerializedNumber(jedis, cacheName, key, delta);
        }
    }

    private long incrSerializedNumber(Jedis jedis, String cacheName, String key, long delta) {
        String realKey = realKeyString(cacheName, key);
        byte[] realKeyBytes = realKey(cacheName, key);

        while (true) {
            jedis.watch(realKey);
            try {
                byte[] old = jedis.get(realKeyBytes);
                Long counter = tryParseLong(old);
                if (old == null || counter != null) {
                    jedis.unwatch();
                    return incr(cacheName, key, delta);
                }

                Object value = serializer.deserialize(old);
                long next = nextCounterValue(value, cacheName, key, delta);
                byte[] bytes = String.valueOf(next).getBytes(StandardCharsets.UTF_8);

                redis.clients.jedis.Transaction tx = jedis.multi();
                if (config.getDefaultTtlSeconds() > 0) {
                    tx.setex(realKeyBytes, config.getDefaultTtlSeconds(), bytes);
                } else {
                    tx.set(realKeyBytes, bytes);
                }
                if (tx.exec() != null) {
                    return next;
                }
            } catch (RuntimeException e) {
                jedis.unwatch();
                throw e;
            }
        }
    }

    @Override
    public void close() {
        pool.close();
    }

    private void deleteByPattern(String pattern) {
        try (Jedis jedis = pool.getResource()) {
            ScanParams params = new ScanParams().match(pattern).count(500);
            String cursor = ScanParams.SCAN_POINTER_START;
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                List<String> keys = result.getResult();
                if (!keys.isEmpty()) {
                    jedis.del(keys.toArray(new String[0]));
                }
                cursor = result.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        }
    }

    private String realPrefix(String cacheName) {
        String prefix = trimToNull(config.getKeyPrefix());
        return (prefix == null ? "" : prefix + ":") + cacheName + ":";
    }

    private byte[] realKey(String cacheName, String key) {
        return realKeyString(cacheName, key).getBytes(StandardCharsets.UTF_8);
    }

    private String realKeyString(String cacheName, String key) {
        return realPrefix(cacheName) + key;
    }

    private Long tryParseLong(byte[] bytes) {
        try {
            String value = new String(bytes, StandardCharsets.UTF_8);
            if (!value.matches("-?\\d+")) {
                return null;
            }
            return Long.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private URI redisUri(CacheConfig config) {
        try {
            String scheme = config.isRedisSsl() ? "rediss" : "redis";
            String user = URLEncoder.encode(config.getRedisUser().trim(), "UTF-8");
            String password = trimToNull(config.getRedisPassword()) == null ? "" : URLEncoder.encode(config.getRedisPassword().trim(), "UTF-8");
            return URI.create(scheme + "://" + user + ":" + password + "@" +
                    config.getRedisHost().trim() + ":" + config.getRedisPort() + "/" + config.getRedisDatabase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid redis config", e);
        }
    }

    private void validateRedisConfig(CacheConfig config) {
        if (!hasText(config.getRedisHost())) {
            throw new IllegalArgumentException("cache.redis.host can not be blank");
        }
        if (config.getRedisPort() <= 0 || config.getRedisPort() > 65535) {
            throw new IllegalArgumentException("cache.redis.port must be between 1 and 65535");
        }
        if (config.getRedisDatabase() < 0) {
            throw new IllegalArgumentException("cache.redis.database can not be negative");
        }
        if (config.getRedisTimeoutMillis() <= 0) {
            throw new IllegalArgumentException("cache.redis.timeoutMillis must be positive");
        }
    }

    private String globEscape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '*' || ch == '?' || ch == '[' || ch == ']') {
                out.append('\\');
            }
            out.append(ch);
        }
        return out.toString();
    }
}

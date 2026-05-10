package cn.aifei.cache.backend;

import cn.aifei.cache.CacheConfig;
import cn.aifei.cache.serializer.CacheSerializer;
import cn.aifei.cache.serializer.JdkCacheSerializer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLEncoder;
import java.util.List;

public class RedisCache extends AbstractCache {

    private final JedisPool pool;
    private final CacheSerializer serializer = new JdkCacheSerializer();

    public RedisCache(CacheConfig config) {
        super(config);
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        if (config.getRedisUser() != null) {
            this.pool = new JedisPool(poolConfig, redisUri(config), config.getRedisTimeoutMillis());
        } else {
            this.pool = new JedisPool(poolConfig,
                    config.getRedisHost(),
                    config.getRedisPort(),
                    config.getRedisTimeoutMillis(),
                    config.getRedisPassword(),
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
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(realKey(cacheName, key));
        }
    }

    @Override
    public void remove(String cacheName, String key) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(realKey(cacheName, key));
        }
    }

    @Override
    public void clear(String cacheName) {
        deleteByPattern(realPrefix(cacheName) + "*");
    }

    @Override
    public void clearAll() {
        if (config.getKeyPrefix() == null || config.getKeyPrefix().trim().isEmpty()) {
            throw new IllegalStateException("Redis clearAll requires non-empty cache.keyPrefix");
        }
        deleteByPattern(config.getKeyPrefix() + ":*");
    }

    @Override
    public long incr(String cacheName, String key, long delta) {
        try (Jedis jedis = pool.getResource()) {
            byte[] realKey = realKey(cacheName, key);
            byte[] old = jedis.get(realKey);
            Long counter = tryParseLong(old);
            if (old == null || counter != null) {
                return jedis.incrBy(realKey, delta);
            }

            Object value = serializer.deserialize(old);
            if (!(value instanceof Number)) {
                throw new IllegalStateException("Cache value is not an integer counter: " + cacheName + ":" + key);
            }

            long next = ((Number) value).longValue() + delta;
            byte[] bytes = String.valueOf(next).getBytes(StandardCharsets.UTF_8);
            long ttlMillis = jedis.pttl(realKey);
            if (ttlMillis > 0) {
                jedis.psetex(realKey, ttlMillis, bytes);
            } else {
                jedis.set(realKey, bytes);
            }
            return next;
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
        String prefix = config.getKeyPrefix();
        return (prefix == null || prefix.isEmpty() ? "" : prefix + ":") + cacheName + ":";
    }

    private byte[] realKey(String cacheName, String key) {
        return (realPrefix(cacheName) + key).getBytes(StandardCharsets.UTF_8);
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
            String user = URLEncoder.encode(config.getRedisUser(), "UTF-8");
            String password = config.getRedisPassword() == null ? "" : URLEncoder.encode(config.getRedisPassword(), "UTF-8");
            return URI.create(scheme + "://" + user + ":" + password + "@" +
                    config.getRedisHost() + ":" + config.getRedisPort() + "/" + config.getRedisDatabase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid redis config", e);
        }
    }
}

package io.github.macaque0.aifei.cache.backend;

import io.github.macaque0.aifei.cache.Cache;
import io.github.macaque0.aifei.cache.CacheConfig;
import java.util.function.Supplier;

public abstract class AbstractCache implements Cache {

    protected final CacheConfig config;
    private final Object[] locks = new Object[64];

    protected AbstractCache(CacheConfig config) {
        this.config = requireConfig(config);
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public <T> T get(String key) {
        return get(config.getDefaultName(), key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        Object value = getValue(cacheName, key);
        return value == NullValue.INSTANCE ? null : (T) value;
    }

    @Override
    public void put(String key, Object value) {
        put(config.getDefaultName(), key, value, config.getDefaultTtlSeconds());
    }

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        put(config.getDefaultName(), key, value, ttlSeconds);
    }

    @Override
    public void put(String cacheName, String key, Object value) {
        put(cacheName, key, value, config.getDefaultTtlSeconds());
    }

    @Override
    public void put(String cacheName, String key, Object value, long ttlSeconds) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        ttlSeconds = requireTtlSeconds(ttlSeconds);
        if (value == null) {
            if (!config.isCacheNull()) {
                remove(cacheName, key);
                return;
            }
            value = NullValue.INSTANCE;
        }
        putValue(cacheName, key, value, ttlSeconds);
    }

    @Override
    public <T> T getOrSet(String key, Supplier<T> supplier) {
        return getOrSet(config.getDefaultName(), key, config.getDefaultTtlSeconds(), supplier);
    }

    @Override
    public <T> T getOrSet(String key, long ttlSeconds, Supplier<T> supplier) {
        return getOrSet(config.getDefaultName(), key, ttlSeconds, supplier);
    }

    @Override
    public <T> T getOrSet(String cacheName, String key, long ttlSeconds, Supplier<T> supplier) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        ttlSeconds = requireTtlSeconds(ttlSeconds);
        supplier = requireSupplier(supplier);
        T value = get(cacheName, key);
        if (value != null || exists(cacheName, key)) {
            return value;
        }
        synchronized (lockFor(cacheName, key)) {
            value = get(cacheName, key);
            if (value != null || exists(cacheName, key)) {
                return value;
            }
            value = supplier.get();
            put(cacheName, key, value, ttlSeconds);
            return value;
        }
    }

    private Object lockFor(String cacheName, String key) {
        int hash = 31 * cacheName.hashCode() + key.hashCode();
        return locks[(hash & 0x7fffffff) % locks.length];
    }

    @Override
    public boolean exists(String key) {
        return exists(config.getDefaultName(), key);
    }

    @Override
    public void remove(String key) {
        remove(config.getDefaultName(), key);
    }

    @Override
    public long incr(String key) {
        return incr(config.getDefaultName(), key, 1);
    }

    @Override
    public long decr(String key) {
        return decr(config.getDefaultName(), key, 1);
    }

    @Override
    public long decr(String cacheName, String key, long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("delta can not be negative");
        }
        try {
            return incr(cacheName, key, Math.negateExact(delta));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("delta is too large: " + delta, e);
        }
    }

    protected long nextCounterValue(Object old, String cacheName, String key, long delta) {
        if (old == null) {
            return delta;
        }
        if (old instanceof Number) {
            try {
                return Math.addExact(((Number) old).longValue(), delta);
            } catch (ArithmeticException e) {
                throw new IllegalStateException("Cache counter overflow: " + cacheName + ":" + key, e);
            }
        }
        throw new IllegalStateException("Cache value is not an integer counter: " + cacheName + ":" + key);
    }

    protected String requireCacheName(String cacheName) {
        return requireText(cacheName, "cacheName can not be blank");
    }

    protected String requireKey(String key) {
        return requireText(key, "key can not be blank");
    }

    protected long requireTtlSeconds(long ttlSeconds) {
        if (ttlSeconds < 0) {
            throw new IllegalArgumentException("ttlSeconds can not be negative");
        }
        return ttlSeconds;
    }

    protected static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    protected static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static CacheConfig requireConfig(CacheConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        requireText(config.getDefaultName(), "cache.defaultName can not be blank");
        if (config.getDefaultTtlSeconds() < 0) {
            throw new IllegalArgumentException("cache.defaultTtlSeconds can not be negative");
        }
        if (config.getMaxSize() <= 0) {
            throw new IllegalArgumentException("cache.maxSize must be positive");
        }
        return config;
    }

    private static <T> Supplier<T> requireSupplier(Supplier<T> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("supplier can not be null");
        }
        return supplier;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    protected abstract Object getValue(String cacheName, String key);

    protected abstract void putValue(String cacheName, String key, Object value, long ttlSeconds);
}

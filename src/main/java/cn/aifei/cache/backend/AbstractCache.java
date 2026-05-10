package cn.aifei.cache.backend;

import cn.aifei.cache.Cache;
import cn.aifei.cache.CacheConfig;
import java.util.function.Supplier;

public abstract class AbstractCache implements Cache {

    protected final CacheConfig config;
    private final Object[] locks = new Object[64];

    protected AbstractCache(CacheConfig config) {
        this.config = config;
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
        return incr(cacheName, key, -delta);
    }

    protected abstract Object getValue(String cacheName, String key);

    protected abstract void putValue(String cacheName, String key, Object value, long ttlSeconds);
}

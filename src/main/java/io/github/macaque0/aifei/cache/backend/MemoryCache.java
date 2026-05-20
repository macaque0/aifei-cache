package io.github.macaque0.aifei.cache.backend;

import io.github.macaque0.aifei.cache.CacheConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MemoryCache extends AbstractCache {

    private final ConcurrentMap<String, ConcurrentMap<String, CacheEntry>> store = new ConcurrentHashMap<>();

    public MemoryCache(CacheConfig config) {
        super(config);
    }

    @Override
    protected Object getValue(String cacheName, String key) {
        ConcurrentMap<String, CacheEntry> cache = store.get(cacheName);
        if (cache == null) {
            return null;
        }
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }
        return entry.getValue();
    }

    @Override
    protected void putValue(String cacheName, String key, Object value, long ttlSeconds) {
        ConcurrentMap<String, CacheEntry> cache = store.computeIfAbsent(cacheName, k -> new ConcurrentHashMap<>());
        cache.put(key, new CacheEntry(value, ttlSeconds));
        enforceMaxSize(cache);
    }

    @Override
    public boolean exists(String cacheName, String key) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        return getValue(cacheName, key) != null;
    }

    @Override
    public void remove(String cacheName, String key) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        ConcurrentMap<String, CacheEntry> cache = store.get(cacheName);
        if (cache != null) {
            cache.remove(key);
        }
    }

    @Override
    public void clear(String cacheName) {
        cacheName = requireCacheName(cacheName);
        ConcurrentMap<String, CacheEntry> cache = store.get(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    @Override
    public void clearAll() {
        store.clear();
    }

    @Override
    public long incr(String cacheName, String key, long delta) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        ConcurrentMap<String, CacheEntry> cache = store.computeIfAbsent(cacheName, k -> new ConcurrentHashMap<>());
        synchronized (cache) {
            Object old = getValue(cacheName, key);
            long next = nextCounterValue(old, cacheName, key, delta);
            cache.put(key, new CacheEntry(next, config.getDefaultTtlSeconds()));
            enforceMaxSize(cache);
            return next;
        }
    }

    @Override
    public void close() {
        clearAll();
    }

    private void enforceMaxSize(ConcurrentMap<String, CacheEntry> cache) {
        long maxSize = config.getMaxSize();
        if (maxSize < 0) {
            return;
        }
        while (cache.size() > maxSize) {
            String evictKey = null;
            for (String candidate : cache.keySet()) {
                evictKey = candidate;
                CacheEntry entry = cache.get(candidate);
                if (entry != null && entry.isExpired()) {
                    break;
                }
            }
            if (evictKey == null || cache.remove(evictKey) == null) {
                return;
            }
        }
    }
}

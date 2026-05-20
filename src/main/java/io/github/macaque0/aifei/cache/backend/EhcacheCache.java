package io.github.macaque0.aifei.cache.backend;

import io.github.macaque0.aifei.cache.CacheConfig;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;

public class EhcacheCache extends AbstractCache {

    private static final String CACHE_PREFIX = "aifei_cache_";

    private final CacheManager manager;

    public EhcacheCache(CacheConfig config) {
        super(config);
        if (config.getMaxSize() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("cache.maxSize is too large for ehcache: " + config.getMaxSize());
        }
        Configuration cfg = new Configuration()
                .name("aifei-cache-" + Integer.toHexString(System.identityHashCode(this)))
                .defaultCache(new CacheConfiguration("aifei-cache-default-template", (int) config.getMaxSize()));
        this.manager = CacheManager.newInstance(cfg);
    }

    @Override
    protected Object getValue(String cacheName, String key) {
        net.sf.ehcache.Cache cache = cache(cacheName, false);
        if (cache == null) {
            return null;
        }
        Element element = cache.get(key);
        if (element == null) {
            return null;
        }
        CacheEntry entry = (CacheEntry) element.getObjectValue();
        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }
        return entry.getValue();
    }

    @Override
    protected void putValue(String cacheName, String key, Object value, long ttlSeconds) {
        cache(cacheName, true).put(new Element(key, new CacheEntry(value, ttlSeconds)));
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
        net.sf.ehcache.Cache cache = cache(cacheName, false);
        if (cache != null) {
            cache.remove(key);
        }
    }

    @Override
    public void clear(String cacheName) {
        cacheName = requireCacheName(cacheName);
        net.sf.ehcache.Cache cache = cache(cacheName, false);
        if (cache != null) {
            cache.removeAll();
        }
    }

    @Override
    public void clearAll() {
        for (String name : manager.getCacheNames()) {
            if (name.startsWith(CACHE_PREFIX)) {
                manager.getCache(name).removeAll();
            }
        }
    }

    @Override
    public long incr(String cacheName, String key, long delta) {
        cacheName = requireCacheName(cacheName);
        key = requireKey(key);
        net.sf.ehcache.Cache cache = cache(cacheName, true);
        synchronized (cache) {
            Object old = getValue(cacheName, key);
            long next = nextCounterValue(old, cacheName, key, delta);
            cache.put(new Element(key, new CacheEntry(next, config.getDefaultTtlSeconds())));
            return next;
        }
    }

    @Override
    public void close() {
        manager.shutdown();
    }

    private net.sf.ehcache.Cache cache(String cacheName, boolean create) {
        String realName = realCacheName(cacheName);
        net.sf.ehcache.Cache cache = manager.getCache(realName);
        if (cache != null || !create) {
            return cache;
        }
        synchronized (manager) {
            cache = manager.getCache(realName);
            if (cache == null) {
                cache = new net.sf.ehcache.Cache(realName, (int) config.getMaxSize(), false, false, 0, 0);
                manager.addCache(cache);
            }
            return cache;
        }
    }

    private String realCacheName(String cacheName) {
        return CACHE_PREFIX + cacheName;
    }
}

package io.github.macaque0.aifei.cache;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopKit;
import io.github.macaque0.aifei.cache.backend.CaffeineCache;
import io.github.macaque0.aifei.cache.backend.EhcacheCache;
import io.github.macaque0.aifei.cache.backend.MemoryCache;
import io.github.macaque0.aifei.cache.backend.RedisCache;
import cn.aifei.plugin.Plugin;

public class CachePlugin implements Plugin {

    private final CacheConfig config;
    private Cache cache;

    public CachePlugin() {
        this(loadConfig());
    }

    public CachePlugin(CacheConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        this.config = config;
    }

    @Override
    public synchronized void start() {
        if (cache != null) {
            return;
        }
        registerInjectableCache();
        cache = createCache(config);
        CacheKit.init(cache);
    }

    @Override
    public synchronized void stop() {
        Cache old = cache;
        cache = null;
        if (old != null) {
            try {
                old.close();
            } finally {
                CacheKit.clearInit(old);
            }
        } else {
            CacheKit.clearInit(null);
        }
    }

    protected Cache createCache(CacheConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        String type = config.getType() == null || config.getType().trim().isEmpty()
                ? "memory"
                : config.getType().trim().toLowerCase();
        if ("memory".equals(type) || "map".equals(type)) {
            return new MemoryCache(config);
        }
        if ("caffeine".equals(type) || "caffine".equals(type)) {
            return new CaffeineCache(config);
        }
        if ("ehcache".equals(type) || "eh".equals(type)) {
            return new EhcacheCache(config);
        }
        if ("redis".equals(type)) {
            return new RedisCache(config);
        }
        throw new IllegalArgumentException("Unsupported cache.type: " + config.getType());
    }

    private static CacheConfig loadConfig() {
        try {
            return CacheConfig.fromPropKit();
        } catch (IllegalStateException e) {
            return new CacheConfig();
        }
    }

    private static void registerInjectableCache() {
        try {
            AopKit.get().addSingletonObject(Cache.class, InjectableCache.INSTANCE);
        } catch (RuntimeException e) {
            Cache existing = currentAopCache();
            if (existing == InjectableCache.INSTANCE) {
                return;
            }
            throw new IllegalStateException("Aop singleton for Cache.class already exists", e);
        }
    }

    private static Cache currentAopCache() {
        try {
            return Aop.get(Cache.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

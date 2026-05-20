package io.github.macaque0.aifei.cache;

import java.util.function.Supplier;

public class CacheKit {

    private static volatile Cache cache;

    public static void init(Cache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("cache can not be null");
        }
        CacheKit.cache = cache;
    }

    public static Cache getCache() {
        Cache ret = cache;
        if (ret == null) {
            throw new IllegalStateException("CacheKit has not been initialized. Add CachePlugin first.");
        }
        return ret;
    }

    public static <T> T get(String key) {
        return getCache().get(key);
    }

    public static <T> T get(String cacheName, String key) {
        return getCache().get(cacheName, key);
    }

    public static void put(String key, Object value) {
        getCache().put(key, value);
    }

    public static void put(String key, Object value, long ttlSeconds) {
        getCache().put(key, value, ttlSeconds);
    }

    public static void put(String cacheName, String key, Object value) {
        getCache().put(cacheName, key, value);
    }

    public static void put(String cacheName, String key, Object value, long ttlSeconds) {
        getCache().put(cacheName, key, value, ttlSeconds);
    }

    public static <T> T getOrSet(String key, Supplier<T> supplier) {
        return getCache().getOrSet(key, supplier);
    }

    public static <T> T getOrSet(String key, long ttlSeconds, Supplier<T> supplier) {
        return getCache().getOrSet(key, ttlSeconds, supplier);
    }

    public static <T> T getOrSet(String cacheName, String key, long ttlSeconds, Supplier<T> supplier) {
        return getCache().getOrSet(cacheName, key, ttlSeconds, supplier);
    }

    public static boolean exists(String key) {
        return getCache().exists(key);
    }

    public static boolean exists(String cacheName, String key) {
        return getCache().exists(cacheName, key);
    }

    public static void remove(String key) {
        getCache().remove(key);
    }

    public static void remove(String cacheName, String key) {
        getCache().remove(cacheName, key);
    }

    public static void clear(String cacheName) {
        getCache().clear(cacheName);
    }

    public static void clearAll() {
        getCache().clearAll();
    }

    public static long incr(String key) {
        return getCache().incr(key);
    }

    public static long incr(String cacheName, String key, long delta) {
        return getCache().incr(cacheName, key, delta);
    }

    public static long decr(String key) {
        return getCache().decr(key);
    }

    public static long decr(String cacheName, String key, long delta) {
        return getCache().decr(cacheName, key, delta);
    }

    public static void clearInit() {
        cache = null;
    }

    static void clearInit(Cache expected) {
        if (cache == expected) {
            cache = null;
        }
    }
}

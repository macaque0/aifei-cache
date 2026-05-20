package io.github.macaque0.aifei.cache;

import java.util.function.Supplier;

final class InjectableCache implements Cache {

    static final InjectableCache INSTANCE = new InjectableCache();

    private InjectableCache() {
    }

    private Cache target() {
        return CacheKit.getCache();
    }

    @Override
    public <T> T get(String key) {
        return target().get(key);
    }

    @Override
    public <T> T get(String cacheName, String key) {
        return target().get(cacheName, key);
    }

    @Override
    public void put(String key, Object value) {
        target().put(key, value);
    }

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        target().put(key, value, ttlSeconds);
    }

    @Override
    public void put(String cacheName, String key, Object value) {
        target().put(cacheName, key, value);
    }

    @Override
    public void put(String cacheName, String key, Object value, long ttlSeconds) {
        target().put(cacheName, key, value, ttlSeconds);
    }

    @Override
    public <T> T getOrSet(String key, Supplier<T> supplier) {
        return target().getOrSet(key, supplier);
    }

    @Override
    public <T> T getOrSet(String key, long ttlSeconds, Supplier<T> supplier) {
        return target().getOrSet(key, ttlSeconds, supplier);
    }

    @Override
    public <T> T getOrSet(String cacheName, String key, long ttlSeconds, Supplier<T> supplier) {
        return target().getOrSet(cacheName, key, ttlSeconds, supplier);
    }

    @Override
    public boolean exists(String key) {
        return target().exists(key);
    }

    @Override
    public boolean exists(String cacheName, String key) {
        return target().exists(cacheName, key);
    }

    @Override
    public void remove(String key) {
        target().remove(key);
    }

    @Override
    public void remove(String cacheName, String key) {
        target().remove(cacheName, key);
    }

    @Override
    public void clear(String cacheName) {
        target().clear(cacheName);
    }

    @Override
    public void clearAll() {
        target().clearAll();
    }

    @Override
    public long incr(String key) {
        return target().incr(key);
    }

    @Override
    public long incr(String cacheName, String key, long delta) {
        return target().incr(cacheName, key, delta);
    }

    @Override
    public long decr(String key) {
        return target().decr(key);
    }

    @Override
    public long decr(String cacheName, String key, long delta) {
        return target().decr(cacheName, key, delta);
    }

    @Override
    public void close() {
        // Lifecycle is owned by CachePlugin. Injected Cache users should not
        // close the shared backend out from under the running application.
    }
}

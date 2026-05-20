package io.github.macaque0.aifei.cache;

import java.util.function.Supplier;

public interface Cache {

    <T> T get(String key);

    <T> T get(String cacheName, String key);

    void put(String key, Object value);

    void put(String key, Object value, long ttlSeconds);

    void put(String cacheName, String key, Object value);

    void put(String cacheName, String key, Object value, long ttlSeconds);

    <T> T getOrSet(String key, Supplier<T> supplier);

    <T> T getOrSet(String key, long ttlSeconds, Supplier<T> supplier);

    <T> T getOrSet(String cacheName, String key, long ttlSeconds, Supplier<T> supplier);

    boolean exists(String key);

    boolean exists(String cacheName, String key);

    void remove(String key);

    void remove(String cacheName, String key);

    void clear(String cacheName);

    void clearAll();

    long incr(String key);

    long incr(String cacheName, String key, long delta);

    long decr(String key);

    long decr(String cacheName, String key, long delta);

    void close();
}

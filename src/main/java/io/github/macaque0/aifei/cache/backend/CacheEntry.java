package io.github.macaque0.aifei.cache.backend;

import java.io.Serializable;

public class CacheEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Object value;
    private final long expireAtMillis;

    public CacheEntry(Object value, long ttlSeconds) {
        if (ttlSeconds < 0) {
            throw new IllegalArgumentException("ttlSeconds can not be negative");
        }
        this.value = value;
        this.expireAtMillis = ttlSeconds > 0 ? expireAtMillis(ttlSeconds) : 0;
    }

    public Object getValue() {
        return value;
    }

    public boolean isExpired() {
        return expireAtMillis > 0 && System.currentTimeMillis() >= expireAtMillis;
    }

    private long expireAtMillis(long ttlSeconds) {
        long now = System.currentTimeMillis();
        long ttlMillis;
        try {
            ttlMillis = Math.multiplyExact(ttlSeconds, 1000L);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("ttlSeconds is too large: " + ttlSeconds, e);
        }
        if (Long.MAX_VALUE - now < ttlMillis) {
            throw new IllegalArgumentException("ttlSeconds is too large: " + ttlSeconds);
        }
        return now + ttlMillis;
    }
}

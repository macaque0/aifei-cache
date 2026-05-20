package io.github.macaque0.aifei.cache;

import cn.aifei.util.PropKit;

public class CacheConfig {

    private String type = "memory";
    private String defaultName = "default";
    private long defaultTtlSeconds = 0;
    private boolean cacheNull = false;
    private String keyPrefix = "aifei";
    private long maxSize = 10000;

    private String redisHost = "127.0.0.1";
    private int redisPort = 6379;
    private String redisUser;
    private String redisPassword;
    private int redisDatabase = 0;
    private boolean redisSsl = false;
    private int redisTimeoutMillis = 2000;

    public static CacheConfig fromPropKit() {
        CacheConfig c = new CacheConfig();
        c.type = PropKit.get("cache.type", c.type);
        c.defaultName = PropKit.get("cache.defaultName", c.defaultName);
        c.defaultTtlSeconds = PropKit.getLong("cache.defaultTtlSeconds", c.defaultTtlSeconds);
        c.cacheNull = PropKit.getBoolean("cache.cacheNull", c.cacheNull);
        c.keyPrefix = PropKit.get("cache.keyPrefix", c.keyPrefix);
        c.maxSize = PropKit.getLong("cache.maxSize", c.maxSize);

        c.redisHost = PropKit.get("cache.redis.host", c.redisHost);
        c.redisPort = PropKit.getInt("cache.redis.port", c.redisPort);
        c.redisUser = blankToNull(PropKit.get("cache.redis.user", c.redisUser));
        c.redisPassword = blankToNull(PropKit.get("cache.redis.password", c.redisPassword));
        c.redisDatabase = PropKit.getInt("cache.redis.database", c.redisDatabase);
        c.redisSsl = PropKit.getBoolean("cache.redis.ssl", c.redisSsl);
        c.redisTimeoutMillis = PropKit.getInt("cache.redis.timeoutMillis", c.redisTimeoutMillis);
        return c;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public String getType() {
        return type;
    }

    public CacheConfig setType(String type) {
        this.type = type;
        return this;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public CacheConfig setDefaultName(String defaultName) {
        this.defaultName = defaultName;
        return this;
    }

    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public CacheConfig setDefaultTtlSeconds(long defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
        return this;
    }

    public boolean isCacheNull() {
        return cacheNull;
    }

    public CacheConfig setCacheNull(boolean cacheNull) {
        this.cacheNull = cacheNull;
        return this;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public CacheConfig setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
        return this;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public CacheConfig setMaxSize(long maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    public String getRedisHost() {
        return redisHost;
    }

    public CacheConfig setRedisHost(String redisHost) {
        this.redisHost = redisHost;
        return this;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public CacheConfig setRedisPort(int redisPort) {
        this.redisPort = redisPort;
        return this;
    }

    public String getRedisUser() {
        return redisUser;
    }

    public CacheConfig setRedisUser(String redisUser) {
        this.redisUser = redisUser;
        return this;
    }

    public String getRedisPassword() {
        return redisPassword;
    }

    public CacheConfig setRedisPassword(String redisPassword) {
        this.redisPassword = redisPassword;
        return this;
    }

    public int getRedisDatabase() {
        return redisDatabase;
    }

    public CacheConfig setRedisDatabase(int redisDatabase) {
        this.redisDatabase = redisDatabase;
        return this;
    }

    public boolean isRedisSsl() {
        return redisSsl;
    }

    public CacheConfig setRedisSsl(boolean redisSsl) {
        this.redisSsl = redisSsl;
        return this;
    }

    public int getRedisTimeoutMillis() {
        return redisTimeoutMillis;
    }

    public CacheConfig setRedisTimeoutMillis(int redisTimeoutMillis) {
        this.redisTimeoutMillis = redisTimeoutMillis;
        return this;
    }
}

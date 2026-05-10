# aifei-cache

Aifei cache extension. It provides a small cache API, an Aifei `Plugin`, and backend implementations for:

- `memory`
- `caffeine`
- `ehcache`
- `redis`

## Maven

```xml
<dependency>
    <groupId>cn.aifei</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Aifei config

```java
public void configPlugin(Plugins plugins) {
    plugins.add(new CachePlugin());
}
```

## app-config.txt

```properties
cache.type = caffeine
cache.defaultName = default
cache.defaultTtlSeconds = 3600
cache.cacheNull = false
cache.keyPrefix = aifei
cache.maxSize = 10000

# redis only
cache.redis.host = 127.0.0.1
cache.redis.port = 6379
cache.redis.password =
cache.redis.database = 0
cache.redis.ssl = false
cache.redis.timeoutMillis = 2000
```

## Usage

```java
CacheKit.put("user:1", user, 300);
User user = CacheKit.get("user:1");

User user = CacheKit.getOrSet("user:1", 300, () -> User.findById(1));

CacheKit.remove("user:1");
CacheKit.clear("default");
```

## Notes

- `redis` backend serializes ordinary values with JDK serialization, so custom objects must implement `Serializable`.
- Redis counters are stored as integer text and returned as `Long`.
- Keep `cache.keyPrefix` non-empty when using Redis. `clearAll()` refuses to run with an empty prefix.

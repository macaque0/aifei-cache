# aifei-cache

English | [简体中文](README.zh-CN.md)

`aifei-cache` is a small cache extension for Aifei projects. It provides:

- A unified `Cache` API.
- A static helper class: `CacheKit`.
- An Aifei `Plugin`: `CachePlugin`.
- Four backend implementations: `memory`, `caffeine`, `ehcache`, and `redis`.

The extension is intentionally lightweight. It is suitable for page data cache, query result cache, login/session helper data, rate counters, and other common application-level cache scenarios.

## Requirements

- JDK 8+
- Maven 3+
- Aifei 1.0.1 when used as an Aifei plugin

The project is compiled with Java 8 bytecode target and has been tested with JDK 8 and JDK 21.

## Maven

```xml
<dependency>
    <groupId>io.github.macaque0</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

Backend dependencies are declared as optional, so application projects only need to add the backend they use. For example, when using Ehcache only:

```xml
<dependency>
    <groupId>net.sf.ehcache</groupId>
    <artifactId>ehcache-core</artifactId>
    <version>2.6.11</version>
</dependency>
```

Current backend dependency versions:

- Caffeine `2.8.0`
- Ehcache `2.6.11`
- Jedis `3.8.0`

## Quick Start

Register the plugin in the Aifei application config:

```java
import io.github.macaque0.aifei.cache.CachePlugin;
import cn.aifei.plugin.Plugins;

public void configPlugin(Plugins plugins) {
    plugins.add(new CachePlugin());
}
```

Then use `CacheKit` anywhere after plugins have started:

```java
CacheKit.put("user:1", user, 300);
User user = CacheKit.get("user:1");

User cached = CacheKit.getOrSet("user:1", 300, () -> User.findById(1));

CacheKit.remove("user:1");
```

You can also inject `Cache` into objects created or injected by Aifei AOP:

```java
import cn.aifei.aop.Inject;
import io.github.macaque0.aifei.cache.Cache;

public class UserService {

    @Inject
    Cache cache;

    public User getUser(int id) {
        return cache.getOrSet("user:" + id, 300, () -> User.findById(id));
    }
}
```

## Configuration

Add cache settings to `app-config.txt`.

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
cache.redis.user =
cache.redis.password =
cache.redis.database = 0
cache.redis.ssl = false
cache.redis.timeoutMillis = 2000
```

Configuration fields:

| Key | Default | Description |
| --- | --- | --- |
| `cache.type` | `memory` | Backend type. Supports `memory`, `map`, `caffeine`, `caffine`, `ehcache`, `eh`, `redis`. |
| `cache.defaultName` | `default` | Default cache region name used by single-key APIs. |
| `cache.defaultTtlSeconds` | `0` | Default TTL in seconds. `0` means no expiration. |
| `cache.cacheNull` | `false` | Whether `getOrSet` should cache `null` values. |
| `cache.keyPrefix` | `aifei` | Redis key prefix. Also helps isolate integration tests and multiple apps. |
| `cache.maxSize` | `10000` | Max size for memory, Caffeine, and Ehcache backends. Must be greater than `0`. |
| `cache.redis.host` | `127.0.0.1` | Redis host. |
| `cache.redis.port` | `6379` | Redis port. |
| `cache.redis.user` | empty | Redis ACL username. Leave empty for password-only Redis. |
| `cache.redis.password` | empty | Redis password. |
| `cache.redis.database` | `0` | Redis database index. |
| `cache.redis.ssl` | `false` | Whether to connect through SSL. |
| `cache.redis.timeoutMillis` | `2000` | Redis connection/read timeout. |

## Backend Examples

Memory:

```properties
cache.type = memory
cache.maxSize = 10000
```

Caffeine:

```properties
cache.type = caffeine
cache.defaultTtlSeconds = 600
cache.maxSize = 50000
```

Ehcache:

```properties
cache.type = ehcache
cache.defaultTtlSeconds = 600
cache.maxSize = 50000
```

Redis:

```properties
cache.type = redis
cache.keyPrefix = my-app
cache.defaultTtlSeconds = 3600
cache.redis.host = 127.0.0.1
cache.redis.port = 6379
cache.redis.password = your-password
cache.redis.database = 0
```

## API

Most application code should use `CacheKit`. The single-key methods use `cache.defaultName` as the cache region. Methods with `cacheName` operate on the specified named region.

### Read

```java
String value = CacheKit.get("k");
User user = CacheKit.get("user", "1");
```

| Method | Description |
| --- | --- |
| `<T> T get(String key)` | Reads a value from the default cache region. Returns `null` when the key does not exist or has expired. |
| `<T> T get(String cacheName, String key)` | Reads a value from the specified cache region. |

The returned value is cast by the caller, so keep the key's value type stable in your application code.

### Write

```java
CacheKit.put("k", "v");
CacheKit.put("k", "v", 60);
CacheKit.put("user", "1", user);
CacheKit.put("user", "1", user, 300);
```

| Method | Description |
| --- | --- |
| `void put(String key, Object value)` | Writes a value to the default cache region using `cache.defaultTtlSeconds`. |
| `void put(String key, Object value, long ttlSeconds)` | Writes a value to the default cache region with a custom TTL in seconds. `0` means no expiration. |
| `void put(String cacheName, String key, Object value)` | Writes a value to the specified cache region using `cache.defaultTtlSeconds`. |
| `void put(String cacheName, String key, Object value, long ttlSeconds)` | Writes a value to the specified cache region with a custom TTL in seconds. |

For Redis, ordinary values are JDK-serialized. Custom objects should implement `java.io.Serializable`.

### Read-Through Cache

```java
User user = CacheKit.getOrSet("user:1", () -> User.findById(1));
User userWithTtl = CacheKit.getOrSet("user:1", 300, () -> User.findById(1));
User namedUser = CacheKit.getOrSet("user", "1", 300, () -> User.findById(1));
```

| Method | Description |
| --- | --- |
| `<T> T getOrSet(String key, Supplier<T> supplier)` | Reads from the default cache region. If missing, calls `supplier`, writes the result with `cache.defaultTtlSeconds`, and returns it. |
| `<T> T getOrSet(String key, long ttlSeconds, Supplier<T> supplier)` | Same as above, but uses a custom TTL in seconds. |
| `<T> T getOrSet(String cacheName, String key, long ttlSeconds, Supplier<T> supplier)` | Read-through cache for a named region. |

`getOrSet` uses segmented locks to reduce repeated loading for the same key under concurrency. If `supplier` returns `null`, the value is cached only when `cache.cacheNull = true`.

### Exists

```java
boolean exists = CacheKit.exists("k");
boolean userExists = CacheKit.exists("user", "1");
```

| Method | Description |
| --- | --- |
| `boolean exists(String key)` | Checks whether a non-expired key exists in the default cache region. |
| `boolean exists(String cacheName, String key)` | Checks whether a non-expired key exists in the specified cache region. |

### Remove And Clear

```java
CacheKit.remove("k");
CacheKit.remove("user", "1");
CacheKit.clear("user");
CacheKit.clearAll();
```

| Method | Description |
| --- | --- |
| `void remove(String key)` | Removes one key from the default cache region. |
| `void remove(String cacheName, String key)` | Removes one key from the specified cache region. |
| `void clear(String cacheName)` | Clears all keys in the specified cache region. |
| `void clearAll()` | Clears all cache data controlled by the current backend instance. |

For Redis, `clearAll()` requires a non-empty `cache.keyPrefix` and only deletes keys under that prefix.

### Counters

```java
long n = CacheKit.incr("sms:send:18800000000");
long stock = CacheKit.decr("stock", "sku-1001", 1);
```

| Method | Description |
| --- | --- |
| `long incr(String key)` | Increments a numeric value in the default cache region by `1`. Missing keys start from `0`. |
| `long incr(String cacheName, String key, long delta)` | Increments a numeric value in the specified cache region by `delta`. |
| `long decr(String key)` | Decrements a numeric value in the default cache region by `1`. Missing keys start from `0`. |
| `long decr(String cacheName, String key, long delta)` | Decrements a numeric value in the specified cache region by `delta`. |

Counter methods return the new value after the operation. Counter keys use `cache.defaultTtlSeconds` as their TTL. Calling `incr/decr` on a non-numeric value throws an exception. Redis counters are stored as integer text and returned as `Long`.

### Lifecycle And Advanced Access

```java
Cache cache = CacheKit.getCache();
CacheKit.init(cache);
CacheKit.clearInit();
```

| Method | Description |
| --- | --- |
| `Cache getCache()` | Returns the current cache instance. Throws an exception if `CacheKit` has not been initialized. |
| `void init(Cache cache)` | Sets the current cache instance. Normally called by `CachePlugin.start()`. |
| `void clearInit()` | Clears the static cache reference. Normally called by `CachePlugin.stop()`. |
| `void close()` | Method on the `Cache` instance, used to release backend resources such as Redis connection pools. Normally called by `CachePlugin.stop()`. |

In normal Aifei applications, prefer `CachePlugin` to manage `init`, `clearInit`, and `close`.

## Manual Initialization

In normal Aifei projects, prefer `CachePlugin`. For tests or standalone usage, initialize manually:

```java
import io.github.macaque0.aifei.cache.Cache;
import io.github.macaque0.aifei.cache.CacheConfig;
import io.github.macaque0.aifei.cache.CacheKit;
import io.github.macaque0.aifei.cache.backend.CaffeineCache;

CacheConfig config = new CacheConfig()
        .setType("caffeine")
        .setDefaultName("default")
        .setDefaultTtlSeconds(300);

Cache cache = new CaffeineCache(config);
CacheKit.init(cache);
```

For application code outside the plugin lifecycle, instantiate the concrete backend directly only when necessary.

## Behavior Notes

- `getOrSet` uses a small segmented lock to reduce cache stampede for the same key.
- `cache.cacheNull = true` allows `getOrSet` to cache `null` values.
- After `CachePlugin` starts, it also registers an Aifei AOP singleton, so both `CacheKit.xxx` and `@Inject Cache cache` are supported.
- The lifecycle of an injected `Cache` is managed by `CachePlugin`; application code should not call `close()` on it.
- `cacheName` and `key` must not be blank, TTL must not be negative, and `cache.maxSize` must be greater than `0`.
- Redis serializes ordinary values with JDK serialization, so custom objects must implement `java.io.Serializable`.
- Redis counters are stored as integer text and returned as `Long`.
- After running `incr/decr` on a numeric Redis value, later `get` returns a `Long`.
- Calling `incr/decr` on a non-numeric value throws an exception instead of overwriting it.
- Avoid calling `clearAll()` in shared Redis databases unless `cache.keyPrefix` is unique for the current app.

## Tests

Run regular tests:

```bash
mvn test
```

Regular tests cover:

- `memory`
- `caffeine`
- `ehcache`

Redis integration test is skipped by default because it needs an external Redis server. Run it explicitly with:

```bash
mvn "-Dtest=RedisCacheIntegrationTest" "-Dredis.integration=true" "-Dredis.host=127.0.0.1" "-Dredis.port=6379" "-Dredis.password=your-password" "-Dredis.database=0" test
```

If Redis uses ACL username/password:

```bash
mvn "-Dtest=RedisCacheIntegrationTest" "-Dredis.integration=true" "-Dredis.host=127.0.0.1" "-Dredis.port=6379" "-Dredis.user=default" "-Dredis.password=your-password" "-Dredis.database=0" test
```

Tested status:

- `memory`: passed
- `caffeine`: passed
- `ehcache`: passed
- `redis`: integration test is skipped by default; enable it manually with a real Redis server

## Build

Install to the local Maven repository:

```bash
mvn -DskipTests install
```

The built jar and sources jar are generated under `target/`.

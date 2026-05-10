# aifei-cache

`aifei-cache` is a small cache extension for Aifei projects. It provides:

- A unified `Cache` API.
- A static helper class: `CacheKit`.
- An Aifei `Plugin`: `CachePlugin`.
- Four backend implementations: `memory`, `caffeine`, `ehcache`, and `redis`.

The extension is intentionally lightweight. It is suitable for page data cache, query result cache, login/session helper data, rate counters, and other common application-level cache scenarios.

## Requirements

- JDK 8+
- Maven 3+
- Aifei 1.0.0 when used as an Aifei plugin

## Maven

```xml
<dependency>
    <groupId>cn.aifei</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

Current bundled backend dependencies:

- Caffeine `2.8.0`
- Ehcache `2.6.11`
- Jedis `3.8.0`

## Quick Start

Register the plugin in the Aifei application config:

```java
import cn.aifei.cache.CachePlugin;
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
| `cache.maxSize` | `10000` | Max size hint for memory, Caffeine, and Ehcache backends. |
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

Use the default cache region:

```java
CacheKit.put("k", "v");
CacheKit.put("k", "v", 60);

String value = CacheKit.get("k");
boolean exists = CacheKit.exists("k");

CacheKit.remove("k");
```

Use a named cache region:

```java
CacheKit.put("user", "1", user, 300);
User user = CacheKit.get("user", "1");

CacheKit.remove("user", "1");
CacheKit.clear("user");
```

Use read-through cache:

```java
User user = CacheKit.getOrSet("user:1", 300, () -> User.findById(1));
```

Use counters:

```java
long n = CacheKit.incr("sms:send:18800000000");
long left = CacheKit.decr("stock", "sku-1001", 1);
```

Clear all cache data controlled by the current backend:

```java
CacheKit.clearAll();
```

For Redis, `clearAll()` requires a non-empty `cache.keyPrefix` and only deletes keys under that prefix.

## Manual Initialization

In normal Aifei projects, prefer `CachePlugin`. For tests or standalone usage, initialize manually:

```java
import cn.aifei.cache.Cache;
import cn.aifei.cache.CacheConfig;
import cn.aifei.cache.CacheKit;
import cn.aifei.cache.backend.CaffeineCache;

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
- Redis serializes ordinary values with JDK serialization, so custom objects must implement `java.io.Serializable`.
- Redis counters are stored as integer text and returned as `Long`.
- After running `incr/decr` on a numeric Redis value, later `get` returns a `Long`.
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
- `redis`: passed with a real Redis connection

## Build

Install to the local Maven repository:

```bash
mvn -DskipTests install
```

The built jar and sources jar are generated under `target/`.

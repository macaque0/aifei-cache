# aifei-cache 中文说明

[English](README.md) | 简体中文

`aifei-cache` 是面向 Aifei 项目的轻量级缓存扩展，提供统一缓存 API 和多个后端实现。

它包含：

- 统一的 `Cache` 接口
- 静态工具类 `CacheKit`
- Aifei 插件 `CachePlugin`
- 四种缓存后端：`memory`、`caffeine`、`ehcache`、`redis`

适合用于页面数据缓存、查询结果缓存、登录/会话辅助数据、验证码/短信发送计数、库存/限流计数等常见业务场景。

## 环境要求

- JDK 8+
- Maven 3+
- 如果作为 Aifei 插件使用，需要 Aifei 1.0.1

项目以 Java 8 字节码目标编译，已在 JDK 8 和 JDK 21 下测试通过。

## Maven 依赖

```xml
<dependency>
    <groupId>io.github.macaque0</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

缓存后端依赖已声明为 optional，业务项目只需要额外添加实际使用的后端依赖。例如只用 Ehcache：

```xml
<dependency>
    <groupId>net.sf.ehcache</groupId>
    <artifactId>ehcache-core</artifactId>
    <version>2.6.11</version>
</dependency>
```

当前后端依赖版本：

- Caffeine `2.8.0`
- Ehcache `2.6.11`
- Jedis `3.8.0`

## 快速接入

在 Aifei 应用配置中注册插件：

```java
import io.github.macaque0.aifei.cache.CachePlugin;
import cn.aifei.plugin.Plugins;

public void configPlugin(Plugins plugins) {
    plugins.add(new CachePlugin());
}
```

插件启动后，即可通过 `CacheKit` 使用缓存：

```java
CacheKit.put("user:1", user, 300);
User user = CacheKit.get("user:1");

User cached = CacheKit.getOrSet("user:1", 300, () -> User.findById(1));

CacheKit.remove("user:1");
```

也可以在由 Aifei AOP 创建或注入的业务对象中直接注入 `Cache`：

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

## 配置说明

在 `app-config.txt` 中增加缓存配置：

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

配置项说明：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `cache.type` | `memory` | 缓存后端类型，支持 `memory`、`map`、`caffeine`、`caffine`、`ehcache`、`eh`、`redis`。 |
| `cache.defaultName` | `default` | 默认缓存区域名称，单 key API 会使用该区域。 |
| `cache.defaultTtlSeconds` | `0` | 默认过期时间，单位秒。`0` 表示不过期。 |
| `cache.cacheNull` | `false` | `getOrSet` 是否缓存 `null` 值。 |
| `cache.keyPrefix` | `aifei` | Redis key 前缀，用于隔离不同应用或测试数据。 |
| `cache.maxSize` | `10000` | `memory`、`caffeine`、`ehcache` 后端的最大容量，必须大于 `0`。 |
| `cache.redis.host` | `127.0.0.1` | Redis 主机地址。 |
| `cache.redis.port` | `6379` | Redis 端口。 |
| `cache.redis.user` | 空 | Redis ACL 用户名。普通密码模式可留空。 |
| `cache.redis.password` | 空 | Redis 密码。 |
| `cache.redis.database` | `0` | Redis 数据库索引。 |
| `cache.redis.ssl` | `false` | 是否使用 SSL 连接 Redis。 |
| `cache.redis.timeoutMillis` | `2000` | Redis 连接和读取超时时间，单位毫秒。 |

## 后端配置示例

内存缓存：

```properties
cache.type = memory
cache.maxSize = 10000
```

Caffeine：

```properties
cache.type = caffeine
cache.defaultTtlSeconds = 600
cache.maxSize = 50000
```

Ehcache：

```properties
cache.type = ehcache
cache.defaultTtlSeconds = 600
cache.maxSize = 50000
```

Redis：

```properties
cache.type = redis
cache.keyPrefix = my-app
cache.defaultTtlSeconds = 3600
cache.redis.host = 127.0.0.1
cache.redis.port = 6379
cache.redis.password = your-password
cache.redis.database = 0
```

## 常用 API

业务代码优先使用 `CacheKit`。只传 `key` 的方法会使用 `cache.defaultName` 指定的默认缓存区域；带 `cacheName` 的方法会操作指定的命名缓存区域。

### 读取

```java
String value = CacheKit.get("k");
User user = CacheKit.get("user", "1");
```

| 方法 | 说明 |
| --- | --- |
| `<T> T get(String key)` | 从默认缓存区域读取值。key 不存在或已过期时返回 `null`。 |
| `<T> T get(String cacheName, String key)` | 从指定缓存区域读取值。 |

返回值由调用方进行类型转换，因此同一个 key 在业务代码里应保持稳定的值类型。

### 写入

```java
CacheKit.put("k", "v");
CacheKit.put("k", "v", 60);
CacheKit.put("user", "1", user);
CacheKit.put("user", "1", user, 300);
```

| 方法 | 说明 |
| --- | --- |
| `void put(String key, Object value)` | 写入默认缓存区域，过期时间使用 `cache.defaultTtlSeconds`。 |
| `void put(String key, Object value, long ttlSeconds)` | 写入默认缓存区域，并指定过期时间，单位秒。`0` 表示不过期。 |
| `void put(String cacheName, String key, Object value)` | 写入指定缓存区域，过期时间使用 `cache.defaultTtlSeconds`。 |
| `void put(String cacheName, String key, Object value, long ttlSeconds)` | 写入指定缓存区域，并指定过期时间，单位秒。 |

Redis 后端对普通对象使用 JDK 序列化，自定义对象需要实现 `java.io.Serializable`。

### 读穿缓存

```java
User user = CacheKit.getOrSet("user:1", () -> User.findById(1));
User userWithTtl = CacheKit.getOrSet("user:1", 300, () -> User.findById(1));
User namedUser = CacheKit.getOrSet("user", "1", 300, () -> User.findById(1));
```

| 方法 | 说明 |
| --- | --- |
| `<T> T getOrSet(String key, Supplier<T> supplier)` | 先从默认缓存区域读取。不存在时执行 `supplier`，将结果按 `cache.defaultTtlSeconds` 写入缓存，并返回结果。 |
| `<T> T getOrSet(String key, long ttlSeconds, Supplier<T> supplier)` | 与上面相同，但使用指定过期时间，单位秒。 |
| `<T> T getOrSet(String cacheName, String key, long ttlSeconds, Supplier<T> supplier)` | 指定缓存区域的读穿缓存。 |

`getOrSet` 内部使用分段锁，减少高并发下同一个 key 的重复加载。`supplier` 返回 `null` 时，只有 `cache.cacheNull = true` 才会缓存 `null`。

### 判断是否存在

```java
boolean exists = CacheKit.exists("k");
boolean userExists = CacheKit.exists("user", "1");
```

| 方法 | 说明 |
| --- | --- |
| `boolean exists(String key)` | 判断默认缓存区域中是否存在未过期的 key。 |
| `boolean exists(String cacheName, String key)` | 判断指定缓存区域中是否存在未过期的 key。 |

### 删除和清理

```java
CacheKit.remove("k");
CacheKit.remove("user", "1");
CacheKit.clear("user");
CacheKit.clearAll();
```

| 方法 | 说明 |
| --- | --- |
| `void remove(String key)` | 从默认缓存区域删除单个 key。 |
| `void remove(String cacheName, String key)` | 从指定缓存区域删除单个 key。 |
| `void clear(String cacheName)` | 清空指定缓存区域中的全部 key。 |
| `void clearAll()` | 清空当前后端实例控制的全部缓存数据。 |

使用 Redis 后端时，`clearAll()` 要求 `cache.keyPrefix` 非空，并且只会删除该前缀下的 key。

### 计数器

```java
long n = CacheKit.incr("sms:send:18800000000");
long stock = CacheKit.decr("stock", "sku-1001", 1);
```

| 方法 | 说明 |
| --- | --- |
| `long incr(String key)` | 将默认缓存区域中的数字值加 `1`。key 不存在时从 `0` 开始。 |
| `long incr(String cacheName, String key, long delta)` | 将指定缓存区域中的数字值增加 `delta`。 |
| `long decr(String key)` | 将默认缓存区域中的数字值减 `1`。key 不存在时从 `0` 开始。 |
| `long decr(String cacheName, String key, long delta)` | 将指定缓存区域中的数字值减少 `delta`。 |

计数器方法返回操作后的新值。计数器 key 使用 `cache.defaultTtlSeconds` 作为过期时间；对非数字值执行 `incr/decr` 会抛出异常。Redis 计数器以整数字符串存储，读取结果类型为 `Long`。

### 生命周期和高级访问

```java
Cache cache = CacheKit.getCache();
CacheKit.init(cache);
CacheKit.clearInit();
```

| 方法 | 说明 |
| --- | --- |
| `Cache getCache()` | 获取当前缓存实例。`CacheKit` 未初始化时会抛出异常。 |
| `void init(Cache cache)` | 设置当前缓存实例。通常由 `CachePlugin.start()` 调用。 |
| `void clearInit()` | 清空静态缓存实例引用。通常由 `CachePlugin.stop()` 调用。 |
| `void close()` | `Cache` 实例方法，用于释放 Redis 连接池等后端资源。通常由 `CachePlugin.stop()` 调用。 |

普通 Aifei 应用中，建议交给 `CachePlugin` 管理 `init`、`clearInit` 和 `close`。

## 手动初始化

普通 Aifei 项目推荐使用 `CachePlugin`。在测试或独立工具中，也可以手动初始化：

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

业务项目中如无特殊原因，建议交给 `CachePlugin` 管理生命周期。

## 行为说明

- `getOrSet` 内部使用少量分段锁，减少同一个 key 在高并发下的缓存击穿。
- `cache.cacheNull = true` 时，`getOrSet` 可以缓存 `null` 值。
- `CachePlugin` 启动后会同时注册 Aifei AOP 单例，因此既可以使用 `CacheKit.xxx`，也可以使用 `@Inject Cache cache`。
- 注入得到的 `Cache` 生命周期由 `CachePlugin` 托管，业务代码无需也不应主动调用 `close()`。
- `cacheName`、`key` 不能为空，TTL 不能小于 `0`，`cache.maxSize` 必须大于 `0`。
- Redis 后端对普通对象使用 JDK 序列化，因此自定义对象需要实现 `java.io.Serializable`。
- Redis 计数器以整数字符串存储，读取结果类型为 `Long`。
- 对 Redis 中的数字值执行 `incr/decr` 后，再通过 `get` 读取会得到 `Long`。
- 对非数字值执行 `incr/decr` 会抛出异常，避免不同后端之间出现隐式覆盖行为。
- 共享 Redis 数据库中使用 `clearAll()` 要非常谨慎，务必为每个应用配置独立且非空的 `cache.keyPrefix`。

## 测试

运行常规测试：

```bash
mvn test
```

常规测试覆盖：

- `memory`
- `caffeine`
- `ehcache`

Redis 集成测试默认跳过，因为它依赖外部 Redis 服务。需要测试 Redis 时手动执行：

```bash
mvn "-Dtest=RedisCacheIntegrationTest" "-Dredis.integration=true" "-Dredis.host=127.0.0.1" "-Dredis.port=6379" "-Dredis.password=your-password" "-Dredis.database=0" test
```

如果 Redis 使用 ACL 用户名和密码：

```bash
mvn "-Dtest=RedisCacheIntegrationTest" "-Dredis.integration=true" "-Dredis.host=127.0.0.1" "-Dredis.port=6379" "-Dredis.user=default" "-Dredis.password=your-password" "-Dredis.database=0" test
```

当前测试状态：

- `memory`：已通过
- `caffeine`：已通过
- `ehcache`：已通过
- `redis`：集成测试默认跳过；需要真实 Redis 时按上方命令手动开启

## 构建

安装到本地 Maven 仓库：

```bash
mvn -DskipTests install
```

构建产物和源码包会生成在 `target/` 目录下。

# aifei-cache 中文说明

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
- 如果作为 Aifei 插件使用，需要 Aifei 1.0.0

## Maven 依赖

```xml
<dependency>
    <groupId>cn.aifei</groupId>
    <artifactId>aifei-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

当前内置后端依赖版本：

- Caffeine `2.8.0`
- Ehcache `2.6.11`
- Jedis `3.8.0`

## 快速接入

在 Aifei 应用配置中注册插件：

```java
import cn.aifei.cache.CachePlugin;
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
| `cache.maxSize` | `10000` | `memory`、`caffeine`、`ehcache` 后端的最大容量提示。 |
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

使用默认缓存区域：

```java
CacheKit.put("k", "v");
CacheKit.put("k", "v", 60);

String value = CacheKit.get("k");
boolean exists = CacheKit.exists("k");

CacheKit.remove("k");
```

使用命名缓存区域：

```java
CacheKit.put("user", "1", user, 300);
User user = CacheKit.get("user", "1");

CacheKit.remove("user", "1");
CacheKit.clear("user");
```

读穿缓存，也就是没有缓存时执行加载逻辑并写入缓存：

```java
User user = CacheKit.getOrSet("user:1", 300, () -> User.findById(1));
```

计数器：

```java
long n = CacheKit.incr("sms:send:18800000000");
long left = CacheKit.decr("stock", "sku-1001", 1);
```

清理当前后端控制的全部缓存数据：

```java
CacheKit.clearAll();
```

使用 Redis 后端时，`clearAll()` 要求 `cache.keyPrefix` 非空，并且只会删除该前缀下的 key。

## 手动初始化

普通 Aifei 项目推荐使用 `CachePlugin`。在测试或独立工具中，也可以手动初始化：

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

业务项目中如无特殊原因，建议交给 `CachePlugin` 管理生命周期。

## 行为说明

- `getOrSet` 内部使用少量分段锁，减少同一个 key 在高并发下的缓存击穿。
- `cache.cacheNull = true` 时，`getOrSet` 可以缓存 `null` 值。
- Redis 后端对普通对象使用 JDK 序列化，因此自定义对象需要实现 `java.io.Serializable`。
- Redis 计数器以整数字符串存储，读取结果类型为 `Long`。
- 对 Redis 中的数字值执行 `incr/decr` 后，再通过 `get` 读取会得到 `Long`。
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
- `redis`：已使用真实 Redis 连接测试通过

## 构建

安装到本地 Maven 仓库：

```bash
mvn -DskipTests install
```

构建产物和源码包会生成在 `target/` 目录下。

# Redis 读写

依赖 `kset-starter-redis`。注入 `KsetRedisService`，或用静态 `KsetRedis`。值按普通 JSON 字符串存，不写 `@type` / `@class`。

分布式锁见 [redis-lock.md](redis-lock.md)。作 Cache L2 见 [cache.md](cache.md)。

## 最小配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
kset:
  redis:
    key-prefix: "myapp:"
    default-ttl: 30m
    max-ttl: 7d
```

## 写入必须带 TTL

未显式传 TTL 时用 `kset.redis.default-ttl`（默认 30 分钟）。`Duration.ZERO` 或负数会抛错，禁止永久 key。

```java
redisService.setEx("order:" + order.getId(), order, Duration.ofMinutes(30));
Order cached = redisService.get("order:" + orderId, Order.class);
KsetRedis.of("cache").setEx("item:" + id, item, Duration.ofHours(1));
```

## Key

段之间用 `:`，段内不要包含 `:`。与 `kset.redis.key-prefix` 组合时 Template 会自动加前缀。

```java
String userKey = KsetRedisKeys.cache("myapp", "user", "profile", userId);
// myapp:cache:user:profile:1001
```

## 高危操作

不要用 `KEYS` 或一次性拉大 Hash/Set。用流式 API：

```java
redisService.scanKeys("order:*", keys -> redisService.deleteAll(keys));
redisService.hScan("user:100", entry -> process(entry));
```

排行榜、多数据源、连接定制见 [kset-starter-redis/README.md](../../kset-starter-redis/README.md)。

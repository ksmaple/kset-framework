# KSet Redis 统一抽象

依赖 `kset-spring-boot-starter-redis` 后，可使用 **`KsetRedisService`（注入）** 与 **`KsetRedis`（静态）** 操作 Redis；可选 **Redisson** 提供分布式锁与统一 Jackson 编解码。

| 包 | 说明 |
|----|------|
| `com.kset.redis.core` | `KsetRedisOperations`、`KsetRedisService`、`KsetRedisRegistry`、`KsetRedis` |
| `com.kset.redis.lock` | 分布式锁（Redisson） |
| `com.kset.redis.rank` | 排行榜（ZSET 单榜 / 分组二级榜） |
| `com.kset.redis.key` | Key 规范与生成（`:` 分隔） |
| `com.kset.redis.autoconfigure` | Spring Boot 自动配置 |

## Redis Key 规范（`:` 分隔）

对齐 cache-spec **K001**：`{system}:{module}:{business}:{identifier}`。统一使用 [`KsetRedisKeys`](kset-spring-boot-starter-redis/src/main/java/com/kset/redis/key/KsetRedisKeys.java)：

- 每段非空，**段内不得包含 `:`**（多段用 `join` / `builder`）
- 与 `kset.redis.key-prefix` 组合：`KsetRedisKeys.joinPrefix(prefix, logicalKey)`（Template 序列化已内置）
- 领域快捷：`cache` / `rank` / `lock`；常量段见 `KsetRedisKeyNamespace`

```java
// 缓存
String userKey = KsetRedisKeys.cache("myapp", "user", "profile", userId);
// myapp:cache:user:profile:1001

// 流式 builder
String key = KsetRedisKeys.builder("myapp")
        .segment("order").segment("detail").id(orderId)
        .build();

// 排行榜榜根（boardId 为单段，不含 ':'）
String boardKey = KsetRedisKeys.rank("myapp", "season-1");
// boardId 需含 ':' 时：KsetRedisRankOptions.builder("arena:2025-w20") 由 joinPrefix 拼接，或 .redisKey("myapp:rank:arena:2025-w20")

// 逻辑 key + 全局前缀（业务代码只写后缀时）
redis.set("cache:user:" + id, value);  // Template 自动 joinPrefix
```

## 强制 TTL（禁止永久 key）

所有缓存写入必须带有效期：

- 配置 **`kset.redis.default-ttl`**（默认 `30m`），未显式传 `ttl` 的 `set` / `hSet` / `lPush` / `sAdd` / `setIfAbsent` 等均使用该默认值。
- 可选 **`kset.redis.max-ttl`** 限制单次写入最大过期时间。
- 传入 `Duration.ZERO` 或负数将抛出异常。

```yaml
kset:
  redis:
    default-ttl: 30m
    max-ttl: 7d
```

## 高危操作：流式 / 分批

| 场景 | 推荐 API | 说明 |
|------|----------|------|
| 按模式删 key | `deleteByPattern` | SCAN + 分批 UNLINK/DEL，不用 KEYS |
| 遍历 key | `scanKeys(pattern, consumer)` | 流式回调，不一次性加载 |
| 大 Hash | `hScan` | 替代 `hGetAll`（已 `@Deprecated`） |
| 大 Set | `sScan` | 替代 `sMembers`（已 `@Deprecated`） |
| 大批量读 | `mgetChunked` | 按 `kset.redis.stream.mget-chunk-size` 分块 |

```yaml
kset:
  redis:
    stream:
      scan-batch-size: 500
      delete-batch-size: 500
      mget-chunk-size: 100
      hash-scan-count: 100
      use-unlink: true
```

## Redisson 分布式锁（`com.kset.redis.lock`）

锁**仅基于 Redisson**，须 `kset.redis.redisson.enabled=true`。缓存用 `KsetRedisService`；锁用 **`KsetRedisLockExecutor`**、**`KsetRedisLocks`** 或 **`@KsetLocked`**。

| 层级 | 说明 |
|------|------|
| `com.kset.redis.lock` | `KsetRedisLockExecutor`、`KsetRedisLock`、`KsetRedisLockOptions` |
| `com.kset.redis.lock.annotation` | `@KsetLocked` 方法级注解锁 |
| `com.kset.redis.lock.aop` | 切面与启动校验（内部） |
| `com.kset.redis.lock.internal` | Redisson 实现（内部） |

| 模式 | API | 行为 |
|------|-----|------|
| 互斥拒绝 | `runExclusive` | 立即获取，失败 `KsetRedisLockBusyException` |
| 等待后失败 | `runWithWait` | 超时 `KsetRedisLockTimeoutException` |
| 可选 | `callIfLock` / `tryAcquire` | 失败返回 empty |
| 阻塞 | `runBlocking` | Redisson 阻塞直到获取 |
| 多锁 | `runExclusiveAll` / `acquireAll` | MultiLock |
| 跨方法 | `acquire` 返回 `KsetRedisLock` | 在多个方法间传递句柄 |

```java
// 跨方法
KsetRedisLock lock = lockExecutor.acquire("order:1", KsetRedisLockOptions.rejectNow(Duration.ofMinutes(2)));
try {
    stepA();
    stepB(lock); // 同一锁句柄
} finally {
    lock.unlock();
}

// 多锁
lockExecutor.runExclusiveAll(List.of("acct:1", "acct:2"), Duration.ofSeconds(30), () -> transfer());

// Options
lockExecutor.run("job", KsetRedisLockOptions.waitThenFail(Duration.ofSeconds(3), Duration.ofMinutes(5)), () -> { });
```

### 方法级注解锁 `@KsetLocked`

```yaml
kset:
  redis:
    redisson:
      enabled: true
    lock:
      annotation-enabled: true   # 默认 true（需 classpath 含 AOP）
      validate-targets: true     # 启动时 WARN 不可织入场景
```

```java
@Service
public class OrderSyncService {

    @KsetLocked(value = "'order:sync'", lease = "5m")
    public void syncNow() {
        // 互斥拒绝
    }

    @KsetLocked(value = "'order:' + #orderId", strategy = WAIT_THEN_FAIL, waitTime = "3s", lease = "2m")
    public void syncOne(Long orderId) {
    }

    @KsetLocked(keys = {"'acct:' + #from", "'acct:' + #to"}, lease = "30s")
    public void transfer(Long from, Long to) {
    }
}
```

**注解不生效（务必避免）**

| 场景 | 说明 |
|------|------|
| 同类自调用 | `this.syncNow()` 不经过代理；改注入自身或拆 Bean |
| 非 public 方法 | AOP 无法拦截 |
| static 方法 | 无法加锁 |
| 未开 Redisson / 未注册切面 | 检查 `redisson.enabled` 与 `lock.annotation-enabled` |

跨方法、复杂流程请用 `KsetRedisLockExecutor.acquire(...)`。

## 批量缓存示例

```java
// 批量读（Map 与 keys 顺序对齐，未命中为 null）
Map<String, Order> orders = redis.multiGet(orderIds, Order.class);

// 批量写（统一 TTL，Pipeline 分块）
redis.multiSet(Map.of(
        "order:1", order1,
        "order:2", order2
), Duration.ofMinutes(30));

// 批量删 / 续期
redis.deleteAll(List.of("order:1", "order:2"));
redis.expireAll(activeKeys, Duration.ofHours(1));

// Hash 批量
redis.hSetAll("user:100", Map.of("name", "Tom", "level", 3));
Map<String, String> profile = redis.hMGet("user:100", List.of("name", "level"), String.class);
```

## 快速用法

```java
@Service
public class OrderCacheService {
    private final KsetRedisService redis;

    public OrderCacheService(KsetRedisService redis) {
        this.redis = redis;
    }

    public void put(Order order) {
        redis.setEx("order:" + order.getId(), order, Duration.ofMinutes(30));
    }
}
```

## 配置摘要

### Primary

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
```

### 多数据源

```yaml
kset:
  redis:
    default-ttl: 30m
    sources:
      cache:
        host: redis-cache
        port: 6379
        key-prefix: "myapp:cache:"
      session:
        cluster:
          enabled: true
          nodes:
            - redis-1:6379
            - redis-2:6379
        key-prefix: "myapp:session:"
```

- 注入：`@Qualifier("cacheKsetRedisService") KsetRedisService cacheRedis`
- 静态：`KsetRedis.of("cache")`
- 手动覆盖：`registry.register("custom", KsetRedisService.from(template, ttlPolicy, streamSettings))`（同名覆盖自动配置）

```java
@Component
public class CustomRedisRegistrar implements InitializingBean {
    private final KsetRedisRegistry registry;
    private final RedisTemplate<String, Object> customTemplate;

    @Override
    public void afterPropertiesSet() {
        registry.register("custom", KsetRedisService.from("custom", customTemplate, ...));
    }
}
```

### 集群

- Primary：`spring.data.redis.cluster.nodes`
- 命名源：`kset.redis.sources.{name}.cluster.enabled` + `nodes`
- `mget` / 事务需注意 hash slot；大 key 请用流式 API。

## 排行榜（`com.kset.redis.rank`）

基于 Redis **ZSET**，注入 `KsetRedisRankService`；**榜单参数均在代码中设置**（无 `kset.redis.rank` YAML）。

```java
@Bean
KsetRedisRankService rankService(RedisTemplate<String, Object> redisTemplate) {
    return KsetRedisRankService.builder(redisTemplate)
            .keyPrefix("myapp:rank:")   // 可选，默认 kset:rank:
            .build();
}
```

### 基础单榜

| 能力 | API |
|------|-----|
| TopN（分值最高在前） | `board.top(n)` / `board.range(startRank, count)` |
| 成员名次 | `board.rankOf(member)`（1-based） |
| 累加分 | `board.increment(member, delta)` |
| 排名变化通知 | `increment(..., listener)` 或 `board.addListener(...)` |
| 覆盖分 / 移除 | `setScore`, `remove` |

```java
@Service
public class ArenaRankService {
    private final KsetRedisRankService rankService;

    private KsetRedisRankBoard arenaBoard() {
        return rankService.board(KsetRedisRankOptions.builder("arena:2025-w20")
                .ttl(Duration.ofDays(7))
                .order(KsetRedisRankOrder.HIGH_SCORE_FIRST)
                .build());
    }

    public List<KsetRedisRankEntry> hallOfFame() {
        return arenaBoard().top(100);
    }

    public void addScore(String userId, double points) {
        arenaBoard().increment(userId, points, event -> {
            if (event.rankChanged()) {
                // 推送名次变化（建议异步）
            }
        });
    }
}
```

简写：`rankService.board("arena:2025-w20")` 或 `board(id, order, ttl)`（使用工厂 `keyPrefix`，无 TTL/排序时需传 Options）。

### 分组二级榜

一级：**分组总贡献**；二级：**组内成员贡献**。`contribute(groupId, memberId, delta)` 同时更新两层。

```java
KsetRedisGroupRankBoard guildBoard = rankService.groupBoard(
        KsetRedisRankOptions.builder("guild-war:s1")
                .redisKey("myapp:rank:guild-war:s1")  // 榜根 key；子 key 为 :groups、:g:{groupId}:members
                .ttl(Duration.ofDays(14))
                .build());
guildBoard.contribute("guild-1", "player-9", 50, event -> {
    if (event.changeType() == KsetRedisGroupRankChangeType.GROUP) {
        // 公会总榜名次变化
    } else {
        // 组内个人名次变化
    }
});

List<KsetRedisRankEntry> topGuilds = guildBoard.topGroups(10);
List<KsetRedisRankEntry> mvpInGuild = guildBoard.topMembers("guild-1", 5);
Optional<Integer> myRank = guildBoard.memberRankInGroup("guild-1", "player-9");
```

### 后续可扩展（当前未内置）

| 方向 | 说明 |
|------|------|
| 多赛季/多榜 | 已支持 `board("不同 boardId")`，可按业务封装赛季枚举 |
| 并列名次 | 当前为 Redis 连续名次；需「并列跳号」可在业务层二次计算 |
| 异步通知 | 监听器内发 MQ / Redis Stream，避免阻塞写路径 |
| Lua 原子排名 | 高并发下可用脚本合并「读旧名次 + 加分 + 读新名次」 |
| 成员换组 | 需业务迁移分值（从旧组扣减、新组增加） |

## 常用 API

| 分类 | 方法 |
|------|------|
| String | `get`, `set`（默认 TTL）, `setEx`, `setIfAbsent`, `delete`, `increment`+ttl |
| 批量 | `multiGet`, `multiSet`, `deleteAll`, `existsAll`, `expireAll`, `mget`/`mgetChunked` |
| Hash 批量 | `hSetAll`, `hMGet` |
| 流式 | `scanKeys`, `deleteByPattern`, `hScan`, `sScan`, `mgetChunked` |
| 锁 | `KsetRedisLockExecutor` / `KsetRedisLocks` / `@KsetLocked`（仅 Redisson） |
| 排行榜 | `KsetRedisRankService` → `board` / `groupBoard` |
| 高级 | `template()` |

# Redis 业务 API 边界变更记录

- feature-key：`redis-business-api-boundary`
- 当前 change-id：`redis-business-api-boundary-v1`
- 当前状态：`active`
- 当前入口：`kset-starter-redis/src/main/java/com/kset/redis/core/KsetRedisOperations.java#KsetRedisOperations`、`kset-starter-redis/src/main/java/com/kset/redis/lock/KsetRedisLockExecutor.java#KsetRedisLockExecutor`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| redis-business-api-boundary-v1 | active | `KsetRedisOperations`、`KsetRedisLockExecutor` | 两个底层对象出口 | `a98f3ecdc9cf2989689a0eeb4627cd1efde42d64` | 可用 | N/A |

## redis-business-api-boundary-v1

- 日期：2026-10-16
- 目标：移除业务高层 API 中未被生产代码调用的 Spring Data Redis 和 Redisson 适配对象出口。
- 判定时间：2026-10-16 10:44:47 +08:00
- 上线状态：待确认；公开方法存在于 `v1.0.16` 发布版本，未取得生产部署证据。
- 判定方式：版本标签与发布记录核对；直接替换及外部调用方迁移由用户确认。
- 判定证据：`v1.0.16`、`CHANGELOG.md`；仓内只有测试调用 `template()`，未发现 `provider()` 调用。
- 证据来源：Git tag `v1.0.16`、`CHANGELOG.md`、本仓源码与用户确认。
- 当时代码状态：`KsetRedisOperations#template` 返回 `RedisTemplate`，`KsetRedisService` 和 `KsetRedisTemplateOperations` 委托/实现该方法；`KsetRedisLockExecutor#provider` 返回 `KsetRedissonLockProvider`。外部调用方已迁移由用户确认，生产部署状态未知，旧实现尚在使用的证据未发现。
- 兼容选择：直接替换，不保留旧方法。
- 回滚目标：N/A（无先前 change-id；原入口可从基线 Git ref 恢复）
- 基线 Git ref：`a98f3ecdc9cf2989689a0eeb4627cd1efde42d64`
- 结果 Git ref：N/A
- 回滚来源：Git ref `a98f3ecdc9cf2989689a0eeb4627cd1efde42d64`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-redis/src/main/java/com/kset/redis/core/KsetRedisOperations.java` | `template()` | 高层 Redis SPI 底层出口 | remove | removed |
| `kset-starter-redis/src/main/java/com/kset/redis/core/KsetRedisService.java` | `template()` | 服务门面委托 | remove | removed |
| `kset-starter-redis/src/main/java/com/kset/redis/core/KsetRedisTemplateOperations.java` | `template()` | Spring Data Redis 适配实现出口 | remove | removed |
| `kset-starter-redis/src/main/java/com/kset/redis/lock/KsetRedisLockExecutor.java` | `provider()` | 锁执行器底层出口 | remove | removed |
| `kset-starter-redis/src/test/java/com/kset/redis/autoconfigure/KsetRedisAutoConfigurationTest.java` | `keepsNativeRedisBeansAndAddsKsetRedisTemplate` | 装配断言 | modify | active |
| `kset-starter-redis/README.md` | 常用 API | 移除已失效说明 | modify | active |

### 回滚步骤

1. 由用户指定回滚到本轮修改前的基线后，从基线 Git ref 恢复四处方法和相关文档、断言。
2. 更新本记录的入口关系与实现状态。

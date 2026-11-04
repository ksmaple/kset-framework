# KSet Schedule Starter

`kset-starter-schedule` 提供受管定时任务注解和基于 SQL 锁表的集群唯一运行锁。`@KsetScheduled` 继承 Spring `@Scheduled` 的调度属性，`@KsetTaskLock` 可叠加在定时任务上，也可单独用于需要集群互斥的方法。

## 依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-schedule</artifactId>
</dependency>
```

业务应用仍需通过 `@EnableScheduling` 开启 Spring 调度。唯一运行锁使用现有 `DataSource` 和 `JdbcTemplate`，不会额外创建数据源。

## 快速接入

```java
import com.kset.schedule.annotation.KsetScheduled;
import com.kset.schedule.annotation.KsetTaskLock;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@EnableScheduling
@Component
public class OrderTasks {

    @KsetScheduled(cron = "0 0/5 * * * ?", name = "order-timeout-close")
    @KsetTaskLock(name = "order-timeout-close", atMostFor = "10m")
    public void closeTimeoutOrders() {
        // 集群内同一锁名只由一个实例执行
    }
}
```

- 集群单活任务：使用 `@KsetScheduled` 并叠加 `@KsetTaskLock`。
- 每个实例都要执行的任务，例如本地缓存刷新：继续使用原生 `@Scheduled`，不要添加唯一运行锁。
- 手动补偿等非常规调度入口也可单独使用 `@KsetTaskLock`。
- `name` 为空时，任务名或锁名默认使用 `简单类名.方法名`；同一锁名表示同一互斥范围。

## 配置

```yaml
kset:
  scheduler:
    lock:
      enabled: true
      table-name: t_kset_schedule_lock
      at-most-for: 10m
      auto-create-table: true
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `kset.scheduler.lock.enabled` | `true` | 是否启用唯一运行锁自动配置 |
| `kset.scheduler.lock.table-name` | `t_kset_schedule_lock` | SQL 锁表名 |
| `kset.scheduler.lock.at-most-for` | `10m` | 注解未指定 `atMostFor` 时使用的全局租期 |
| `kset.scheduler.lock.auto-create-table` | `true` | 启动时是否执行 `CREATE TABLE IF NOT EXISTS` |

存在 `DataSource` 时，模块默认创建 SQL `TaskLockProvider` 并自动建表；支持识别 PostgreSQL、MySQL 和 MariaDB，其他数据库使用通用 DDL。关闭自动建表时，需提前按模块实现准备同结构锁表。没有 `DataSource` 或 `JdbcTemplate` 时，`@KsetTaskLock` 不生效，启动日志会明确告警，任务将在每个实例执行。

## 锁参数与执行语义

| 注解属性 | 默认值 | 说明 |
|----------|--------|------|
| `name` | 空 | 锁名；空值按 `简单类名.方法名` 生成 |
| `atMostFor` | 空 | 宕机后的兜底租期；空值使用全局配置，必须大于任务最长执行时间 |
| `atLeastFor` | `0s` | 执行结束后至少保留锁的时间，用于降低短周期任务接力重复执行风险 |

时长支持 `10m`、`40s` 等 Duration 风格或 ISO-8601 格式。非法的注解时长会在启动期校验并阻止应用启动；`TaskLockProvider` 收到空白锁名或非正 `atMostFor` 时会拒绝请求。

抢锁先通过过期条件更新尝试续期，再尝试插入新锁记录；主键冲突表示其他实例持有锁。锁时间以数据库当前时间为准，过期抢占会递增 `fencing_token`。未抢到锁时方法不会执行，切面返回 `null`，因此有返回值的方法需要调用方处理这种情况。每次抢锁使用独立持有标识，方法结束后只释放本次持有的锁并按 `atLeastFor` 更新期限；默认 JDBC 实现要求在抢锁线程释放。释放失败时记录告警，并等待 `atMostFor` 到期。

## 使用边界

唯一运行锁用于降低集群并发执行，不等同于业务幂等或永久互斥：

- 任务执行超过 `atMostFor` 后，其他实例可以抢锁，需按真实最长执行时间设置租期。
- 进程宕机、锁释放失败或租期到期后，任务可能再次执行；写入、消息投递和远程调用等副作用仍需业务幂等。
- SQL 实现维护 `fencing_token`，但业务方法不会自动获得该令牌；需要 fencing 条件写的业务应在自身事务与持久化模型中实现。
- 批量任务仍应采用稳定排序、有界批次和独立短事务，不要在全量扫描或远程调用期间扩大数据库事务。

字段和锁表结构可查阅[当前代码对象与字段](../docs/design/current-object-fields.md#kset-starter-schedule)。

# 定时任务唯一运行锁 变更记录

- feature-key：`scheduled-task-lock`
- 当前 change-id：`scheduled-task-lock-v2`
- 当前状态：`active`
- 当前入口：`kset-starter-schedule/src/main/java/com/kset/schedule/autoconfigure/KsetScheduleAutoConfiguration.java`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| scheduled-task-lock-v1 | active | `KsetScheduleAutoConfiguration`、`@KsetScheduled`、`@KsetTaskLock` | N/A（全新能力） | N/A | N/A | 主体仍在使用；旧按实例 ID 释放方式已由 v2 替代 |
| scheduled-task-lock-v2 | active | `KsetTaskLockAspect` → `JdbcTaskLockProvider#tryLock/unlock` | v1 按实例 ID 释放锁 | `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` | 可用 | 不适用 |

## scheduled-task-lock-v1

- 日期：2026-08-29
- 目标：提供贴合 Spring 原生的定时任务唯一运行锁（SQL 锁表），注解与调度解耦，零配置自动识别
- 判定时间：2026-08-29 +08:00
- 上线状态：开发未上线（1.0.13-SNAPSHOT 未发布）
- 判定方式：自动判定
- 判定证据：根 pom 版本 1.0.13-SNAPSHOT，Nexus 无该版本构件
- 证据来源：`pom.xml`
- 当时代码状态：基线 Git ref `1066ec2`；框架无定时任务锁能力；kset-rag 使用自研 `ScheduledJobLeaseGuard`（PG 租约）
- 兼容选择：N/A（全新能力）
- 回滚目标：N/A
- 基线 Git ref：`1066ec2e367cceff68330bbb15c897b64bcc7347`
- 结果 Git ref：N/A（未提交）
- 回滚来源：N/A（新模块，删除即回滚）
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| kset-starter-schedule（新模块） | KsetScheduled / KsetTaskLock | 受管任务注解（继承原生 @Scheduled）/ 独立唯一运行锁注解 | add | active |
| kset-starter-schedule/.../lock/ | TaskLockProvider / JdbcTaskLockProvider | 锁 SPI 与 SQL 锁表实现（启动自动识别方言建表、fencing、DB 时间） | add | active |
| kset-starter-schedule/.../aop/KsetTaskLockAspect.java | KsetTaskLockAspect | 抢锁成功才执行，失败跳过返回 null | add | active |
| kset-starter-schedule/.../autoconfigure/ | KsetScheduleAutoConfiguration / KsetScheduleProperties | 零配置装配（DataSource 识别），无数据源时告警降级 | add | active |
| pom.xml、kset-boot-parent/pom.xml | modules / dependencyManagement | 注册新模块与版本管理 | modify | active |
| README.md、project-spec.md | - | 模块与约定登记 | modify | active |
| kset-starter-schedule 测试 | KsetTaskLockAspectTest / JdbcTaskLockProviderTest | 切面与锁语义单测（未执行） | add | active |

### 回滚步骤

1. 删除 `kset-starter-schedule` 模块及根 pom / parent pom 注册项。
2. 业务侧移除注解或回退各任务实现。

## scheduled-task-lock-v2

- 日期：2026-11-03
- 目标：同一实例再次抢锁后，旧任务不得释放新任务持有的锁；到期等于数据库当前时间时允许抢占。
- 判定时间：2026-11-03 18:05:32 +08:00
- 上线状态：不涉及兼容（恢复唯一运行锁预期行为，公开方法签名不变）
- 判定方式：自动判定
- 判定证据：已发布 `v1.0.13` 包含本模块；本轮仅修复过期重抢后的释放条件。
- 证据来源：`CHANGELOG.md`、Git tag `v1.0.13`、`kset-starter-schedule/src/main/java/com/kset/schedule/lock/JdbcTaskLockProvider.java`
- 当时代码状态：入口为 `KsetTaskLockAspect#around`，调用 `TaskLockProvider#tryLock/unlock`；旧 JDBC 实现按实例 ID 释放，无法区分同实例先后两次持有，且到期判断排除等于数据库当前时间的锁；仓内调用方为切面与测试，外部调用方未知；现实现状态为 active。
- 兼容选择：N/A
- 回滚目标：`scheduled-task-lock-v1`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认（未提供目标环境发布与部署映射）
- 旧版正式发布版本：待确认
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 结果 Git ref：N/A（未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-schedule/src/main/java/com/kset/schedule/lock/JdbcTaskLockProvider.java` | `tryLock/unlock` | 每次抢锁用独立持有标识，按该标识条件释放，并允许锁到期时抢占 | modify | active |
| `kset-starter-schedule/src/main/java/com/kset/schedule/lock/TaskLockProvider.java` | `TaskLockProvider` | 明确默认 JDBC 实现的同线程释放契约 | modify | active |
| `kset-starter-schedule/src/test/java/com/kset/schedule/JdbcTaskLockProviderTest.java` | 锁持有回归用例 | 静态核对两次持有的释放标识 | modify | active |
| `kset-starter-schedule/README.md` | 锁执行语义 | 说明每次持有标识与释放边界 | modify | active |

### 回滚步骤

1. 从基线 Git ref 恢复 `JdbcTaskLockProvider#tryLock/unlock` 与相关契约说明。
2. 保留无关工作区修改，按实际结果更新实现状态。

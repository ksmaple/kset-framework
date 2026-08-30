# 版本发布说明

本文档记录 kset-framework 各版本的变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/)。

## [v1.0.16] - 2026-08-30

### 新增

- **全模块配置元数据自动生成**：`kset-boot-parent` 注解处理器链新增 `spring-boot-configuration-processor`，全部 `kset.*` 配置项编译期自动生成 `spring-configuration-metadata.json`，业务方 IDE 写 yml 时有自动补全与默认值提示（注意：升级 Boot 父版本时需同步该处理器版本）

### 变更

- `GatewayRouteRuleProvider`：`RouteDefinitionWriter` 迁移至 `RouteDefinitionRepository`；路由写入/删除的裸 `subscribe()` 增加错误日志，不再静默吞错
- `KsetRedis` 静态门面的 `hGetAll`/`sMembers` 同步标记 `@Deprecated`（与接口层弃用语义对齐，消除门面自调用警告）
- `LogMaskingUtil` 回滚保留方法对 Jackson `ObjectNode.fields()` 弃用警告显式抑制并注释说明
- `JdbcTaskLockProvider` 入参防御：空锁名 / 非正 `atMostFor` 直接 `IllegalArgumentException`（否则唯一运行语义静默失效）
- 新增 `KsetTaskLockAnnotationValidator`：启动期校验 `@KsetTaskLock` 的 `atMostFor`/`atLeastFor` 时长格式，非法值 fail-fast 而不是任务触发时才报错

### 备注

- 构建日志中 `GatewayRouteRuleProvider.java 使用或覆盖了已过时的 API` 为 Maven 并行构建的既有提示噪音：单文件 javac `-Xlint:deprecation` 验证该文件无任何过时 API 使用。

## [v1.0.15] - 2026-08-29

### 变更（架构解耦，配置键全部不变）

- **cloud 配置载体下沉**：`KsetRedisProperties` 移至 `kset-starter-redis`（`com.kset.redis.config`）、`KsetDatasourceProperties` 移至 `kset-starter-datasource`（`com.kset.datasource.config`）；cloud 中旧类标记 `@Deprecated` 保留兼容，**配置键 `kset.redis.*` / `kset.datasource.*` 完全不变**
- **monitor 解除对 kset-cloud 的依赖**：灰度/链路配置自持——`kset.monitor.dubbo.trace-propagation-enabled`、`kset.monitor.dubbo.default-gray-tag`、`kset.monitor.gateway.trace-header`，未配置时自动回退读取 `kset.cloud.*` 旧键（行为兼容）
- redis / datasource / monitor 三个 starter 不再依赖 kset-cloud；cloud 收敛为纯云规则层（dubbo/gateway/nacos/sentinel 使用）

### 升级指引

- 配置无需任何修改（新旧键均生效，自持键优先）
- 极少数直接 import `com.kset.cloud.config.KsetRedisProperties` / `KsetDatasourceProperties` 的代码建议改引新包（旧类仍编译可用）

## [v1.0.14] - 2026-08-29

### 变更

- `kset-starter-schedule` 移除未使用的 `kset-common` 依赖，成为零内部依赖的最轻 starter
- 静态门面停机解绑：新增 `KsetCache.unbind()` / `Monitor.uninstall()`；`KsetCacheBootstrap` / `KsetRedisBootstrap` / `MonitorFacadeInstaller` 在停机时解除静态绑定，避免同 JVM 重启（DevTools/测试上下文）串状态
- EnvPostProcessor 注册机制统一：datasource/monitor 从老式 `META-INF/spring.factories` 迁到新式 `META-INF/spring/` 文件，全框架一致

### 修复

- `MonitorFacadeTest` 测试隔离：原 `@AfterEach` 安装 NoOp 门面不还原，污染同 JVM 后续测试（KsetHttpTest/ParallelTest）；改为保存/还原原门面

### 升级指引

无迁移事项；行为变化仅限停机路径（更干净）。

## [v1.0.13] - 2026-08-29

### 新增

- **新模块 `kset-starter-schedule`**：定时任务唯一运行锁
  - `@KsetScheduled`：元注解继承原生 `@Scheduled` 全量调度属性（cron/fixedRate/zone 等），与原生注解明确区分
  - `@KsetTaskLock`：独立声明的集群唯一运行锁，可叠加在调度或任意方法上；抢不到锁的实例跳过执行
  - SQL 锁表 `t_kset_schedule_lock`：UPDATE 过期抢占 + INSERT 兜底 + fencing token 自增，时间以数据库为准防时钟漂移
  - 启动自动识别数据库方言（PostgreSQL/MySQL/MariaDB）并自动建表，零配置；无 DataSource 时告警降级为单机语义
  - 配置：`kset.scheduler.lock.enabled/table-name/at-most-for/auto-create-table`

### 升级指引

1. parent 升级为 `1.0.13`，按需引入 `kset-starter-schedule`
2. 集群单活的定时任务：原生 `@Scheduled` 换为 `@KsetScheduled` 并叠加 `@KsetTaskLock(atMostFor=...)`（atMostFor 须大于任务最长执行时间）
3. 每实例都要执行的任务（本地缓存刷新、实例级巡检）保持原生 `@Scheduled` 不动

## [v1.0.12] - 2026-08-29

> 统一优雅启停、健康状态接入、依赖瘦身。**包含需要注意的行为变化，升级前请阅读"行为变化"小节。**

### 新增

- **统一优雅启停编排**（`kset-common` 新增 `com.kset.common.lifecycle` 包）：基于 Spring `SmartLifecycle` 相位编排全部组件停机顺序——健康摘流(600) → Dubbo/MQ 停收(500) → 事件执行器排空(400) → 业务线程池排空(300) → 监控 flush(200) → Redis 连接关闭(100)；DB 连接池在 Spring destroy 阶段最后关闭
- **优雅停机默认值**：未显式配置时自动注入 `server.shutdown=graceful`、`spring.lifecycle.timeout-per-shutdown-phase=30s`、`dubbo.application.shutwait=10000`（用户配置始终优先）
- **停机策略**：线程池/事件执行器"排空 + 超时兜底"（`kset.lifecycle.shutdown-timeout`，默认 30s），超时强关后额外等待 2s 让被中断任务完成清理；关闭全程打印每池快照与排空结果日志
- **健康状态接入**：停机开始即将 readiness 置为 `REFUSING_TRAFFIC`（K8s readiness 探针立即失败摘流）；引入 actuator 时 `/actuator/health` 暴露 `ksetLifecycle` 项，可查看各组件 `RUNNING/STOPPED` 排空进度（`kset.lifecycle.health.enabled` 可关）
- **业务侧扩展点**：`KsetLifecycleHook` + `KsetLifecycleHookAdapter`，业务组件可注册 Bean 加入统一启停编排（推荐函数式用法，相位支持常量加偏移插入任意位置）
- **Dubbo 优雅停机**：`KsetDubboLifecycle` 在流量相位调用 `DubboBootstrap.stop()` 注销摘流
- **总开关**：`kset.lifecycle.enabled=false` 可整体回退为 Spring 原生销毁顺序

### 变更（行为变化）

- `KsetThreadPoolFactory.shutdownAll()` 由"立即关闭"改为"排空 + 默认 30s 超时兜底"；新增 `shutdownAll(long timeoutMillis)`
- `SpringEventFacade` 停机由 `shutdownNow` 硬关改为优雅排空（原实现保留为 `destroyForRollback`）
- **线程命名统一 `kset-` 前缀**：`we-thread-pool-%d` → `kset-thread-pool-%d`，业务池 `{name}-%d` → `kset-{name}-%d`，调参线程 `{name}-tuner` → `kset-{name}-tuner`（如有按线程名过滤的日志/监控规则请同步调整）
- **默认 profile 注入收窄**：不再向 Spring 环境注入 `spring.profiles.default=dev`，仅日志层内部按 dev 判定行为（无 profile 时日志输出不变）；显式读取 `spring.profiles.default` 的代码将拿到 `null`

### 移除

- **Guava 依赖**：`ThreadFactoryBuilder` 内联为 JDK 实现，Guava 不再随 `kset-common` 传递；业务需要请自行声明（版本仍由 `kset-boot-parent` 管理）

### 修复（随本次发布包含的既有提交）

- `71c21c6` fix: prevent framework thread and config leaks
- `19f17ed` perf: bound framework utility caches

### 升级指引

1. parent 升级为 `1.0.12` 即可，无配置迁移
2. 使用 Spring AI 的项目注意：**1.0.11 起框架已移除 AI 依赖管理**，需在自身父 POM 导入 `spring-ai-bom`（参考 kset-rag-server/pom.xml）
3. K8s 部署建议 `terminationGracePeriodSeconds ≥ 90`（Web 30s + Dubbo 10s + 各相位兜底）

## [v1.0.11] - 2026-08

### 变更

- 统一改用普通 JSON 字符串序列化（`71c3347`）
- **移除框架 AI 依赖管理**（spring-ai-bom / spring-ai-alibaba-graph），AI 版本改由业务侧自管（`71412da`）——升级至此版本及以上需在业务父 POM 自行管理 Spring AI 版本

## [v1.0.10] - 2026-08

- 移除 Web 文档框架（`3652251`）
- MyBatis-Plus 字段填充兼容多种时间字段名称（`3011533`）

## [v1.0.9] - 2026-07

- 统一原生 HTTP 与日期处理；时间工具改用原生 Date 重构（`b38d246`、`e6b1803`）

## [v1.0.8] - 2026-07

- 优化业务侧日志调试开关：`kset.logging.business-debug` 按包名注入 DEBUG 级别（`c50fc42`）
- 全局框架能力完善

## [v1.0.0 ~ v1.0.7] - 2026-06 ~ 2026-07

早期迭代：模块结构定型、鉴权注解与权限优化、AI 功能初版等。此阶段提交粒度较粗，详细变更见 git 历史。

---

## 如何发布新版本

项目提供两种发布方式：**方式一（release 插件，pom 已配置，推荐）**为标准流程，日常发布一律使用；**方式二（手工 versions:set + deploy）**仅作为插件流程异常或需逐步验证时的备选，v1.0.12 即用方式二发布。两种方式都必须手工处理 `kset-framework.version` 自定义属性（详见各节）。

### 方式一：maven-release-plugin（标准流程，推荐）

根 `pom.xml` 已预配置 maven-release-plugin（`tagNameFormat=v@{project.version}`、`autoVersionSubmodules=true`、构建参数 `-q -DskipTests -Dmaven.test.skip=true -Pnexus`）。

**1. 先将 `kset-boot-parent/pom.xml` 的自定义属性改为发布版本并提交**（插件只改 `<version>`，不会改该属性，否则发布的 POM 内部模块引用会指向 SNAPSHOT）：

```xml
<kset-framework.version>1.0.12</kset-framework.version>
```

**2. 一条命令完成 prepare + perform**（-B 批处理模式避免交互输入版本号）：

```powershell
mvn -B release:prepare release:perform "-DreleaseVersion=1.0.12" "-DdevelopmentVersion=1.0.13-SNAPSHOT"
```

该命令自动完成：全量构建（跳过测试）→ 改版本号并提交两次（`prepare release` / `prepare for next development iteration`）→ 打标签 `v1.0.12` → 推送 main 与标签 → 从标签检出重建并 deploy 到 Nexus releases。

**3. 插件回滚版本后，手工把 `kset-framework.version` 改回下一 SNAPSHOT 并补充提交：**

```powershell
git add -A; git commit -m "chore: sync kset-framework.version to 1.0.13-SNAPSHOT"; git push origin main
```

**4. 失败恢复**：prepare 阶段失败可用 `mvn release:rollback` 回退本地变更；已推送的错误标签需手工删除（`git push origin :v<x.y.z>`）。

### 方式二：手工 versions:set + deploy（备选，v1.0.12 实际使用）

适合需要逐步验证、或插件流程异常时的备选。

#### 前置条件

- JDK 21、Maven 可用；能访问内部 Nexus（默认 `http://192.168.53.5:8081`）
- `~/.m2/settings.xml` 已配置 `kset-nexus-releases` / `kset-nexus-snapshots` 两个 server 凭据
- 工作区干净（`git status` 无未提交变更），`main` 与远端同步

#### 发布步骤（以 1.0.12 → 1.0.13-SNAPSHOT 为例）

> PowerShell 注意：`-D` 参数含多个 `.` 时必须加引号，如 `"-DnewVersion=1.0.12"`，否则会被拆参数导致 `Unknown lifecycle phase` 报错。

**1. 发布前编译验证（必做）**

```powershell
mvn -q -DskipTests test-compile -T 1C   # 编译主源码与测试源码，不执行测试
```

**2. 设置发布版本**

```powershell
mvn -q versions:set "-DnewVersion=1.0.12" "-DprocessAllModules=true" "-DgenerateBackupPoms=false"
```

并手工同步 `kset-boot-parent/pom.xml` 中的自定义属性（versions 插件不会改它）：

```xml
<kset-framework.version>1.0.12</kset-framework.version>
```

**3. 提交发布版本并打标签**

```powershell
git add -A; git commit -m "release: 1.0.12"; git tag v1.0.12
```

**4. 构建并发布到 Nexus**

```powershell
mvn deploy -DskipTests "-Dmaven.test.skip=true" -Pnexus
```

全部模块出现 `Uploaded to kset-nexus-releases` 且 `BUILD SUCCESS` 即成功。可访问 Nexus 仓库页面或 `http://192.168.53.5:8081/repository/maven-releases/com/kset/` 核对。

**5. 进入下一开发版本**

```powershell
mvn -q versions:set "-DnewVersion=1.0.13-SNAPSHOT" "-DprocessAllModules=true" "-DgenerateBackupPoms=false"
```

同步 `kset-framework.version` 为 `1.0.13-SNAPSHOT`，并更新 `README.md`、`kset-boot-parent/README.md` 中的版本示例，然后：

```powershell
git add -A; git commit -m "chore: next development iteration 1.0.13-SNAPSHOT"
git push origin main; git push origin v1.0.12
```

> 注意：`git push --follow-tags` 只推送 annotated tag（`git tag -a`），轻量标签需显式 `git push origin v<x.y.z>`。

**6. 补充本文档**：在顶部新增该版本的发布说明（新增/变更/修复/行为变化/升级指引）。

### 注意事项（两种方式通用）

- 根聚合 POM 已配置 `maven.deploy.skip=true`，不会发布自身，仅发布各模块
- Nexus releases 仓库默认禁止覆盖同版本构件；发错版本只能发下一版本号，不能重发
- 发布前确保测试已在 CI 或本地执行通过（本流程命令中跳过测试仅是打包提速，不代表可以发布未验证代码）
- 使用方接入：parent 升级为新版本号即可；涉及行为变化时在发布说明中必须写清升级指引

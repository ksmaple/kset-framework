# KSet 公共框架（kset-framework）项目专属规范

> init 生成。优先级：本文档 > `.claude/skills/kaka-coder-designer/references/`。

**proj**：`kset-framework` · **框架库（Spring Boot Starter 聚合仓）** · 生成：2026-06-14

## 1. 画像

- coder / fixer：`kset-framework-coder`、`kset-framework-fixer`
- 技术栈：Java 21、Spring Boot 3.5.14、Spring Cloud 2025.0.2、Spring Cloud Alibaba 2025.0.0.0、MyBatis-Plus 3.5.5、Dubbo 3.3.6、Nacos、Redis、RocketMQ、Gateway trace、Sentinel、Elasticsearch、Flyway、Spring AI 1.0.0、DuckDB
- 包名：`com.kset.*`（demo 为 `com.kset.demo.*`）
- SQL：**MySQL 8.0**（见 `.claude/sql-dialect.json`）；实体以 MyBatis-Plus 注解为主
- API：REST + `ApiResponse`（`code` / `message` / `data`），非平台默认 Full POST + `ApiResult`
- Git 提交：`kaka-util-git-commit`（init 自 kset-developer 复制）

## 2. 规范整合

| 主题 | 状态 | 实际做法 | 域路径 |
|------|------|----------|--------|
| Naming | 部分整合 | Java 驼峰；表/字段 demo 用 `createTime`/`deleted`（非 `created_at`）；名称与入参/出参须简单易懂（N028） | references/naming-spec.md |
| DDD | 不适用 | Starter/工具库为主；demo 为 Entity + Mapper，无 domain/application 分层 | references/ddd-spec.md |
| API | 需调整 | `@GetMapping`/`@PostMapping`、路径参数；统一 `ApiResponse` | references/api-spec.md |
| Frontend | 待生成 | 本仓无前端 | references/frontend-spec.md |
| SQL | 部分整合 | MyBatis-Plus、`@TableLogic`、`IdType.AUTO`；方言 MySQL | references/sql-spec.md |
| Conversion | 已整合 | MapStruct 1.5.5；`com.kset.common.convert.KsetMapperConfig` 全局配置；依赖与注解处理器见 `kset-parent` / `kset-common` | references/conversion-spec.md |
| Event | 按需 | 部分能力在 starter 中（按模块阅读） | references/event-spec.md |
| Cache | 部分整合 | Redis starter；按模块配置 | references/cache-spec.md |
| Orchestration | 部分整合 | Dubbo、Gateway、Nacos、Sentinel 等云原生组件 | references/orchestration-spec.md |
| Log | 已整合 | SLF4J+Logback+logstash-encoder；`@Slf4j`+`LogUtil`；TraceId/MDC 见 cloud/starter | references/log-spec.md |
| Engineering | 按需 | 熔断/容量等见 starter 与 cloud 模块 | references/engineering-spec.md |

## 3. 差异项

- **API**：禁止将 demo/对外 HTTP 改为「全 POST + ApiResult」；保持 REST 动词与 `com.kset.web.response.ApiResponse`；`@OpLog` 在 `com.kset.web.annotation`。
- **DDD**：新增 starter 或 common 能力时不强制四层 DDD；`kset-demo` 仅作集成示例，保持轻量分层。
- **模块边界**：无 Servlet/AOP 的工具与监控门面 API 进 `kset-common`；Servlet Filter、OpLog、Web 工具进对应 `kset-starter-*`；云相关进 `kset-cloud`；示例仅放 `kset-demo`。
- **监控**：统一包根 `com.kset.common.monitor`；业务代码使用静态入口 `com.kset.common.monitor.Monitor` 与 `facade.MonitorFacade`；默认后端 `backend.LogBackend`（本地 SLF4J）。
- **依赖**：常用工具库（Commons / Guava / OkHttp / Jackson / Fastjson2 / TTL 等）**仅**在 `kset-common` 声明；`kset-cloud` 与各 `kset-starter-*` **必须**依赖 `kset-common`，禁止重复声明上述工具依赖；领域能力（MyBatis、Redis、Nacos 等）在对应 starter 声明。
- **版本**：子模块版本由根 `kset-parent` / `kset-framework` BOM 统一管理，勿在子模块随意覆盖 Spring Boot 主版本。
- **测试依赖**：`spring-boot-starter-test` 由 `kset-parent` 以 `test` scope 统一继承；子模块勿重复声明，新增测试能力（如 Testcontainers）在 parent 集中维护。
- **Lombok**：由 `kset-parent` 以 `provided` 继承；Gateway 等模块若 IDE 未识别可显式声明同坐标（`provided`，不传递下游）。子模块勿用 `optional` 覆盖。
- **规范来源**：仅引用本仓 `.claude/skills/`，禁止写死平台仓 `kset-developer` 绝对路径。
- **日志**：`@Slf4j` 声明 Logger；结构化用 **`StructLog.of(X.class)` 绑定一次**；脱敏用 `LogMaskingUtil`。
- **文档**：组件能力文档就近写入对应模块 `README.md`；`docs/` 仅保留跨组件指南、发布说明与共享样例，禁止在 `docs/` 继续新增单组件说明。

## 4. 命令

| 用途 | 命令 |
|------|------|
| 编译 | `mvn -q -DskipTests compile`（仓库根目录） |
| 测试 | `mvn -q test -pl kset-demo/demo-standalone-service -am`、`mvn -q test -pl kset-demo/demo-micro-service -am`、`mvn -q test -pl kset-demo/demo-gateway -am`（测试由 demo 服务模块承载；库模块默认跳过 surefire） |

# 全局日志变更记录

- feature-key：`global-logging`
- 当前 change-id：`global-logging-20261105-02`
- 当前状态：`active`
- 当前入口：`kset-common/src/main/resources/kset-logback-spring.xml`、`kset-starter-web/src/main/java/com/kset/web/filter/RequestLoggingFilter.java`、`kset-starter-web/src/main/java/com/kset/web/handler/GlobalExceptionHandler.java`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| `global-logging-20261105-01` | active-compatible | 保留的日志配置与异常边界 | 当前源码中的日志格式与输出逻辑；旧参数摘要已被替换 | `a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c` | 可用，需保留本轮前工作树改动 | N/A |
| `global-logging-20261105-02` | active | 请求参数日志 | `global-logging-20261105-01` 的白名单参数摘要 | 本轮修改前工作树状态 | 可用，需按本轮差异恢复 | N/A |

## global-logging-20261105-01

- 日期：2026-11-05
- 目标：统一可定位的登录、参数摘要和异常日志，移除操作目标名称原值，并补齐 DEBUG/TRACE 文件输出。
- 判定时间：2026-11-05 12:10:40 Asia/Shanghai
- 上线状态：不涉及兼容
- 判定方式：N/A
- 判定证据：仅调整内部日志与可关闭的请求日志配置，不修改 Web 请求响应契约。
- 证据来源：`kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java`、`kset-starter-web/src/main/java/com/kset/web/response/ApiResponse.java`
- 当时代码状态：Logback 只有 INFO/WARN/ERROR 文件；请求日志默认关闭且 DEBUG 输出请求体；MVC 未预期异常仅输出堆栈；网关异常仅输出状态和异常类型；认证拒绝无独立日志。其他未提交改动保持原样。
- 兼容选择：N/A
- 回滚目标：N/A
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c`
- 结果 Git ref：`9dc7d78fea8c3a3e5eecab2cd7c7820bc1c3f866`
- 回滚来源：`a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c` 与本轮修改前工作树状态
- 回滚可用性：可用；不能覆盖本轮前已有的未提交改动

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-common/src/main/resources/kset-logback-spring.xml` | root appenders | 日志输出入口 | modify | active |
| `kset-common/src/main/resources/kset-logback-file-appenders.xml` | file appenders | JSON 文件格式与级别 | modify | active |
| `kset-common/src/main/java/com/kset/common/logging/FlowLogContext.java` | `error` | 流程异常上下文 | modify | active |
| `kset-common/src/main/java/com/kset/common/logging/LogMaskingUtil.java` | `isSensitiveKey` | 参数敏感字段识别 | modify | active |
| `kset-common/src/main/java/com/kset/common/trace/TraceHeaders.java` | `BUSINESS_CODE_ATTRIBUTE` | 请求业务码关联 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/logging/RequestLogSummary.java` | `ofRequest`、`ofValues` | 有界参数摘要 | add | active |
| `kset-starter-web/src/main/java/com/kset/web/filter/RequestLoggingFilter.java` | `doFilterInternal` | 请求摘要 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java` | `RequestLogging` | 摘要开关与白名单 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/autoconfigure/KsetWebAutoConfiguration.java` | `requestLoggingFilter` | 默认启用请求摘要 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/advice/TraceIdResponseBodyAdvice.java` | `beforeBodyWrite` | 标记业务失败码 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/annotation/OpLog.java` | `recordParams`、`recordResult` | 注解日志语义说明 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/aop/OpLogAspect.java` | `logOperation` | 操作摘要 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/handler/GlobalExceptionHandler.java` | `handleException` | MVC 最终异常边界 | modify | active |
| `kset-starter-auth/src/main/java/com/kset/auth/web/LoginAuthFilter.java` | `doFilterInternal` | Servlet 认证结果 | modify | active |
| `kset-starter-auth/src/main/java/com/kset/auth/web/DefaultServletAuthFailureHandler.java` | `handle` | 认证失败码关联 | modify | active |
| `kset-starter-auth/src/main/java/com/kset/auth/gateway/LoginAuthGatewayFilter.java` | `filter` | Gateway 认证结果 | modify | active |
| `kset-starter-gateway/src/main/java/com/kset/gateway/handler/KsetGatewayErrorHandler.java` | `handle` | Gateway 最终异常边界 | modify | active |
| `kset-starter-monitor/src/main/java/com/kset/common/monitor/web/TraceIdFilter.java` | `doFilter` | Servlet 过滤器异常边界 | modify | active |
| `kset-common/README.md`、`kset-starter-web/README.md`、`docs/usage/web.md` | 日志说明 | 配置与诊断口径 | modify | active |

### 回滚步骤

1. 对照基线 Git ref 与本轮前工作树状态恢复日志入口关系。
2. 恢复 Logback、Web、认证和网关日志配置及说明。
3. 更新本记录中的实现状态。

## global-logging-20261105-02

- 日期：2026-11-05
- 目标：每条 Web 请求记录全部可解析的路径、查询、表单与 JSON 参数值，对敏感值脱敏，参数日志最多 64KB 并标记截断。
- 判定时间：2026-11-05 13:11:50 Asia/Shanghai
- 上线状态：不涉及请求响应契约兼容；日志输出量与字段变化
- 判定方式：用户确认
- 判定证据：用户明确选择全字段和值脱敏记录、64KB 上限。
- 证据来源：本轮对话；`kset-starter-web/src/main/java/com/kset/web/filter/RequestLoggingFilter.java`
- 当时代码状态：上一轮 `RequestLoggingFilter` 仅在失败/慢请求输出 WARN、正常请求 DEBUG；`RequestLogSummary` 最多 16 个参数名且值需白名单；Json 缓存上限 64KB；其他工作树改动保留。
- 兼容选择：N/A
- 回滚目标：`global-logging-20261105-01`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c` 与本轮修改前工作树状态
- 结果 Git ref：`9dc7d78fea8c3a3e5eecab2cd7c7820bc1c3f866`
- 回滚来源：本轮修改前工作树中的 `global-logging-20261105-01` 实现
- 回滚可用性：可用；此前实现尚未提交，需按本轮差异恢复

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-web/src/main/java/com/kset/web/logging/RequestLogSummary.java` | `ofRequest` | 旧参数摘要 | remove | removed |
| `kset-starter-web/src/main/java/com/kset/web/logging/RequestLogParameters.java` | `capture` | 结构化参数采集与截断 | add | active |
| `kset-starter-web/src/main/java/com/kset/web/filter/RequestLoggingFilter.java` | `logRequest` | 每条请求写入 | modify | active |
| `kset-common/src/main/java/com/kset/common/logging/LogMaskingUtil.java` | `maskJson` | 嵌套敏感值脱敏 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java` | `RequestLogging` | 参数配置 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/autoconfigure/KsetWebAutoConfiguration.java` | `requestLoggingFilter` | 请求日志装配 | modify | active |
| `kset-common/src/main/java/com/kset/common/trace/TraceHeaders.java` | 登录请求属性 | 可信身份关联 | modify | active |
| `kset-starter-auth/src/main/java/com/kset/auth/web/LoginAuthFilter.java` | `doFilterInternal` | 登录身份绑定 | modify | active |
| `kset-starter-monitor/src/main/java/com/kset/common/monitor/web/GrayTagServletFilter.java` | `doFilter` | 请求灰度标签关联 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/aop/OpLogAspect.java` | `parameterSummary` | 操作参数记录 | modify | active |
| `kset-starter-web/src/main/java/com/kset/web/handler/GlobalExceptionHandler.java` | `logUnhandled` | 异常参数记录 | modify | active |
| `kset-starter-web/README.md`、`docs/usage/web.md` | 请求日志说明 | 配置与数据范围 | modify | active |

### 回滚步骤

1. 恢复本轮修改前的参数摘要、白名单与日志等级。
2. 保留 `global-logging-20261105-01` 的其他日志配置与异常边界改动。
3. 更新本记录中的实现状态。

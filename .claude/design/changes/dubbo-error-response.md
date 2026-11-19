# Dubbo 调用故障响应变更记录

- feature-key：`dubbo-error-response`
- 当前 change-id：`dubbo-error-response-v1`
- 当前状态：`active`
- 当前入口：`kset-starter-dubbo/src/main/java/com/kset/dubbo/web/DubboRpcExceptionHandler.java#handleRpcException`

> 2026-11-03 清理说明：`KsetGatewayAutoConfiguration#gatewayErrorHandlerForRollback` 已移除，原行为从历史基线 Git ref 恢复；详见 `rollback-code-cleanup.md`。历史时点记录保留原样。

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| baseline | rollback-candidate | `kset-starter-gateway/src/main/java/com/kset/gateway/autoconfigure/KsetGatewayAutoConfiguration.java#gatewayErrorHandlerForRollback` | N/A | `a98f3ecdc9cf2989689a0eeb4627cd1efde42d64` | 可用 | 用户授权且旧行为不再需要 |
| dubbo-error-response-v1 | active | `kset-starter-dubbo/src/main/java/com/kset/dubbo/web/DubboRpcExceptionHandler.java#handleRpcException` | baseline | `a98f3ecdc9cf2989689a0eeb4627cd1efde42d64` | 可用 | N/A |

## dubbo-error-response-v1

- 日期：2026-10-15
- 目标：区分 Dubbo 无提供者、网络/注册中心、超时和未知异常；对齐 MVC 与网关错误响应。
- 判定时间：2026-10-15 12:14:10 +08:00
- 上线状态：待确认
- 判定方式：自动判定
- 判定证据：`v1.0.16` 发布标签与 `CHANGELOG.md` 已发布记录；无可定位到生产环境的部署或调用证据。
- 证据来源：`CHANGELOG.md`、Git tag `v1.0.16`
- 当时代码状态：MVC 对 `RpcException` 统一返回系统 500；Gateway 对未处理异常统一返回 HTTP 500 与 `Gateway error`；已知调用方为引入对应 starter 的业务服务，迁移状态未知。
- 兼容选择：兼容增量；保持原有响应字段与默认 HTTP 200 配置，新增明确 RPC 故障分类，未知故障仍走兜底。
- 回滚目标：baseline
- 基线 Git ref：`a98f3ecdc9cf2989689a0eeb4627cd1efde42d64`
- 结果 Git ref：N/A
- 回滚来源：`a98f3ecdc9cf2989689a0eeb4627cd1efde42d64`、`KsetGatewayAutoConfiguration#gatewayErrorHandlerForRollback`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-dubbo/pom.xml` | 可选 `kset-starter-web` 依赖 | MVC 适配编译依赖，不向纯 RPC 调用方传递 | modify | active-compatible |
| `kset-starter-dubbo/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | `KsetDubboWebAutoConfiguration` | 自动装配入口 | modify | active-compatible |
| `kset-starter-dubbo/src/main/java/com/kset/dubbo/autoconfigure/KsetDubboWebAutoConfiguration.java` | `dubboRpcExceptionHandler` | 条件装配 MVC 适配 | add | active |
| `kset-starter-dubbo/src/main/java/com/kset/dubbo/exception/DubboFailureType.java` | `DubboFailureType#from` | RPC 故障分类 | add | active |
| `kset-starter-dubbo/src/main/java/com/kset/dubbo/web/DubboRpcExceptionHandler.java` | `handleRpcException` | MVC 响应适配 | add | active |
| `kset-starter-web/src/main/java/com/kset/web/handler/GlobalExceptionHandler.java` | `handleException` | 非 RPC 异常兜底仍承接调用 | modify | active-compatible |
| `kset-starter-gateway/src/main/java/com/kset/gateway/autoconfigure/KsetGatewayAutoConfiguration.java` | `ksetGatewayErrorWebExceptionHandler` | 新网关处理器装配 | modify | active |
| `kset-starter-gateway/src/main/java/com/kset/gateway/handler/KsetGatewayErrorHandler.java` | `handle` | Gateway 错误响应 | add | active |
| `kset-starter-gateway/src/main/java/com/kset/gateway/autoconfigure/KsetGatewayAutoConfiguration.java` | `gatewayErrorHandlerForRollback` | 原 Gateway 兜底 | retain | rollback-candidate |
| `kset-starter-monitor/src/main/java/com/kset/common/monitor/gateway/TraceIdGatewayFilter.java` | `filter` | 错误响应 traceId 传递 | modify | active-compatible |
| `kset-starter-auth/src/main/java/com/kset/auth/web/AuthExceptionHandler.java` | `AuthExceptionHandler` | 鉴权 advice 优先级 | modify | active-compatible |
| `kset-starter-redis/src/main/java/com/kset/redis/web/KsetRedisLockExceptionHandler.java` | `KsetRedisLockExceptionHandler` | Redis 锁 advice 优先级 | modify | active-compatible |
| `kset-starter-dubbo/src/test/java/com/kset/dubbo/exception/DubboFailureTypeTest.java` | `DubboFailureTypeTest` | RPC 分类覆盖 | add | active |
| `kset-starter-dubbo/src/test/java/com/kset/dubbo/web/DubboRpcExceptionHandlerTest.java` | `DubboRpcExceptionHandlerTest` | MVC 响应覆盖 | add | active |
| `kset-starter-gateway/src/test/java/com/kset/gateway/handler/KsetGatewayErrorHandlerTest.java` | `KsetGatewayErrorHandlerTest` | 网关响应覆盖 | add | active |

### 回滚步骤

1. 移除 Dubbo Web 适配自动装配，恢复原 MVC 兜底路径。
2. 将 Gateway 错误处理 Bean 指向 `gatewayErrorHandlerForRollback`，按需恢复 TraceId Gateway Filter 的原行为。
3. 更新本记录中的实现状态；未提交前可通过基线 Git ref 恢复。

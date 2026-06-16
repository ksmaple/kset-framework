# KSet Web Starter

业务服务引入 `kset-starter-web` 后，默认集成 Web 基础能力：统一响应 `ApiResponse`、全局异常处理、`@OpLog` 操作日志、请求日志开关、TraceId 响应增强，以及 **Knife4j 4.x**（底层 springdoc-openapi）。

## 核心能力

| 能力 | 入口 | 默认行为 |
|------|------|----------|
| 统一响应 | `com.kset.web.response.ApiResponse` | `code` / `message` / `data` / `traceId` 响应模型 |
| 全局异常 | `GlobalExceptionHandler` | 业务异常、参数校验、404、系统异常统一转换为 `ApiResponse` |
| 操作日志 | `@OpLog` / `OpLogAspect` | `kset.web.oplog.enabled=true` 时启用，默认读取 `X-User-Id` |
| 请求日志 | `RequestLoggingFilter` | `kset.web.request-logging.enabled=true` 时启用 |
| TraceId 响应 | `TraceIdResponseBodyAdvice` | `kset.web.response.trace-id-enabled=true` 时写入响应体 |
| API 文档 | Knife4j / OpenAPI 3 | `knife4j.enable=true` 时访问 `/doc.html` |

## 访问地址

| 资源 | 路径 |
|------|------|
| Knife4j UI | `http://localhost:{port}/doc.html` |
| OpenAPI JSON | `/v3/api-docs` |
| 默认分组 | `/v3/api-docs/default` |

> Gateway 不使用 `starter-web`，无 Knife4j 页面；文档在各业务微服务上查看。

## 配置

优先使用 **Knife4j / springdoc 标准属性**；`kset.web.knife4j.*` 仅作 OpenAPI 元数据可选覆盖。

```yaml
knife4j:
  enable: true              # 标准开关（框架默认 true）
  setting:
    language: zh_cn

springdoc:
  group-configs:
    - group: default
      paths-to-match: /api/**

# 可选：覆盖 OpenAPI 标题/描述（未设 title 时取 spring.application.name）
kset:
  web:
    oplog:
      enabled: true
      user-id-header: X-User-Id
    request-logging:
      enabled: false
    response:
      trace-id-enabled: true
    knife4j:
      title: my-service
      description: KSet API
      version: 1.0.0
```

生产环境建议：`knife4j.enable=false`，或通过网关/鉴权限制 `/doc.html` 访问。

> 兼容旧键 `kset.web.knife4j.enabled`：仅在未设置 `knife4j.enable` 时生效。

## 业务注解约定

```java
@Tag(name = "用户")
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Operation(summary = "按 ID 查询")
    @GetMapping("/{id}")
    public ApiResponse<UserEntity> get(@PathVariable Long id) { ... }
}
```

统一响应体 `com.kset.web.response.ApiResponse` 已带 `@Schema`，业务 `data` 字段建议在实体上补充 `@Schema`。

## 自定义 OpenAPI

如需完全自定义，可自行声明 `@Bean OpenAPI` / `GroupedOpenApi`（框架在未定义时提供默认 Bean）。

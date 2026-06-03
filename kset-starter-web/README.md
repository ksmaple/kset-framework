# API 文档（OpenAPI 3 / Knife4j）

业务服务引入 `kset-starter-web` 后，默认集成 **Knife4j 4.x**（底层 springdoc-openapi）。

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

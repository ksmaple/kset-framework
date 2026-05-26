# API 文档（OpenAPI 3 / Knife4j）

业务服务引入 `kset-spring-boot-starter-web` 后，默认集成 **Knife4j 4.x**（底层 springdoc-openapi）。

## 访问地址

| 资源 | 路径 |
|------|------|
| Knife4j UI | `http://localhost:{port}/doc.html` |
| OpenAPI JSON | `/v3/api-docs` |
| 默认分组 | `/v3/api-docs/default` |

kset-demo：

- 单机（8080）：http://localhost:8080/doc.html — `demo-standalone-service`
- 用户服务（8081）：http://localhost:8081/doc.html — Cloud 示例
- 订单服务（8082）：http://localhost:8082/doc.html — Cloud 示例

> Gateway 不使用 `starter-web`，无 Knife4j 页面；文档在各业务微服务上查看。

## 配置

```yaml
kset:
  web:
    knife4j:
      enabled: true          # 关闭则不同步 knife4j.enable，且不注册 OpenAPI Bean
      title: my-service      # 可选，默认 spring.application.name
      description: KSet API
      version: 1.0.0
      path-pattern: /api/**  # GroupedOpenApi 扫描路径

knife4j:
  enable: true
  setting:
    language: zh_cn
```

生产环境建议：`kset.web.knife4j.enabled=false`，或通过网关/鉴权限制 `/doc.html` 访问。

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

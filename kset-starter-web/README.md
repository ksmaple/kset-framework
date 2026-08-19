# KSet Web Starter

业务服务引入 `kset-starter-web` 后，默认集成 Web 基础能力：统一响应 `ApiResponse`、全局异常处理、`@OpLog` 操作日志、请求日志开关和 TraceId 响应增强。

接入写法见 [Web 统一响应](../docs/usage/web.md)。

## 核心能力

| 能力 | 入口 | 默认行为 |
|------|------|----------|
| 统一响应 | `com.kset.web.response.ApiResponse` | `code` / `message` / `data` / `traceId` 响应模型 |
| 全局异常 | `GlobalExceptionHandler` | 业务异常、参数校验、404、系统异常统一转换为 `ApiResponse` |
| 操作日志 | `@OpLog` / `OpLogAspect` | `kset.web.oplog.enabled=true` 时启用；默认读 `X-User-Id`，鉴权关闭遗留分头时操作人可能为空 |
| 请求日志 | `RequestLoggingFilter` | `kset.web.request-logging.enabled=true` 时启用 |
| TraceId 响应 | `TraceIdResponseBodyAdvice` | `kset.web.response.trace-id-enabled=true` 时写入响应体 |

## 依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-web</artifactId>
</dependency>
```

## 配置

```yaml
kset:
  web:
    oplog:
      enabled: true
      user-id-header: X-User-Id
    request-logging:
      enabled: false
    response:
      trace-id-enabled: true
```

## 业务接口

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    @GetMapping("/{id}")
    public ApiResponse<UserEntity> get(@PathVariable Long id) {
        return ApiResponse.success(userService.getById(id));
    }
}
```

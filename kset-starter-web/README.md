# KSet Web Starter

业务服务引入 `kset-starter-web` 后，默认集成 Web 基础能力：统一响应 `ApiResponse`、全局异常处理、`@OpLog` 操作日志、请求日志开关和 TraceId 响应增强。

接入写法见 [Web 统一响应](../docs/usage/web.md)。

## 核心能力

| 能力 | 入口 | 默认行为 |
|------|------|----------|
| 统一响应 | `com.kset.web.response.ApiResponse` | `code` / `message` / `data` / `traceId` 响应模型 |
| 全局异常 | `GlobalExceptionHandler` | 业务异常、参数校验、404、系统异常统一转换为 `ApiResponse` |
| 操作日志 | `@OpLog` / `OpLogAspect` | 默认启用；操作人来自可信 `LoginContext` 或业务显式设置的 `OpLogContext` |
| 请求日志 | `RequestLoggingFilter` | 默认启用；失败和超过 500ms 的请求记 WARN，正常请求记 INFO |
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
    request-logging:
      enabled: true
    response:
      trace-id-enabled: true
```

每条请求日志包含 traceId、HTTP 状态、业务错误码、耗时和可用的登录主体信息。`parameters` 记录全部可解析的路径变量、查询/表单参数及 JSON 字段和值，支持重复参数和嵌套 JSON；密码、令牌、联系方式等敏感值会脱敏。参数内容最多 64KB，超出后 `parametersTruncated=true`；未读取、只读取部分或无法解析的 JSON 分别标为 `NOT_READ`、`PARTIALLY_READ`、`UNPARSEABLE`。不记录上传文件内容、非 JSON 原始请求体或响应体。`@OpLog(recordParams = true)` 在 HTTP 请求中使用相同参数内容，`recordResult = true` 只记录结果类型。

旧 `kset.web.oplog.user-id-header` 配置不再决定操作人，业务应使用 `LoginContext`，或显式设置 `OpLogContext`。`@OpLog.targetName` 保留兼容但不再输出原值，目标定位使用 `target` 和 `targetId`。

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

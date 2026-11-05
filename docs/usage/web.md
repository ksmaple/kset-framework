# Web 统一响应

依赖 `kset-starter-web`。业务接口返回 `ApiResponse<T>`；异常由全局处理器转成同一形状。

## 响应形状

HTTP 默认保持 **200**，业务成败看 body 里的 `code`。成功 `code=0`。

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

`{"code":0,"message":"success","data":{...},"traceId":"..."}`

未登录 / 无权限由 auth starter 返回 `code` 401 / 403，HTTP 仍是 200。需要真实 HTTP 状态码时：

```yaml
kset:
  web:
    exception-handling:
      use-http-status: true
```

日期字段统一 `yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai`。

## 业务异常

```java
throw new BusinessException("订单已关闭");
throw new BusinessException(409, "订单已关闭");
```

`BusinessException` 可带 cause。未带数字码时默认业务码 400。

## 操作日志

`@OpLog` 优先从可信 `LoginContext` 取操作人；无登录上下文时可由业务显式设置 `OpLogContext`。不从客户端 `X-User-Id` 请求头取操作人。

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

每条请求都会记录日志：失败和慢请求为 WARN，普通成功请求为 INFO。日志包含 traceId、登录态、可信用户 ID、主体类型、HTTP 状态、业务错误码、耗时，以及全部可解析的路径变量、查询/表单参数和嵌套 JSON 参数值。敏感值脱敏；参数内容最多 64KB，超过时记录 `parametersTruncated=true`。上传文件内容、非 JSON 原始请求体和响应体不记录。未预期异常在最终处理边界记录完整堆栈。`@OpLog(recordParams = true)` 在 HTTP 请求中使用相同参数内容，`recordResult = true` 仅记录结果类型。

`@OpLog.targetName` 保留兼容但不再输出原值；目标定位使用 `target` 和 `targetId`。

配置项清单见 [kset-starter-web/README.md](../../kset-starter-web/README.md)。

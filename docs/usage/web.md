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

`@OpLog` 默认从请求头 `X-User-Id` 取操作人。鉴权默认不认遗留分头，这个头经常是空的。请改 `kset.web.oplog.user-id-header`，或在业务里用 `LoginContext` 写入 `OpLogContext`。

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

配置项清单见 [kset-starter-web/README.md](../../kset-starter-web/README.md)。

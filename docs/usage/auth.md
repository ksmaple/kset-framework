# 登录鉴权

依赖 `kset-starter-auth`。Web 默认用 `session` 方案 + `X-Session-Token`，从 `LoginSessionStore`（通常 Redis）取用户。

`scheme` 和 `web.mode` 不是一回事，不要配成不存在的 `mode: session`：

| 配置 | 含义 | 默认 |
|------|------|------|
| `kset.auth.default-scheme` | 认证方案。`session` 表示用 token 查 `LoginSessionStore` | `session` |
| `kset.auth.web.mode` | Web 如何拿到用户。`redis` 查 session；`trusted-header` 只信网关写的登录头 | `redis` |

`web.mode` 只有 `redis` / `trusted-header`。

## Web 登录态

```java
Optional<LoginUser> user = LoginContext.currentUser();
String userId = LoginContext.currentUserId().orElseThrow();
```

接口上用 `@RequireLogin` / `@RequireRole` / `@RequirePermission`。`@RequirePermission` 只表示功能码（能不能进这个接口），不表示「某条资源能不能动」。资源实例权限见 [资源权限规范](../design/resource-permission.md)。

跳过校验：

- `@SkipAuth`：整条跳过登录和权限注解，适合公开接口
- `@SkipLoginAuth`：只跳过 `session` / `trusted-header`，不影响验签等其他方案

## 网关后的 trusted-header

业务服务如果只信网关透传的登录头，不要对公网开放这一模式：

```yaml
kset:
  auth:
    web:
      mode: trusted-header
```

网关必须覆盖或剥离客户端带来的登录头，否则任何人都能伪造用户。需要处理的头：

| Header | 说明 |
|--------|------|
| `X-Auth-Subject` | 当前主体，如 `app`、`admin` |
| `X-App-Login-Context` | app 主体的 JSON 登录上下文 |
| `X-Admin-Login-Context` | admin 主体的 JSON 登录上下文 |
| `X-{Subject}-Login-Context` | 其他主体 |
| `X-Login-Context` | 兼容旧头，仍可能被客户端带上 |

JSON 登录头可以带角色权限，因为假定头是网关写的。

## 遗留分头

`X-User-Id`、`X-Roles` 这类拆开的头**默认不认**。仅在网关后的兼容期打开：

```yaml
kset:
  auth:
    web:
      mode: trusted-header
      legacy-split-headers-enabled: true
```

打开后仍**不会**从分头读取角色、权限，只认身份字段。新代码请走 JSON 登录上下文头。

## 业务代码里取用户

Filter 结束后 `LoginContext` 会清掉。要在异步线程继续用，先 `LoginContext.capture()` 或依赖 `Parallel` / `EventFacade` 已经帮你带过去的 `KsetContext`。

多套主体、Gateway/Dubbo 透传、session 配置见 [kset-starter-auth/README.md](../../kset-starter-auth/README.md)。

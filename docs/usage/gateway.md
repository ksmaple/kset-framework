# 网关

`kset-starter-gateway` 给独立 Gateway 进程用，不要和 `kset-starter-web` 放在同一个应用。登录态规则建议再引入 `kset-starter-auth`。

## 鉴权

默认 `kset.cloud.gateway.auth-enabled=false`，不过滤登录。打开后：

1. 实现 `GatewayAuthProvider` Bean（JWT / OAuth2 / 调认证服务）——推荐
2. 或使用内置 Header Token：必须配置共享密钥，请求头与配置值一致才放行

```yaml
kset:
  cloud:
    gateway:
      auth-enabled: true
      auth-token-header: X-Auth-Token
      auth-token: ${GATEWAY_AUTH_TOKEN}
```

未配置 `auth-token`、头缺失、值不一致都是 401。任意非空字符串**不会**再当成已登录。

SPI 约定：

- `Mono.empty()`：鉴权通过，继续后面的 Filter
- 已提交或 4xx/5xx 的响应 Mono：拒绝，不再转发
- `null`：这个 Provider 不处理，试下一个

自定义 Provider 必须真正校验凭证，不要写成「头非空就放行」。

## CORS

默认关闭。需要浏览器跨域时：

```yaml
kset:
  cloud:
    gateway:
      cors-enabled: true
```

当前实现允许任意 Origin 且带凭证，只适合受控环境。生产请自行换成来源白名单，不要直接开默认 CORS。

## 灰度

请求头 `X-Gray-Tag`（可配）会往下游传。LoadBalancer 按实例 metadata 匹配。详见 [cloud.md](cloud.md) 与 `kset-starter-nacos` / `kset-cloud` README。

动态路由 JSON、Sentinel 见 [kset-starter-gateway/README.md](../../kset-starter-gateway/README.md)。

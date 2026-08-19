# KSet Gateway Starter

`kset-starter-gateway` 集成 Spring Cloud Gateway、Nacos 动态路由、灰度标签、Gateway Sentinel 和鉴权 SPI。Gateway 是独立进程，不应与 `kset-starter-web` 同用。

鉴权、CORS 接入见 [网关使用说明](../docs/usage/gateway.md)。

## 依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-monitor</artifactId>
</dependency>
```

需要统一登录鉴权时再引入 `kset-starter-auth` 和 `kset-starter-redis`。

## 配置

```yaml
spring:
  application:
    name: order-gateway
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}

kset:
  cloud:
    gateway:
      enabled: true
      route-data-id: order-gateway-gateway-routes
      sentinel-enabled: true
      auth-enabled: false
      cors-enabled: false
      trace-header: X-Trace-Id
      gray-header: X-Gray-Tag
```

`route-data-id` 未配置时默认使用 `{spring.application.name}-gateway-routes`。

## 动态路由

```json
[
  {
    "id": "order-service",
    "uri": "lb://order-service",
    "predicates": [
      { "name": "Path", "args": { "pattern": "/api/orders/**" } }
    ],
    "filters": [
      { "name": "StripPrefix", "args": { "parts": "1" } }
    ]
  }
]
```

## 鉴权扩展

启用 `kset.cloud.gateway.auth-enabled=true` 后，默认 `HeaderTokenGatewayAuthProvider` 校验 `kset.cloud.gateway.auth-token` 与请求头（默认 `X-Auth-Token`）是否一致；未配置或值不匹配返回 401。生产请实现 `GatewayAuthProvider` 替换为 JWT/OAuth2，**必须真正校验凭证**。

```yaml
kset:
  cloud:
    gateway:
      auth-enabled: true
      auth-token-header: X-Auth-Token
      auth-token: ${GATEWAY_AUTH_TOKEN}
```

```java
@Component
public class JwtGatewayAuthProvider implements GatewayAuthProvider {
    @Override
    public Mono<Void> authenticate(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!jwtService.isValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return Mono.empty(); // 放行，继续 Gateway 过滤链
    }
}
```

`Mono.empty()` 表示通过；已提交或 4xx/5xx 响应表示拒绝；`null` 表示本 Provider 不处理。不要写成「请求头非空就放行」。

CORS 默认关闭。需要跨域时显式 `kset.cloud.gateway.cors-enabled=true`，并自行收紧来源白名单。

## 边界

- Gateway TraceId 由 `kset-starter-monitor` 的 Gateway 插件处理。
- 登录态规则和 session 查询建议使用 `kset-starter-auth`。
- 业务 MVC Controller 不放在 Gateway 进程内。

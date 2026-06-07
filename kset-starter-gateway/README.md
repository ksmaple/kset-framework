# KSet Gateway Starter

`kset-starter-gateway` 集成 Spring Cloud Gateway、Nacos 动态路由、灰度标签、Gateway Sentinel 和鉴权 SPI。Gateway 是独立进程，不应与 `kset-starter-web` 同用。

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
      cors-enabled: true
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

启用 `kset.cloud.gateway.auth-enabled=true` 后，可实现 `GatewayAuthProvider` 扩展网关鉴权：

```java
@Component
public class TokenGatewayAuthProvider implements GatewayAuthProvider {
    @Override
    public Mono<Void> authenticate(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst("X-Auth-Token");
        if (StringUtils.hasText(token)) {
            return null; // 放行，继续执行后续 Provider 或 Gateway 过滤链
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
```

## 边界

- Gateway TraceId 由 `kset-starter-monitor` 的 Gateway 插件处理。
- 登录态规则和 session 查询建议使用 `kset-starter-auth`。
- 业务 MVC Controller 不放在 Gateway 进程内。

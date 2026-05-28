# kset-demo 示例工程

两个独立示例，对应不同接入方式：

## 1. 单机项目 — `demo-standalone-service`

| 项 | 说明 |
|----|------|
| 依赖 | web + mysql + redis |
| 端口 | 8080 |
| 中间件 | MySQL（库 `kset_demo`）、Redis |
| 启动 | `mvn -pl kset-demo/demo-standalone-service spring-boot:run` |

- API：http://localhost:8080/api/users/1  
- 文档：http://localhost:8080/doc.html  

详见 [docs/getting-started.md](../docs/getting-started.md#一单机项目)。

## 2. 微服务 Cloud — `demo-user-service` / `demo-order-service` / `demo-gateway`

| 模块 | 端口 | 说明 |
|------|------|------|
| `demo-api` | — | Dubbo 接口定义 |
| `demo-user-service` | 8081 | 用户服务 + Dubbo Provider（nacos + sentinel） |
| `demo-order-service` | 8082 | 订单服务，Dubbo 消费用户 + Redis（nacos + sentinel） |
| `demo-gateway` | 见 application.yaml | 网关（仅 starter-gateway） |

中间件：MySQL、Redis、Nacos（`NACOS_ADDR` 默认 `127.0.0.1:8848`）。

```bash
mvn clean install
mvn -pl kset-demo/demo-user-service spring-boot:run
mvn -pl kset-demo/demo-order-service spring-boot:run
mvn -pl kset-demo/demo-gateway spring-boot:run
```

Gateway 路由样例：[docs/nacos/demo-gateway-routes.json](../docs/nacos/demo-gateway-routes.json)。

详见 [docs/getting-started.md](../docs/getting-started.md#二微服务-cloud-项目)。

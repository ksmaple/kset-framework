# KSet Demo 环境配置

本目录集中放置 demo 可复用的本地环境配置，所有中间件地址默认指向 `127.0.0.1` 或 `localhost`。

配置原则：
- `application-global.yml` 是全局覆盖配置，放应用名、端口、profile 等跨组件配置。
- 每个组件一个 `component-*.yml`，按需叠加。
- 组件文件只保留必选项；非必选项全部以注释形式保留来源和含义。
- 基础配置优先使用开源组件原生配置键；KSet 扩展配置只在需要覆盖默认行为时出现。

## 文件清单

| 文件 | 用途 |
|------|------|
| `application-global.yml` | 全局覆盖配置：端口、profile、应用名 |
| `component-mysql.yml` | MySQL 单数据源配置示例 |
| `component-pgsql.yml` | PostgreSQL 单数据源配置示例，可平替 `component-mysql.yml` |
| `component-sqlite.yml` | SQLite 单数据源配置示例，可平替 `component-mysql.yml` |
| `component-datasource-dynamic.yml` | dynamic-datasource 多数据源配置，使用 `@DS` 切换 |
| `component-redis.yml` | Spring Redis 配置 |
| `component-knife4j.yml` | Knife4j / OpenAPI 配置 |
| `component-nacos.yml` | Nacos Discovery / Config 配置 |
| `component-dubbo.yml` | Dubbo 可选覆盖配置；基础项默认由 starter 自动派生 |
| `component-monitor.yml` | Monitor / CAT 后端覆盖配置 |
| `component-redisson.yml` | Redisson 分布式锁配置 |
| `component-cache.yml` | KSet Cache 可选覆盖配置 |
| `component-gateway.yml` | Gateway 可选覆盖配置 |
| `component-sentinel.yml` | Sentinel 可选覆盖配置 |
| `component-rocketmq.yml` | RocketMQ 配置 |
| `component-logging.yml` | 日志可选覆盖配置 |
| `component-web.yml` | KSet Web 可选覆盖配置 |
| `cat/client.xml` | CAT 客户端本地连接配置，默认连接 `127.0.0.1:2280` |

## 使用方式

单体应用基础组合：
```bash
mvn -pl kset-demo/demo-standalone-service spring-boot:run -Dspring-boot.run.arguments="--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-sqlite.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-knife4j.yml --spring.application.name=demo-standalone-service --server.port=8080"
```

微服务基础组合：
```bash
mvn -pl kset-demo/demo-user-service spring-boot:run -Dspring-boot.run.arguments="--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-sqlite.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-nacos.yml,optional:file:./kset-demo/env/component-knife4j.yml --spring.application.name=demo-user-service --server.port=8081"
```

PostgreSQL 单体应用基础组合：
```bash
mvn -pl kset-demo/demo-standalone-service spring-boot:run -Dspring-boot.run.arguments="--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-pgsql.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-knife4j.yml --spring.application.name=demo-standalone-service --server.port=8080"
```

PostgreSQL 微服务基础组合：
```bash
mvn -pl kset-demo/demo-user-service spring-boot:run -Dspring-boot.run.arguments="--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-pgsql.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-nacos.yml,optional:file:./kset-demo/env/component-knife4j.yml --spring.application.name=demo-user-service --server.port=8081"
```

SQLite 单体应用基础组合：
```bash
mvn -pl kset-demo/demo-standalone-service spring-boot:run -Dspring-boot.run.arguments="--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-sqlite.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-knife4j.yml --spring.application.name=demo-standalone-service --server.port=8080"
```

多数据源单体应用基础组合：
```bash
mvn -pl kset-demo/demo-standalone-service spring-boot:run -Dspring-boot.run.arguments="--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-datasource-dynamic.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-knife4j.yml --spring.application.name=demo-standalone-service --server.port=8080"
```

多数据源微服务基础组合：
```bash
mvn -pl kset-demo/demo-user-service spring-boot:run -Dspring-boot.run.arguments="--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-datasource-dynamic.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-nacos.yml,optional:file:./kset-demo/env/component-knife4j.yml --spring.application.name=demo-user-service --server.port=8081"
```

MySQL、PostgreSQL、SQLite 现在只保留配置示例，数据库能力由 `kset-starter-datasource` 和 JDBC 驱动提供。启动时单数据源配置文件三选一导入；多数据源场景只导入 `component-datasource-dynamic.yml`。如需切换数据库，请在业务模块 POM 中替换为对应 JDBC 驱动；多数据库混用时同时引入需要的 JDBC 驱动。

业务代码使用 dynamic-datasource 开源注解切换数据源：
```java
@DS("audit")
public void writeAuditLog() {
    // 当前方法使用 audit 数据源
}
```

启用 CAT 监控后端：
```bash
mvn -pl kset-demo/demo-standalone-service spring-boot:run -Dspring-boot.run.arguments="--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-sqlite.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-knife4j.yml,optional:file:./kset-demo/env/component-monitor.yml --spring.application.name=demo-standalone-service --server.port=8080"
```

启用 Redisson 分布式锁：
```bash
mvn -pl kset-demo/demo-standalone-service spring-boot:run -Dspring-boot.run.arguments="--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-sqlite.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-redisson.yml --spring.application.name=demo-standalone-service --server.port=8080"
```

CAT 客户端配置文件路径按 CAT 客户端默认查找规则加载。若本机 CAT Server 不在 `127.0.0.1`，先修改 `cat/client.xml` 中的 `ip`、`port` 和 `http-port`。

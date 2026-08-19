# 数据源

依赖 `kset-starter-datasource`。本仓不按数据库拆 Starter，JDBC 驱动由业务自己加。

生产约定是 **MySQL 8**（列类型 `DATETIME`）。README 里的 SQLite 只方便本地无外部库时跑通。

## 单库 MySQL

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-datasource</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/order?serverTimezone=Asia/Shanghai
    username: ${DB_USER}
    password: ${DB_PASSWORD}
kset:
  datasource:
    enabled: true
    auto-fill: true
```

`createTime` / `updateTime`、`createdAt` / `updatedAt`、`createDate` / `updateDate` 自动填充字段类型为 `Date`。实体仍要写 `@TableField(fill = FieldFill.INSERT)` 或 `INSERT_UPDATE`，否则不会覆盖业务赋值。

## 多数据源

配了 `spring.datasource.dynamic.datasource.*` 才启用 dynamic-datasource；没配时默认关掉，避免干扰单库。代码用 `@DS("名称")` 切换。

Flyway 不是本 Starter 默认能力，需要时业务自己加 `flyway-core` 和对应方言。SQL 监控在同时引入 `kset-starter-monitor` 后生效。

完整配置见 [kset-starter-datasource/README.md](../../kset-starter-datasource/README.md)。

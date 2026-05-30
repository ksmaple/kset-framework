# KSet Datasource

`kset-starter-datasource` 提供 JDBC、MyBatis-Plus、Flyway、dynamic-datasource 多数据源和 KSet 自动填充能力。MySQL、PostgreSQL、SQLite 不再提供 KSet 独立 starter，业务按需直接引入对应 JDBC 驱动。

## 单数据源

单数据源使用 Spring Boot 原生 `spring.datasource.*`：

```yaml
spring:
  datasource:
    url: jdbc:sqlite:./data/kset_demo.db
```

Maven 依赖示例：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-datasource</artifactId>
</dependency>
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
</dependency>
```

如使用 MySQL 或 PostgreSQL，将驱动替换为 `com.mysql:mysql-connector-j` 或 `org.postgresql:postgresql`。需要 Flyway 数据库方言时再显式引入 `flyway-mysql` 或 `flyway-database-postgresql`。

配置来源和含义：
- `spring.datasource.url`：来源 Spring Boot `DataSourceProperties`，数据库连接地址。
- `spring.datasource.username`：来源 Spring Boot `DataSourceProperties`，数据库用户名。
- `spring.datasource.password`：来源 Spring Boot `DataSourceProperties`，数据库密码。
- `spring.datasource.hikari.*`：来源 HikariCP，连接池参数，非必选。
- `spring.flyway.*`：来源 Spring Boot FlywayProperties，数据库迁移参数，非必选。
- `mybatis-plus.*`：来源 MyBatis-Plus，Mapper、枚举、驼峰映射、逻辑删除等参数，非必选。

## 多数据源

多数据源对齐开源 `dynamic-datasource-spring-boot3-starter`，配置使用 `spring.datasource.dynamic.*`，代码使用开源 `@DS` 注解切换。

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:sqlite:./data/kset_demo.db
          driver-class-name: org.sqlite.JDBC
        audit:
          url: jdbc:sqlite:./data/kset_audit.db
          driver-class-name: org.sqlite.JDBC
```

配置来源和含义：
- `spring.datasource.dynamic.primary`：来源 dynamic-datasource，默认数据源名称。
- `spring.datasource.dynamic.strict`：来源 dynamic-datasource，找不到数据源时是否直接报错。
- `spring.datasource.dynamic.datasource.{name}.url`：来源 dynamic-datasource `DataSourceProperty`，命名数据源连接地址。
- `spring.datasource.dynamic.datasource.{name}.username`：来源 dynamic-datasource `DataSourceProperty`，命名数据源用户名。
- `spring.datasource.dynamic.datasource.{name}.password`：来源 dynamic-datasource `DataSourceProperty`，命名数据源密码。
- `spring.datasource.dynamic.datasource.{name}.driver-class-name`：来源 dynamic-datasource `DataSourceProperty`，命名数据源 JDBC 驱动类。
- `spring.datasource.dynamic.datasource.{name}.hikari.*`：来源 dynamic-datasource `HikariCpConfig`，当前命名数据源连接池参数，非必选。

```java
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("audit")
public void writeAuditLog() {
    // 当前方法使用 audit 数据源
}
```

## KSet 默认行为

`kset.datasource.*` 只保留 KSet 自身能力：

```yaml
# kset:
#   datasource:
#     enabled: true
#     auto-fill: true
```

配置来源和含义：
- `kset.datasource.enabled`：来源 KSet Datasource Starter，控制 KSet 数据源辅助自动装配，默认 `true`。
- `kset.datasource.auto-fill`：来源 KSet Datasource Starter，控制 `createTime` / `updateTime` 自动填充处理器，默认 `true`。

兼容说明：
- 没有配置 `spring.datasource.dynamic.datasource.*` 时，KSet 会默认关闭 dynamic-datasource 自动配置，避免影响 Spring Boot 原生单数据源。
- 配置了 `spring.datasource.dynamic.datasource.*` 后，dynamic-datasource 按开源默认规则启用。
- MyBatis-Plus 使用最终的 Spring `DataSource`，单库和多库都不需要额外配置。
- Flyway 默认跟随主数据源；多库迁移建议由业务显式配置。
- Monitor SQL 拦截器基于 MyBatis / MyBatis-Plus 生效，不按数据库类型额外配置。

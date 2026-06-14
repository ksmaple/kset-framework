# kset-parent

`kset-parent` 是 KSet 框架面向非 Spring Boot 体系的 Maven parent，继承 `kset-dependencies` BOM，统一 Java 21、编码、编译插件与通用测试依赖。

纯库或非 Boot 业务工程可继承本模块。

## 继承方式

```xml
<parent>
    <groupId>com.kset</groupId>
    <artifactId>kset-parent</artifactId>
    <version>1.0.6-SNAPSHOT</version>
    <relativePath/>
</parent>
```

继承后，依赖 KSet 模块或 BOM 中管理的三方库时通常不需要再写 `version`。

## 基线

| 项 | 版本 / 约定 |
|----|-------------|
| Java | 21 |
| Maven | 3.9+ |
| Spring Boot Dependencies BOM | 3.5.14（仅用于版本管理，不引入 Boot 父 POM） |
| Spring Cloud | 2025.0.2 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Apache Dubbo | 3.3.6 |
| 编码 | UTF-8 |

## 与 kset-boot-parent 的关系

- `kset-parent`：不继承 `spring-boot-starter-parent`，适合纯库、非 Boot 项目。
- `kset-boot-parent`：继承 `spring-boot-starter-parent`，适合 Spring Boot 业务服务与 `kset-starter-*`。
- 两者都通过 `kset-dependencies` 统一管理 KSet 模块与三方组件版本。

## 测试栈

本 parent 为子模块统一提供：

- JUnit 5（`junit-jupiter`）
- AssertJ（`assertj-core`）
- Mockito（`mockito-core`、`mockito-junit-jupiter`）
- Spring Test（`spring-test`，提供 `MockEnvironment` 等测试工具）

版本由 `kset-dependencies` 中导入的 `spring-boot-dependencies` BOM 管理。

## 发布

启用 `nexus` profile 时，本模块与各模块发布到配置的 Nexus 仓库：

```bash
mvn -q -DskipTests deploy -Pnexus
```

根聚合 POM 设置了 `maven.deploy.skip=true`，不会作为构件发布。

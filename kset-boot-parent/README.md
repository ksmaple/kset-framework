# kset-boot-parent

`kset-boot-parent` 是 KSet 框架面向 Spring Boot 体系的 Maven parent，继承 `spring-boot-starter-parent` 并导入 `kset-dependencies` BOM，统一 Java 21、编码、插件、三方依赖与 KSet 模块版本。

业务 Spring Boot 服务应继承本模块。

## 继承方式

```xml
<parent>
    <groupId>com.kset</groupId>
    <artifactId>kset-boot-parent</artifactId>
    <version>1.0.6-SNAPSHOT</version>
    <relativePath/>
</parent>
```

继承后，业务依赖 KSet 模块或 BOM 中管理的三方库时通常不需要再写 `version`。

## 基线

| 项 | 版本 / 约定 |
|----|-------------|
| Java | 21 |
| Maven | 3.9+ |
| Spring Boot | 3.5.14 |
| Spring Cloud | 2025.0.2 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Apache Dubbo | 3.3.6 |
| 编码 | UTF-8 |

## 依赖管理

`kset-boot-parent` 通过导入 `kset-dependencies` BOM 管理全部 KSet 模块版本与三方依赖版本。KSet 自身模块版本由 `kset-framework.version` 固定管理，不使用业务工程的 `project.version`。

公共工具库统一由 `kset-common` 声明和传递；业务引入任意 `kset-starter-*` 后一般无需重复声明 Guava、Commons、OkHttp、Jackson、Fastjson2、TTL 等基础工具。

## 发布

启用 `nexus` profile 时，本模块与各模块发布到配置的 Nexus 仓库：

```bash
mvn -q -DskipTests deploy -Pnexus
```

根聚合 POM 设置了 `maven.deploy.skip=true`，不会作为构件发布。

# kset-dependencies

KSet 框架的依赖版本 BOM，统一管理 KSet 自身模块与全部第三方组件版本。

## 定位

- 不继承任何 Maven parent，可被任意项目 `import`。
- 只负责 `<dependencyManagement>`，不声明默认依赖，也不配置构建插件。
- `kset-parent`（非 Boot）与 `kset-boot-parent`（Boot）都会使用本 BOM。

## 使用方式

在业务工程的 `<dependencyManagement>` 中导入：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.kset</groupId>
            <artifactId>kset-dependencies</artifactId>
            <version>${kset-framework.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

导入后，即可无版本号声明 KSet 模块与受管第三方依赖。

## 版本基线

- Java 21
- Spring Boot `${spring-boot.version}`
- Spring Cloud `${spring-cloud.version}`
- Spring Cloud Alibaba `${spring-cloud-alibaba.version}`
- Dubbo `${dubbo.version}`

完整版本列表见 `pom.xml` 中的 `properties` 与 `dependencyManagement`。

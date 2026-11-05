# 业务服务引用规范

外部业务模块引用业务服务接口时，默认在 Spring 管理的 Bean 中使用 `@KsetReference`。注解由 `kset-common` 提供，业务代码不需要因这处引用而直接依赖 Dubbo。

```java
import com.kset.common.service.KsetReference;

@KsetReference
private OrderService orderService;
```

## 接入规则

1. `@KsetReference` 只用于非静态、非 `final` 的接口字段。同一字段不再叠加 `@Autowired` 或 `@DubboReference`。
2. 默认先从当前 Spring 容器选择本地实现；多个实现使用 Spring 的 `@Primary` 或字段上的 `@Qualifier` 消除歧义。`group`、`version` 不参与本地 Bean 选择。
3. 宿主机未引入 `kset-starter-dubbo`，或未配置 Dubbo 注册中心时，只使用本地实现。没有本地实现会在启动时失败并指出缺少可用服务。
4. 需要在本地实现缺失时引用远端，宿主机再引入 `kset-starter-dubbo` 并配置 `dubbo.registry.address`；可按服务契约填写 `group`、`version`，两者支持 Spring 配置占位符。同一接口、group、version 的远端引用会复用。
5. 服务选择只在启动时完成。新增 Dubbo 依赖、调整服务实现或配置后需要重启；调用失败时不会自动切换。本地实现仍存在时始终优先使用本地实现。
6. 启用 Dubbo 注册中心后，本地引用会在启动阶段按接口、group、version 检查提供者。其他进程暴露同名服务会使启动失败；当前进程自身导出的服务不算冲突。无法读取注册中心或提供者目录时也会启动失败。

远端服务示例：

```java
@KsetReference(group = "orders", version = "1.0")
private OrderService orderService;
```

`kset-starter-dubbo` 会在存在 Nacos 地址时补齐 `dubbo.registry.address`。使用 Nacos 配置、但暂不接入 Dubbo 的宿主机可显式设置 `dubbo.registry.address: N/A`，保持仅本地引用。

## 适用边界

- 数据源、配置对象、工具类等普通 Spring 依赖继续使用 Spring 原生注入方式；此规范针对业务服务接口引用。
- 确需固定本地实现、固定 Dubbo 引用或使用 `@KsetReference` 未提供的 Dubbo 专有参数时，可使用对应原生注入方式，并在业务模块的接入说明中记录原因。

模块细节见 [kset-common](../../kset-common/README.md#本地服务引用) 与 [kset-starter-dubbo](../../kset-starter-dubbo/README.md#本地--远程服务引用)。

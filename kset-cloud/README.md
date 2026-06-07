# KSet Cloud

`kset-cloud` 提供云服务 Starter 共享的公共配置、Nacos 命名约定、灰度标签解析、LoadBalancer 与规则 SPI。业务通常通过 `kset-starter-nacos`、`kset-starter-sentinel`、`kset-starter-dubbo`、`kset-starter-gateway` 间接引入。

## 主要入口

| 入口 | 用途 |
|------|------|
| `KsetCloudProperties` | `kset.cloud.*` 配置模型 |
| `NacosConfigConvention` | Nacos dataId、group、namespace 命名约定 |
| `CloudRuleProvider` | 接收 Sentinel、Dubbo、Gateway 规则变更 |
| `GrayTagResolver` | 从请求上下文解析灰度标签 |
| `KsetGrayLoadBalancer` | 基于灰度标签选择服务实例 |

## Nacos 命名约定

| 用途 | dataId 格式 |
|------|-------------|
| 应用配置 | `{app}.yaml` |
| 公共配置 | `kset-common.yaml` |
| Sentinel 限流规则 | `{app}-flow-rules` |
| Sentinel 熔断规则 | `{app}-degrade-rules` |
| Sentinel 热点规则 | `{app}-param-flow-rules` |
| Dubbo 路由规则 | `{app}-route-rules` |
| Gateway 路由 | `{gateway-app}-gateway-routes` |
| Gateway 限流规则 | `{gateway-app}-gateway-flow-rules` |

`namespace` / `group` 优先读取 Spring Cloud Nacos 标准配置，未配置时回退到 `kset.cloud.nacos.*`。

## 配置摘要

```yaml
kset:
  cloud:
    nacos:
      namespace: dev
      group: KSET_GROUP
      common-config-data-id: kset-common.yaml
    loadbalancer:
      gray-header: X-Gray-Tag
      metadata-key: version
    dubbo:
      gray-enabled: true
      gray-metadata-key: version
      default-gray-tag: stable
```

## 扩展规则监听

```java
@Component
public class OrderFlowRuleProvider implements CloudRuleProvider {
    @Override
    public CloudRuleType ruleType() {
        return CloudRuleType.SENTINEL_FLOW;
    }

    @Override
    public void onRuleChanged(String jsonContent) {
        // 处理规则变更
    }
}
```

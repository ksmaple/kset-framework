# cloud 配置载体下沉与 monitor 解耦 变更记录

- feature-key：`cloud-config-decouple`
- 当前 change-id：`cloud-config-decouple-v1`
- 当前状态：`active`
- 当前入口：`kset-starter-redis/.../config/KsetRedisProperties.java`、`kset-starter-datasource/.../config/KsetDatasourceProperties.java`、`kset-starter-monitor/.../config/KsetMonitorCloudCompat.java`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| cloud-config-decouple-v1 | active | 新包属性类 + KsetMonitorCloudCompat | cloud 寄放的属性类 | cloud 中 @Deprecated 旧类（未删） | 可用 | 新键稳定一个版本后，经用户授权删除 cloud 旧类 |

## cloud-config-decouple-v1

- 日期：2026-08-29
- 目标：解除 redis/datasource/monitor 对 kset-cloud 的"配置载体"依赖，cloud 收敛为纯云规则层
- 判定时间：2026-08-29 +08:00
- 上线状态：已上线（框架已发布至 1.0.14，存在业务消费方）
- 判定方式：自动判定（Nexus 发布记录）
- 判定证据：v1.0.12~v1.0.14 已发布 Nexus；kset-rag 已接入
- 证据来源：Nexus maven-releases、git tag
- 当时代码状态：基线 Git ref `bd2068a`；属性类寄放 `com.kset.cloud.config`，monitor 经 KsetCloudProperties 读灰度/链路配置
- 兼容选择：兼容增量（键不变 + 旧类保留 + monitor 旧键回退）
- 回滚目标：N/A（旧类未删，回退 import 即可）
- 基线 Git ref：`bd2068ae47b36b23425d628c79987cf98d990351`
- 结果 Git ref：N/A（未提交）
- 回滚来源：cloud 中 @Deprecated 旧类 + Git ref
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| kset-starter-redis/.../config/KsetRedisProperties.java | KsetRedisProperties | redis 配置自持（键不变） | add | active |
| kset-starter-datasource/.../config/KsetDatasourceProperties.java | KsetDatasourceProperties | datasource 配置自持（键不变） | add | active |
| kset-cloud/.../config/KsetRedisProperties.java / KsetDatasourceProperties.java | - | 兼容保留 | deprecate | active-compatible |
| kset-starter-monitor/.../config/KsetMonitorProperties.java | Dubbo/Gateway 新增字段 | 自持 tracePropagationEnabled/defaultGrayTag/traceHeader | modify | active |
| kset-starter-monitor/.../config/KsetMonitorCloudCompat.java | KsetMonitorCloudCompat | 自持键优先 + cloud 旧键回退解析 | add | active |
| monitor 三个 Filter 与装配类、monitor pom、redis pom、datasource pom | - | 构造改纯值注入，解除 cloud 依赖 | modify | active |

### 回滚步骤

1. 各 starter 恢复 import `com.kset.cloud.config.*` 并还原 pom 依赖；cloud 旧类去掉 @Deprecated。
2. monitor 恢复 KsetCloudProperties 注入（Git ref 可查）。

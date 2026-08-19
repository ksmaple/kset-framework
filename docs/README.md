# kset-framework 使用文档

按场景拆开的业务接入说明。抄这里的代码即可跑通；配置项、SPI、边界仍以各模块 `README.md` 为准。版本与依赖见仓库根 [README.md](../README.md)。

## 设计规范

尚未做成 Starter 的跨产品约定：

| 文档 | 说明 |
|------|------|
| [资源权限](design/resource-permission.md) | 功能码与资源 ACL 两平面、判定顺序、授权条目形状。本仓暂无实现 |

## 怎么选文档

| 我要做的事 | 文档 |
|------------|------|
| 统一 HTTP 响应 / 业务异常 | [web.md](usage/web.md) |
| Web 登录态、网关后 trusted-header | [auth.md](usage/auth.md) |
| 一批独立任务并行、限并发 | [parallel.md](usage/parallel.md) |
| HTTP / RPC 瞬时失败重试 | [retry.md](usage/retry.md) |
| 跨线程带上登录态 / Trace | [context.md](usage/context.md) |
| Redis 读写与强制 TTL | [redis.md](usage/redis.md) |
| Redis 分布式锁 | [redis-lock.md](usage/redis-lock.md) |
| 本地或 Redis 多级缓存 | [cache.md](usage/cache.md) |
| Spring 本地事件或 RocketMQ | [events.md](usage/events.md) |
| TraceId / 业务埋点 | [monitor.md](usage/monitor.md) |
| MyBatis-Plus 数据源 | [datasource.md](usage/datasource.md) |
| Nacos / Sentinel / Dubbo | [cloud.md](usage/cloud.md) |
| 独立 Gateway 鉴权、CORS、灰度 | [gateway.md](usage/gateway.md) |

## 模块参考

| 模块 | 文档 |
|------|------|
| 版本 BOM | [kset-boot-parent/README.md](../kset-boot-parent/README.md) |
| 公共工具 | [kset-common/README.md](../kset-common/README.md) |
| 云规范 / SPI | [kset-cloud/README.md](../kset-cloud/README.md) |
| Web | [kset-starter-web/README.md](../kset-starter-web/README.md) |
| Auth | [kset-starter-auth/README.md](../kset-starter-auth/README.md) |
| Monitor | [kset-starter-monitor/README.md](../kset-starter-monitor/README.md) |
| Datasource | [kset-starter-datasource/README.md](../kset-starter-datasource/README.md) |
| Cache | [kset-starter-cache/README.md](../kset-starter-cache/README.md) |
| Redis | [kset-starter-redis/README.md](../kset-starter-redis/README.md) |
| Nacos | [kset-starter-nacos/README.md](../kset-starter-nacos/README.md) |
| Sentinel | [kset-starter-sentinel/README.md](../kset-starter-sentinel/README.md) |
| Dubbo | [kset-starter-dubbo/README.md](../kset-starter-dubbo/README.md) |
| Gateway | [kset-starter-gateway/README.md](../kset-starter-gateway/README.md) |
| MQ | [kset-starter-mq/README.md](../kset-starter-mq/README.md) |

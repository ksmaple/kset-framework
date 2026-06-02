# 域协作与权威边界

## 权威来源（禁止双份全文）

| 主题 | 权威文件 |
|------|----------|
| 字段语义 | naming-spec.md |
| 聚合与领域事件语义 | ddd-spec.md |
| HTTP 对外契约 | api-spec.md |
| 表结构与 DDL | sql-spec.md |
| Outbox / 消息封装 | event-spec.md |
| DTO/Entity/PO 边界 | conversion-spec.md |
| 缓存 Key / TTL | cache-spec.md |
| 熔断 / 限流 / 429 | orchestration-spec.md |
| traceId / 脱敏 | log-spec.md |
| 编码纪律 / 反模式 | coding-spec.md |
| 修复策略 | fix-spec.md |
| CI / 灰度 / 容量 | engineering-spec.md |

业务项目差异项：`{proj}-coder/references/project-spec.md` 覆盖上表默认项。

## 推荐阅读顺序

```
naming → ddd → sql（持久化）/ api（接口）/ frontend（有端）
         ↘ event / cache / conversion / log / coding / orchestration（按需）
fix、engineering：修错或运维调参时单独打开
```

## 跨域口径

| 冲突点 | 统一口径 |
|--------|----------|
| HTTP 状态 | 对外应用 API：api 域 HTTP 200 + errCode；网关/服务间：orchestration 域可 429 |
| 幂等 POST 创建 | api A020：Idempotency-Key + 24h 去重 |
| 幂等 POST 更新 | api A021 + sql S016：乐观锁 ver |
| 逻辑删除 | naming N011 + sql S004：列 deleted，禁止 del |
| 乐观锁 | naming N008 + sql S005：列 ver，禁止 version |
| 领域事件名 | ddd D012 + event E002：{Entity}{Action}Event / eventType |
| 发消息与写库 | event E010/E011 + orchestration O004：须 Outbox |
| traceId | api A010 + log L001 + event E004：全链路一致 |
| 缓存返回集合 | cache K011 + coding C001：禁止原地变更，须拷贝或 new |
| 金额 | naming N012 + sql S007：BIGINT 分 |
| 工程调节幅度 | orchestration O006 + engineering G010：单次调整 ≤20% |

## 与项目技能分工

| 角色 | 职责 |
|------|------|
| kaka-coder-designer | 规范、门禁、设计产出形态 |
| {proj}-coder | 按 project-spec + 本 references 写源码 |
| {proj}-fixer | 按 fix-spec 最小修复，保持风格 |
| kaka-utils-project-init | 复制本技能整包、写 project-spec、生成 coder/fixer |

## 新模块设计

1. 读 `{proj}-coder/references/project-spec.md`
2. 按需打开本目录 `*-spec.md`（顺序见上表）
3. 产出 ddd/sql 文档（若项目约定）后由 `{proj}-coder` 实现；检查项见 [design-lint-spec.md](design-lint-spec.md)

## 代码落地（写码阶段）

| 文档 | 用途 |
|------|------|
| [codegen-task-spec.md](codegen-task-spec.md) | 任务拆分与依赖 DAG |
| [compile-check-spec.md](compile-check-spec.md) | 生成后编译校验 |
| [test-execution-spec.md](test-execution-spec.md) | 编译后可调用性测试 |

## 禁止

- 在 `{proj}-coder` 中重定义 naming 语义
- 在 ddd 文档写 SQL/API 映射表（见 ddd D009/D020）
- 未读 sql-dialect.json 混用多方言 DDL（sql S020）
- 事务提交后直接发 MQ（event E010）

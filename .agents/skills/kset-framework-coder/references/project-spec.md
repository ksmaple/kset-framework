# kset-framework 项目专属规范

> init 生成。优先级：本文档 > `.claude/skills/kaka-coder-designer/references/`。

**proj**：`kset-framework` · **backend** · 生成：2026-06-25 09:44:04

## 1. 画像

- coder / fixer：`kset-framework-coder`、`kset-framework-fixer`
- 技术栈：Java + Maven + Redis
- SQL：mysql
- API：默认按 Full POST + HTTP 200 + errCode

## 2. 规范整合（对应 kaka-coder-designer 各域）


| 主题            | 状态               | 实际做法                               | 域路径                              |
| ------------- | ---------------- | ---------------------------------- | -------------------------------- |
| Naming        | 待确认   | 待根据现有代码与业务术语确认；名称与入参/出参须简单易懂（N028） | references/naming-spec.md        |
| DDD           | 待确认      | 待根据现有模块与聚合划分确认                        | references/ddd-spec.md           |
| API           | 待确认      | 默认按 Full POST + HTTP 200 + errCode                        | references/api-spec.md           |
| Frontend      | 待确认 | 待根据前端框架与类型声明确认；无前端填 N/A                   | references/frontend-spec.md      |
| SQL           | 待确认      | 待根据 ORM、DDL 与 sql-dialect.json 确认                        | references/sql-spec.md           |
| Conversion    | 待确认     | 待根据 DTO/Entity/PO 转换实现确认                       | references/conversion-spec.md    |
| Event         | 待确认    | 待根据事件驱动实现确认；无事件链路填 N/A                      | references/event-spec.md         |
| Cache         | 待确认    | 待根据缓存注解或配置确认；无缓存填 N/A                      | references/cache-spec.md         |
| Orchestration | 待确认     | 待根据外部调用、熔断与限流实现确认；无编排填 N/A                       | references/orchestration-spec.md |
| Log           | 待确认      | 待根据日志配置、traceId 与脱敏策略确认                        | references/log-spec.md           |
| Engineering   | 按需               | 容量/熔断等                             | references/engineering-spec.md   |


## 3. 时间格式（前后端）

> init **必填**（INITSPEC012）。默认 wire：`yyyy-MM-dd HH:mm:ss`；后端 Jackson（api A029）；前端 string + dayjs `YYYY-MM-DD HH:mm:ss`（frontend F011）。与默认不一致须在差异项说明。

| 项 | 约定 | 探测来源 / 说明 |
| --- | --- | --- |
| API wire 格式 | yyyy-MM-dd HH:mm:ss | 默认 yyyy-MM-dd HH:mm:ss；禁止混用 ISO T 格式或裸 epoch |
| 时区策略 | Asia/Shanghai（默认；如与存量配置不一致请在差异项调整） | 默认 Asia/Shanghai；对应 spring.jackson.time-zone |
| Epoch 单位（若用） | N/A | 秒 / 毫秒；字段后缀如 AtMs |
| 后端序列化 | Jackson：date-format=yyyy-MM-dd HH:mm:ss + JavaTimeModule | 默认 Jackson：date-format=yyyy-MM-dd HH:mm:ss + JavaTimeModule |
| 前端日期库 | N/A | 如 dayjs，格式 YYYY-MM-DD HH:mm:ss；无前端填 N/A |
| SQL 列类型 | DATETIME | 如 DATETIME / TIMESTAMPTZ（sql S026） |
| 存量差异 | 无；如存量代码不一致请补充 | 与默认不一致时的迁移或并存说明 |

## 4. 差异项

- 待根据探测结果或用户确认补充项目差异

## 5. 命令


| 用途  | 命令                 |
| --- | ------------------ |
| 编译  | `mvn -q -DskipTests compile` |
| 测试  | `mvn -q test`    |

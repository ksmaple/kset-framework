# Event 域规范

E001: 须先论证为什么异步
E002: 类名 {Entity}{Action}Event，eventType {module}:{action}
E003: 外层 EventMessage 含 msgId、traceId、eventType、eventTime、source、version、payload
E004: msgId 用 UUID；traceId 与 log/API 一致
E005: payload 仅用 Long、String、Integer 等基础类型
E006: 禁止 payload 传递复杂对象与枚举
E007: 同步 EventListener 或异步 Async/MQ
E008: 失败须重试与 DLQ，禁止无限重试
E009: 禁止事件链循环
E010: 业务写库与发消息原子，须 Outbox，禁止事务提交后直接发 MQ
E011: Saga 发事件须 Outbox
E012: 消费者须声明幂等策略
E013: 领域内事件不跨限界上下文，跨边界用集成事件
E014: Schema 只增不删，破坏性变更升 version
E015: 不产出 design/event 文档；落地以 Event/Listener/Outbox 代码为准
E016: payload 字段名与 DDD 语义一致

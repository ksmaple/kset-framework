# DDD 域规范

D001: 一个文档只含一个限界上下文
D002: 领域名 kebab-case
D003: 跨领域关系仅在文末跨领域引用节，禁止内联他域逻辑
D004: 实体禁止跨文档重复定义；共享概念用 ID 或共享内核 VO
D005: 设计阶段语言无关表示法，禁止编程语言语法
D006: 持久化实体须含 id、createdAt、updatedAt、deleted 语义，文档可假设不逐字段重复
D007: 业务 ID 与 DB id 解耦；跨上下文用 entityId、bizId、code
D008: 实体/值对象/事件字段命名须对照 naming/spec.md
D009: DDD 文档只写语义字段，禁止 SQL 列名、DTO/URL、H5 types、Converter 映射
D010: 领域事件节只写业务语义：触发、消费者、负载字段名
D011: 消息封装与 Outbox 细则见 event 域
D012: 领域事件名 {Entity}{Action}Event，负载为语义字段名
D013: Ubiquitous Language 落英文命名时须满足 naming 规范
D014: 领域约定写入 project-spec 第三节或 ddd 域文档
D015: 代码由 proj-coder 实现，不强制 claude/design 目录
D016: 复杂或有争议设计须先与用户确认
D017: 领域层禁止依赖基础设施框架
D018: 仅字段语义争议走 naming 域；本域负责聚合边界与领域模型

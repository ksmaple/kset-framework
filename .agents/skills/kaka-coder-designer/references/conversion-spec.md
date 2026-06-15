# Conversion 域规范

V001: 转换边界 DTO、Entity、PO，禁止跨层泄漏
V002: 双层 Converter：DtoConverter 与 EntityConverter
V003: 禁止 Service 内大段 set/get 手工映射
V004: MapStruct 优先
V005: 命名 {Entity}Converter、{Entity}DtoConverter
V006: Entity 与 PO 须覆盖 DDD/SQL 语义字段
V007: 布尔 PO Integer 与 Entity Boolean 映射在 EntityConverter
V008: SQL is_ 列映射为 Entity 无 is 布尔属性
V009: 枚举映射为整型或字符串 code 与 Entity 一致；JSON 列用 String 存储须 Converter 显式序列化；禁止 DTO 嵌套 Entity
V010: 禁止在 Converter 重新定义字段业务含义
V011: 不产出 design/conversion 文档；落地以 Converter 代码为准
V012: Entity、DTO、PO 间 DateTime 转换须遵循 project-spec 的 wire 格式（默认 `yyyy-MM-dd HH:mm:ss`）与时区策略；禁止 Converter 内另定 SimpleDateFormat 模式且与 Jackson/前端不一致

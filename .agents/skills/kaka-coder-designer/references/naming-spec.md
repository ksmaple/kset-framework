# Naming 域规范

N001: 本域 N001-N028 为字段语义唯一真理源  
N002: 设计阶段使用语言无关驼峰语义名  
N003: 实现阶段仅做机械格式转换，禁止重新评估业务含义  
N004: 布尔使用形容词或过去分词，禁止 is 前缀（Entity/API/H5/JSON）  
N005: 通用概念须加业务限定符，如 userId、documentStatus  
N006: 基础设施字段 id、createdAt、updatedAt、deleted、ver、createdBy、updatedBy 为持久化实体标配  
N007: 主键语义 id 为库内自增；跨上下文用 entityId、bizId、code  
N008: 乐观锁语义 ver，禁止 version  
N009: 逻辑删除语义 deleted，取值 0 正常 1 删除；禁止缩写 del  
N010: SQL/PO 层 snake_case；Entity/DTO/API/H5 驼峰；URL 与表名全小写  
N011: 逻辑删除全层统一 deleted；SQL 列 deleted，允许 is_deleted 列 PO 映射为 deleted 属性  
N012: 金额语义 amount/price/balance，存储为分 BIGINT  
N013: 状态语义 statusCode 或 state，避免裸 status  
N014: 排序语义 sortOrder，避免裸 order  
N015: 外键语义 {entity}Id，如 userId、folderId  
N016: DDD 只引用语义，不写跨层映射表  
N017: 各域读 naming 与本域 spec 在本层完成格式映射  
N018: 各域设计前须已有 DDD 语义字段  
N019: 禁止在 API/SQL/H5 文档重新定义字段业务含义  
N020: 层间映射方向见 N010 与各域 spec；禁止 DDD 产出跨层对照表  
N021: Converter 命名 {Entity}Converter  
N022: 事件名 PascalCase 后缀 Event；缓存 Key 冒号分隔  
N023: 反模式：布尔 is 前缀、裸 name/type/status、浮点金额、Java 关键字作字段名、逻辑删除用 del 缩写  
N024: init 将 naming 探测写入 project-spec  
N025: 时间字段 createdAt、updatedAt 映射 SQL created_at、updated_at；审计人 createdBy、updatedBy 映射 created_by、updated_by  
N026: URL 路径连字符小写；Command 命名 {Action}Command；DTO 命名 {Entity}DTO 或 {Entity}{Action}DTO  
N027: 枚举语义 {Entity}{Attr}Enum 或 statusCode 整型；禁止裸 Enum 类名作字段语义  
N028: 方法名、API 操作名及入参/出参字段须采用业务人员可读的动词或名词短语，优先日常用语（如 findOrderByCode 优于 queryOrdByCd），避免非行业通用缩写与晦涩简写堆砌  

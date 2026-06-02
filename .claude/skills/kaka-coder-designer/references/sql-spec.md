# SQL 域规范

S001: 表名前缀 t_，全小写 snake_case
S002: 字段全小写 snake_case，禁止保留字作字段名
S003: 主键列名固定 id
S004: 逻辑删除列 deleted，0 正常 1 删除；禁止 del 列名
S005: 乐观锁列 ver，默认 0，禁止 version 列名
S006: 审计列 created_at、updated_at、created_by、updated_by
S007: 金额 BIGINT 存分，禁止浮点金额
S008: 状态 INT 或短 VARCHAR code
S009: 索引普通 idx_{table}_{field}，唯一 uk_{table}_{field}
S010: 列名从 DDD 语义机械映射 snake_case，禁止改义
S011: 产出路径 db/migration/NNN-{table}.sql 或 project-spec 约定
S012: 每个 DDL 文件头须声明 dialect 与 depends ddd 引用
S013: 写 DDL 前须读 sql-dialect.json
S014: 禁止触发器、存储过程、自定义函数、视图
S015: 禁止 DDL 内写业务逻辑
S016: 乐观锁更新须 WHERE ver 条件
S017: MySQL 字符集 utf8mb4，排序 utf8mb4_unicode_ci
S018: PostgreSQL 时间优先 TIMESTAMPTZ，注释用 COMMENT ON
S019: SQLite 主键 INTEGER PRIMARY KEY AUTOINCREMENT，无 ENGINE 与行内 COMMENT
S020: 禁止未读方言文件时混用 MySQL/PG/SQLite 语法
S021: project-spec SQL 差异项优先于本域默认
S022: sql 为少数可产出 design 文档的类型之一
S023: 业务查询默认过滤 deleted 等于 0，除非 project-spec 差异
S024: 外键列 {ref}_id，与 naming 外键语义 {entity}Id 机械映射
S025: ver 默认 0、deleted 默认 0；非空列须 NOT NULL 与 DEFAULT

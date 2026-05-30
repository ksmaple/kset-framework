-- KSet PostgreSQL 本地初始化脚本。
-- 来源：PostgreSQL 官方 docker-entrypoint-initdb.d 初始化机制。
-- 含义：容器首次创建数据目录时执行；默认数据库由 POSTGRES_DB 创建，这里只保留扩展与后续初始化占位。

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

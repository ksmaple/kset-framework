# CAT 本地 Docker 部署

CAT 已纳入 `env/up` 一键启动。本目录保留 CAT 镜像构建与 MySQL 初始化脚本。

## 相关配置

| 项 | 位置 |
|----|------|
| 客户端连接地址 | `env/config/cat/client.xml`（启动时自动同步） |
| 端口等环境变量 | `env/config/.env` |
| MySQL 建表脚本 | `mysql/init/01-CatApplication.sql` |

## 单独操作 CAT

```powershell
docker compose --env-file ../.env -f docker-compose.yml up -d --build
docker compose --env-file ../.env -f docker-compose.yml ps
docker compose --env-file ../.env -f docker-compose.yml logs -f cat
```

## 默认地址

| 组件 | 地址 |
|------|------|
| CAT 控制台 | http://127.0.0.1:8088/cat |
| CAT 上报 | `127.0.0.1:2280` |
| MySQL | `127.0.0.1:3307`，库名 `cat` |

## 使用外部 MySQL

注释 `docker-compose.yml` 中的 `mysql` 服务，并修改 `cat` 环境变量 `MYSQL_URL`、`MYSQL_PASSWD` 等。需先执行 `mysql/init/01-CatApplication.sql` 建表。

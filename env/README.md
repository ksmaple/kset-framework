# KSet 本地部署环境

本目录用于启动项目依赖的本地/测试中间件。**配置编辑入口在 `env/config/`**，启动脚本会自动同步到运行目录。

## 目录结构

```
env/
├── config/              # 配置源（改这里）
│   ├── .env.example     # 环境变量模板
│   ├── .env             # 可选本地覆盖（git 忽略）
│   └── cat/client.xml   # CAT 客户端连接配置
├── sync-config.ps1      # 配置同步（Windows）
├── sync-config.sh       # 配置同步（Linux/macOS/Git Bash）
├── up.ps1 / up.sh       # 一键启动
├── down.ps1 / down.sh   # 一键停止
├── docker-compose.yml   # Nacos / Redis / PostgreSQL
└── cat/                 # CAT Server + 内置 MySQL
```

## 一键启动

首次使用：

```powershell
Copy-Item env/config/.env.example env/config/.env
# 按需编辑 env/config/.env、env/config/cat/client.xml
```

启动全部中间件（含 CAT）：

```powershell
.\env\up.ps1
# 或 Linux/macOS: ./env/up.sh
```

仅启动基础中间件（不含 CAT）：

```powershell
.\env\up.ps1 -NoCat
```

重新构建 CAT 镜像：

```powershell
.\env\up.ps1 -Build
```

停止：

```powershell
.\env\down.ps1
```

## 配置同步规则

执行 `up` 前会自动运行 `sync-config`，将 `env/config/` 复制到：

| 配置源 | 目标 |
|--------|------|
| `config/.env` 或 `.env.example` | `env/.env` |
| `config/cat/client.xml` | `env/cat/appdatas/client.xml` |
| `config/cat/client.xml` | `kset-demo/env/cat/client.xml` |
| `config/cat/client.xml` | `data/appdatas/cat/client.xml` |

修改配置后重新执行 `.\env\up.ps1` 或单独运行 `.\env\sync-config.ps1` 即可生效。

## 组件清单

| 组件 | 容器名 | 默认地址 | 应用配置 |
|------|--------|----------|----------|
| Nacos | `kset-nacos` | http://127.0.0.1:8848/nacos | `kset-demo/env/component-nacos.yml` |
| Redis | `kset-redis` | `127.0.0.1:6379` | `kset-demo/env/component-redis.yml` |
| PostgreSQL | `kset-postgres` | `127.0.0.1:5432` | `kset-demo/env/component-pgsql.yml` |
| CAT Server | `kset-cat` | http://127.0.0.1:8088/cat | `kset-demo/env/component-monitor.yml` |
| CAT Client | 无容器 | `127.0.0.1:2280` | `data/appdatas/cat/client.xml` |

默认 demo 数据库使用 SQLite，PostgreSQL 为可选替换。

## 默认账号

| 组件 | 用户名 | 密码 |
|------|--------|------|
| Nacos | 无 | 无 |
| Redis | 无 | 无 |
| PostgreSQL | `postgres` | `postgres` |
| CAT 控制台 | `admin` | `admin` |
| CAT MySQL | `root` | 无（端口 `3307`） |

## 应用连接示例

启用 CAT 时叠加 `component-monitor.yml`，客户端配置由 sync 写入 `data/appdatas/cat/client.xml`：

```powershell
mvn -pl kset-demo/demo-standalone-service spring-boot:run "-Dspring-boot.run.arguments=--spring.config.import=optional:file:./kset-demo/env/application-global.yml,optional:file:./kset-demo/env/component-sqlite.yml,optional:file:./kset-demo/env/component-redis.yml,optional:file:./kset-demo/env/component-monitor.yml --spring.application.name=demo-standalone-service --server.port=8080"
```

CAT 详情见 [cat/README.md](cat/README.md)。

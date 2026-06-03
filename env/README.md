# KSet 公共环境配置与启动说明

`env` 目录统一维护项目本地部署需要的公共组件环境。Docker 镜像版本和端口统一在 `env/.env.example` 管理；Maven 依赖版本仍由 `kset-parent/pom.xml` 管理。所有容器运行数据统一挂载到宿主机 `/data`。

## 目录结构

```text
env/
├── .env.example              # 公共组件镜像版本、端口、账号模板
├── .env                      # 本地覆盖，git 忽略
├── docker-compose.yml        # Nacos / PostgreSQL / Redis / RocketMQ
├── script/                   # 环境启动、停止、同步脚本
│   ├── up.ps1 / up.sh
│   ├── down.ps1 / down.sh
│   └── sync.ps1 / sync.sh
├── postgres/init/            # PostgreSQL 初始化脚本
├── rocketmq/broker.conf      # RocketMQ Broker 本地配置
└── cat/
    ├── docker-compose.yml    # CAT Server + 内置 MySQL
    ├── Dockerfile            # CAT Server 镜像构建
    ├── datasources.sh        # 容器启动时生成 CAT 服务端配置
    ├── client/client.xml     # CAT 客户端连接配置源
    └── server/
        ├── cat-home.war      # CAT Server 应用包
        └── init_cat.sql      # CAT MySQL 初始化脚本
```

## 组件版本矩阵

| 组件 | 镜像变量 | 默认镜像 | 默认端口 | 数据目录 | 说明 |
|------|----------|----------|----------|----------|------|
| Nacos | `NACOS_IMAGE` | `nacos/nacos-server:v3.2.0` | `8080`、`8848`、`9848`、`9849` | `/data/nacos/` | 注册发现与配置中心；3.x 控制台端口为 `8080` |
| PostgreSQL | `POSTGRES_IMAGE` | `pgvector/pgvector:0.8.2-pg17` | `5432` | `/data/postgres/` | PostgreSQL 17 + pgvector，demo 默认数据库 |
| Redis | `REDIS_IMAGE` | `redis:7.4.9-alpine` | `6379` | `/data/redis/` | 缓存、锁、排行榜 |
| RocketMQ | `ROCKETMQ_IMAGE` | `apache/rocketmq:5.5.0` | `9876`、`10911`、`10909`、`8081`、`18080` | `/data/rocketmq/` | MQ 标准组件；开启 Proxy，应用侧 v5 client 使用 `8081` |
| CAT MySQL | `CAT_MYSQL_IMAGE` | `mysql:5.7.37` | `3307` | `/data/cat/mysql/` | CAT Server 内置数据库，跟随 CAT 服务端兼容性暂不升级 |
| CAT Server | 本地构建 | `kset-cat:local` | `8088`、`2280` | `/data/appdatas/cat/` | 监控服务端 |

版本调整只改 `env/.env` 或 `env/.env.example`，不要在 compose 文件里散落写死版本。

升级约束：

- Nacos 3.x 将控制台从 `8848/nacos` 调整为独立 `8080` 端口；应用侧注册发现和配置中心仍使用 `127.0.0.1:8848`。
- Nacos 3.x 要求 `NACOS_AUTH_TOKEN` 为 Base64 且解码后不少于 32 字节；本地模板已提供默认值，生产环境必须替换。
- PostgreSQL 镜像使用 `pgvector/pgvector:0.8.2-pg17`。PostgreSQL 16 升级到 17 属于大版本升级，已有 `/data/postgres` 数据目录需要先备份并通过 dump/restore 或 `pg_upgrade` 迁移；全新本地环境可直接启动。
- RocketMQ 镜像使用 `apache/rocketmq:5.5.0`，并通过 `ROCKETMQ_VERSION=5.5.0` 统一控制容器内脚本路径；Broker 启动参数开启 `--enable-proxy`，v5 Spring Client 默认连接 `127.0.0.1:8081`。
- CAT 保持现有 Server WAR 与 MySQL 5.7.37，避免牵动 CAT 服务端表结构和初始化脚本。

## 首次准备

脚本会在 `env/.env` 不存在时自动从 `env/.env.example` 初始化。也可以手动复制后修改：

```powershell
Copy-Item env/.env.example env/.env
```

Linux/macOS/Git Bash：

```bash
cp env/.env.example env/.env
```

## 启动

启动全部公共组件和 CAT：

```powershell
.\env\script\up.ps1
```

Linux/macOS/Git Bash：

```bash
./env/script/up.sh
```

仅启动公共基础组件，不启动 CAT：

```powershell
.\env\script\up.ps1 -NoCat
```

```bash
./env/script/up.sh --no-cat
```

重新构建 CAT 镜像并启动：

```powershell
.\env\script\up.ps1 -Build
```

```bash
./env/script/up.sh --build
```

基础组件包含 Nacos、PostgreSQL、Redis、RocketMQ。CAT 作为监控服务端，通过 `cat/docker-compose.yml` 合并启动。

## 停止

停止全部环境：

```powershell
.\env\script\down.ps1
```

```bash
./env/script/down.sh
```

仅停止基础组件，不包含 CAT：

```powershell
.\env\script\down.ps1 -NoCat
```

```bash
./env/script/down.sh --no-cat
```

## 配置同步

启动脚本会先执行同步逻辑：

| 配置源 | 同步目标 |
|--------|----------|
| `env/.env.example` | `env/.env`，仅当 `.env` 不存在时初始化 |
| `env/cat/client/client.xml` | `/data/appdatas/cat/client.xml` |

单独同步：

```powershell
.\env\script\sync.ps1
```

```bash
./env/script/sync.sh
```

## 数据目录

Docker 挂载统一使用宿主机 `/data`：

| 组件 | 数据目录 |
|------|----------|
| Nacos | `/data/nacos/data/`、`/data/nacos/logs/` |
| PostgreSQL | `/data/postgres/` |
| Redis | `/data/redis/` |
| RocketMQ | `/data/rocketmq/namesrv/`、`/data/rocketmq/broker/` |
| CAT Server | `/data/appdatas/cat/` |
| CAT MySQL | `/data/cat/mysql/` |

`docker compose down` 只停止容器，不会删除 `/data` 下的数据。需要重置时，停止容器后手动删除对应目录。

PostgreSQL 初始化脚本只在 `/data/postgres` 首次创建时执行。若本机已有 PostgreSQL 16 数据目录，不能直接使用 PostgreSQL 17 镜像启动，需要先迁移或重置数据目录。迁移后需要确认扩展存在：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

RocketMQ 官方镜像默认使用 `uid=3000` 运行，Docker 自动创建的 `/data/rocketmq` 目录通常归 `root`。本地环境 compose 已固定 RocketMQ 以 `root` 启动，避免日志和 store 目录无写权限导致 broker 静默退出；生产环境如需更严格权限，应在部署脚本中预创建目录并授权给运行用户。

## 默认地址

| 组件 | 地址 |
|------|------|
| Nacos 控制台 | http://127.0.0.1:8080 |
| Nacos 客户端/API | `127.0.0.1:8848` |
| PostgreSQL | `127.0.0.1:5432` |
| Redis | `127.0.0.1:6379` |
| RocketMQ NameServer | `127.0.0.1:9876` |
| RocketMQ Broker | `127.0.0.1:10911` |
| RocketMQ Proxy gRPC | `127.0.0.1:8081` |
| RocketMQ Proxy remoting | `127.0.0.1:18080` |
| CAT Server | http://127.0.0.1:8088/cat |
| CAT TCP 上报 | `127.0.0.1:2280` |
| CAT MySQL | `127.0.0.1:3307` |

CAT 详细说明见 [cat/README.md](cat/README.md)。

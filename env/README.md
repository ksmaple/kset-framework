# 本地 Docker 公共组件

本目录的 [Compose 配置](compose.yaml)用于在开发机启动 Nacos、Redis、MySQL、PostgreSQL 和 RocketMQ。它不部署业务应用，也不初始化业务库、Nacos 命名空间、配置项或 RocketMQ Topic。命令均在仓库根目录执行；需要 Docker Desktop 的 Linux 容器和 Docker Compose 插件。

## 使用约定

1. 仅用于本机开发。所有宿主机映射端口绑定 `127.0.0.1`，各组件未开启鉴权；不得直接用于共享、测试或生产环境，也不要把端口改为对外监听后沿用这套无鉴权配置。
2. Compose 项目名为 `kset-local`，使用独立的 `kset-local_*` Docker 卷。不要复用其他项目的卷，也不要在日常停机时删除卷；重建或升级前先确认数据是否需要备份。
3. MySQL 保持项目的 8.0 兼容基线；其他镜像版本以 `compose.yaml` 为准。升级镜像须先核对项目客户端兼容性及现有数据的迁移方式。
4. 本机运行的应用使用下表的 `127.0.0.1` 地址；加入此 Compose 网络的容器使用服务名地址。业务库、Nacos 命名空间和 RocketMQ Topic 由对应应用按需建立。

| 组件 | 本机地址 | Compose 网络内地址 | 存储方式 |
| --- | --- | --- | --- |
| Nacos 2.5.2 | `127.0.0.1:8848`，控制台 `http://127.0.0.1:8848/nacos/` | `nacos:8848` | 独立模式内置存储；不使用下方 MySQL |
| Redis 7.4.9 | `127.0.0.1:16379` | `redis:6379` | AOF 持久化 |
| MySQL 8.0 | `127.0.0.1:3306` | `mysql:3306` | `utf8mb4`、`+08:00`；用户 `root`，空密码 |
| PostgreSQL 17 | `127.0.0.1:5432` | `postgres:5432` | 用户和默认库均为 `postgres`；本机开发采用 `trust` 鉴权 |
| RocketMQ 5.5 NameServer | `127.0.0.1:9876` | `rocketmq-namesrv:9876` | 无持久化数据 |
| RocketMQ 5.5 V5 Proxy | `127.0.0.1:8081` | `rocketmq-broker:8081` | Broker 与 Proxy 同进程；消息存储在独立卷 |

Nacos 客户端还需连接 `9848` gRPC 端口。当前 Windows 预留了 `6379` 所在端口段，所以 Redis 的本机映射为 `16379`；容器内部端口仍是 `6379`。RocketMQ 对本机应用只开放项目所用 V5 客户端的 Proxy 端口 `8081`，Broker 的 Remoting 端口未映射到宿主机。应用配置示例见 [Nacos 接入](../docs/usage/cloud.md)、[Redis 接入](../docs/usage/redis.md)、[数据源接入](../docs/usage/datasource.md)和[RocketMQ 接入](../kset-starter-mq/README.md)。

## 内存配置

以下是容器内存上限，并非启动时预留量。数据库缓存和 JVM 初始堆另行调小，以适合本机轻量使用；消息量或并发量增加后可能需要提高对应上限。

| 服务 | 内存上限 | 主要参数 |
| --- | --- | --- |
| MySQL | 384 MiB | InnoDB Buffer Pool 64 MiB、最多 30 连接、关闭 Performance Schema |
| Redis | 128 MiB | 保持默认数据行为，空闲占用很小 |
| Nacos | 640 MiB | JVM 初始堆 128 MiB、最大堆 256 MiB |
| PostgreSQL | 256 MiB | Shared Buffers 32 MiB、最多 30 连接 |
| RocketMQ NameServer | 256 MiB | JVM 初始堆 64 MiB、最大堆 128 MiB |
| RocketMQ Broker + Proxy | 768 MiB | JVM 初始堆 128 MiB、最大堆 512 MiB、Direct Memory 上限 128 MiB |

空闲启动后可用 `docker stats --no-stream` 查看实际占用。`rocketmq-store-init` 只负责设置消息卷权限，成功后退出，不常驻内存。

## 启动与核验

```powershell
docker compose -f env/compose.yaml up -d --wait
docker compose -f env/compose.yaml ps
```

`ps` 中六个常驻服务均应显示 `healthy`。`docker compose -f env/compose.yaml ps -a` 中的 `rocketmq-store-init` 应显示 `Exited (0)`。需要进一步定位时可执行：

```powershell
docker compose -f env/compose.yaml exec -T redis redis-cli PING
docker compose -f env/compose.yaml exec -T mysql mysql -uroot -N -e "SELECT VERSION();"
docker compose -f env/compose.yaml exec -T postgres psql -U postgres -Atc "SELECT version();"
(Invoke-WebRequest -Uri 'http://127.0.0.1:8848/nacos/actuator/health' -UseBasicParsing).StatusCode
docker compose -f env/compose.yaml exec -T rocketmq-broker sh /home/rocketmq/rocketmq-5.5.0/bin/mqadmin clusterList -n rocketmq-namesrv:9876
docker compose -f env/compose.yaml logs --since 10m --tail 100 rocketmq-broker
```

预期分别为 `PONG`、MySQL 8.0 与 PostgreSQL 17 版本号、Nacos HTTP `200`，以及 RocketMQ 集群列表中的 `broker-a`。日志命令可换成其他服务名，并按排查范围缩短时间窗及行数。

## 停止与数据

```powershell
docker compose -f env/compose.yaml stop
docker compose -f env/compose.yaml start
docker compose -f env/compose.yaml down
```

`stop` 与 `down` 都保留数据卷；再次 `up -d --wait` 会使用原数据。`down --volumes` 会删除这套环境的 MySQL、Redis、Nacos、PostgreSQL 和 RocketMQ 消息数据卷，不属于日常停机操作。端口占用时先核对占用进程和当前映射，再修改本机端口；修改后同步更新本文中的连接地址。

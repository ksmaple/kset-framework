# 本地 Docker 公共组件

本目录使用 [单容器 Compose](compose.yaml)在本机运行 Nacos、Redis、MySQL、PostgreSQL 和 RocketMQ。一个容器内有多个服务进程，不包含业务应用，也不预置业务库、Nacos 命名空间、配置项或 RocketMQ Topic。命令均在仓库根目录执行，需要 Docker Desktop 的 Linux 容器和 Docker Compose 插件。

## 使用约定

1. 仅用于本机开发。所有宿主机端口绑定 `127.0.0.1`，组件未开启鉴权；不得用于共享、测试或生产环境。
2. 单容器 Compose 项目名为 `kset-local-one`，数据保存在 `kset-local-one_*` 卷。正常停止或重建容器不会删除卷。不要使用 `down --volumes` 作为日常停机命令。
3. MySQL 为 8.0，PostgreSQL 为 17，其他组件版本见下表。镜像升级前须核对现有数据格式和客户端兼容性。
4. 本机应用连接 `127.0.0.1`；加入本 Compose 网络的容器可使用下表别名。一个容器内任一关键进程退出会停止其余进程并由 Docker 重启整个容器，不能独立重启某个组件。

| 组件 | 本机地址 | Compose 网络内地址 | 存储方式 |
| --- | --- | --- | --- |
| Nacos 2.5.2 | `127.0.0.1:8848`，控制台 `http://127.0.0.1:8848/nacos/` | `nacos:8848` | 独立模式内置存储；不使用下方 MySQL |
| Redis 7.4.9 | `127.0.0.1:16379` | `redis:6379` | AOF 持久化 |
| MySQL 8.0 | `127.0.0.1:3306` | `mysql:3306` | `utf8mb4`、`+08:00`；用户 `root`，空密码 |
| PostgreSQL 17 | `127.0.0.1:5432` | `postgres:5432` | 用户和默认库均为 `postgres`；本机开发采用 `trust` 鉴权 |
| RocketMQ 5.5 NameServer | `127.0.0.1:9876` | `rocketmq-namesrv:9876` | 无持久化数据 |
| RocketMQ 5.5 V5 Proxy | `127.0.0.1:8081` | `rocketmq-broker:8081` | Broker 与 Proxy 同进程；消息存储在独立卷 |

Nacos 客户端还使用 `9848` gRPC 端口。Windows 当前预留了 `6379` 所在端口段，因此 Redis 的本机端口为 `16379`。RocketMQ 只向宿主机开放项目所用 V5 Proxy 端口 `8081`，不映射 Broker 的 Remoting 端口。应用配置示例见 [Nacos 接入](../docs/usage/cloud.md)、[Redis 接入](../docs/usage/redis.md)、[数据源接入](../docs/usage/datasource.md)和 [RocketMQ 接入](../kset-starter-mq/README.md)。

## 内存配置

容器总内存上限为 2 GiB，不是启动时预留量。MySQL Buffer Pool 为 64 MiB、最多 30 连接并关闭 Performance Schema；PostgreSQL Shared Buffers 为 32 MiB、最多 30 连接；Nacos 堆为 128–256 MiB；RocketMQ NameServer 堆为 64–128 MiB，Broker + Proxy 堆为 128–512 MiB、Direct Memory 上限为 128 MiB。Redis 保持默认数据行为。可用 `docker stats --no-stream kset-local-infra` 查看实际占用；业务数据或并发增加时需重新评估上限。

## 新环境启动与核验

```powershell
docker compose -f env/compose.yaml up -d --build --wait
docker compose -f env/compose.yaml ps
docker stats --no-stream kset-local-infra
```

`ps` 应只显示 `kset-local-infra` 一个常驻容器，状态为 `healthy`。后续启动可省略 `--build`。逐项检查：

```powershell
docker exec kset-local-infra /usr/local/bin/kset-local-healthcheck
docker exec kset-local-infra mysql -uroot -N -e "SELECT VERSION();"
docker exec kset-local-infra psql -U postgres -Atc "SHOW server_version;"
docker exec kset-local-infra bash -c "LD_LIBRARY_PATH=/opt/redis/lib redis-cli PING"
(Invoke-WebRequest -Uri 'http://127.0.0.1:8848/nacos/actuator/health' -UseBasicParsing).StatusCode
docker exec kset-local-infra gosu rocketmq bash /home/rocketmq/rocketmq-5.5.0/bin/mqadmin clusterList -n 127.0.0.1:9876
docker logs --since 10m --tail 100 kset-local-infra
```

预期为健康检查退出码 `0`、MySQL 8.0、PostgreSQL 17、Redis `PONG`、Nacos HTTP `200` 和 RocketMQ 集群列表中的 `broker-a`。日志按排查范围缩短时间窗和行数。

## 历史配置与数据

[旧版 Compose](compose.legacy.yaml)仅保留配置参考。旧 `kset-local` 容器和五个 `kset-local_*` 数据卷已于 2026-11-06 删除，无法再回退到迁移前数据。当前持久化数据仅在 `kset-local-one_*` 卷中。迁移时新卷由复制命令创建，没有 Compose 标签；后续 `up` 可能提示卷并非由 Compose 创建，这不影响运行。

## 停止与数据

```powershell
docker compose -f env/compose.yaml stop
docker compose -f env/compose.yaml start
docker compose -f env/compose.yaml down
```

`stop` 和 `down` 均保留当前数据卷；再次 `up -d --wait` 会使用同一份数据。删除 `kset-local-one_*` 卷会清除当前 MySQL、Redis、Nacos、PostgreSQL 和 RocketMQ 数据，执行前须单独确认。

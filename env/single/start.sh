#!/usr/bin/env bash
set -Eeuo pipefail

pids=()

start() {
    local name="$1"
    shift
    echo "[kset-local] starting ${name}"
    setsid "$@" &
    pids+=("$!")
}

stop_all() {
    trap - TERM INT
    for pid in "${pids[@]}"; do
        kill -TERM -- "-${pid}" 2>/dev/null || true
    done
    wait 2>/dev/null || true
}

on_signal() {
    stop_all
    exit 0
}

trap on_signal TERM INT

mkdir -p /var/lib/postgresql/data /data /home/nacos/data /home/rocketmq/store /home/rocketmq/logs
chown -R postgres:postgres /var/lib/postgresql/data
chown -R redis:redis /data
chown -R rocketmq:rocketmq /home/rocketmq/store /home/rocketmq/logs

if [[ ! -s /var/lib/postgresql/data/PG_VERSION ]]; then
    gosu postgres /usr/pgsql-17/bin/initdb -D /var/lib/postgresql/data \
        --username=postgres --auth-local=trust --auth-host=trust
fi

start mysql docker-entrypoint.sh mysqld \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_0900_ai_ci \
    --default-time-zone=+08:00 \
    --innodb-buffer-pool-size=64M \
    --max-connections=30 \
    --table-open-cache=256 \
    --performance-schema=OFF

start postgres gosu postgres /usr/pgsql-17/bin/postgres -D /var/lib/postgresql/data \
    -c listen_addresses='*' \
    -c shared_buffers=32MB \
    -c max_connections=30 \
    -c work_mem=2MB \
    -c maintenance_work_mem=32MB \
    -c timezone=Asia/Shanghai

start redis gosu redis env LD_LIBRARY_PATH=/opt/redis/lib \
    /usr/local/bin/redis-server --appendonly yes --dir /data \
    --bind 0.0.0.0 --protected-mode no

start nacos env JAVA=/opt/java/openjdk/bin/java \
    bash /home/nacos/bin/docker-startup.sh

start rocketmq-namesrv gosu rocketmq env \
    JAVA_HOME=/opt/java/openjdk \
    HEAP_OPTS='-Xms64m -Xmx128m -Xmn32m' \
    bash -c 'cd /home/rocketmq/rocketmq-5.5.0/bin && exec ./mqnamesrv'

namesrv_ready=false
for attempt in {1..60}; do
    if (echo > /dev/tcp/127.0.0.1/9876) 2>/dev/null; then
        namesrv_ready=true
        break
    fi
    sleep 2
done
if [[ "${namesrv_ready}" != true ]]; then
    echo "[kset-local] rocketmq-namesrv did not become ready" >&2
    stop_all
    exit 1
fi

start rocketmq-broker gosu rocketmq env \
    JAVA_HOME=/opt/java/openjdk \
    HEAP_OPTS='-Xms128m -Xmx512m -Xmn64m -XX:MaxDirectMemorySize=128m' \
    bash -c 'cd /home/rocketmq/rocketmq-5.5.0/bin && exec ./mqbroker -n 127.0.0.1:9876 --enable-proxy'

if wait -n "${pids[@]}"; then
    status=1
else
    status=$?
fi
echo "[kset-local] component exited; stopping remaining processes"
stop_all
exit "$status"

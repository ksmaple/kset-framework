#!/usr/bin/env bash
set -euo pipefail

mysql -uroot -h127.0.0.1 -N -e 'SELECT 1' >/dev/null
/usr/pgsql-17/bin/pg_isready -h 127.0.0.1 -U postgres >/dev/null
LD_LIBRARY_PATH=/opt/redis/lib redis-cli -h 127.0.0.1 ping | grep -qx PONG
curl -fsS http://127.0.0.1:8848/nacos/actuator/health >/dev/null
(echo > /dev/tcp/127.0.0.1/9848) 2>/dev/null
(echo > /dev/tcp/127.0.0.1/9876) 2>/dev/null
(echo > /dev/tcp/127.0.0.1/8081) 2>/dev/null

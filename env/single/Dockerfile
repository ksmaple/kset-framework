FROM redis:7.4.9-alpine AS redis
FROM nacos/nacos-server:v2.5.2 AS nacos
FROM apache/rocketmq:5.5.0 AS rocketmq

FROM mysql:8.0

RUN microdnf install -y shadow-utils util-linux procps-ng libxcrypt-compat && \
    rpm -Uvh https://download.postgresql.org/pub/repos/yum/reporpms/EL-9-x86_64/pgdg-redhat-repo-latest.noarch.rpm && \
    microdnf install -y postgresql17-server postgresql17 && \
    microdnf clean all && \
    groupadd -g 3000 rocketmq && useradd -u 3000 -g 3000 -d /home/rocketmq rocketmq && \
    groupadd -g 3001 redis && useradd -u 3001 -g 3001 -d /data redis

RUN microdnf install -y glibc-langpack-en && microdnf clean all

COPY --from=redis /usr/local/bin/redis-server /usr/local/bin/redis-server
COPY --from=redis /usr/local/bin/redis-cli /usr/local/bin/redis-cli
COPY --from=redis /lib/ld-musl-x86_64.so.1 /lib/ld-musl-x86_64.so.1
COPY --from=redis /usr/lib/libssl.so.3 /opt/redis/lib/libssl.so.3
COPY --from=redis /usr/lib/libcrypto.so.3 /opt/redis/lib/libcrypto.so.3
COPY --from=nacos /home/nacos /home/nacos
COPY --from=rocketmq /opt/java/openjdk /opt/java/openjdk
COPY --from=rocketmq /home/rocketmq/rocketmq-5.5.0 /home/rocketmq/rocketmq-5.5.0
COPY single/which.sh /usr/local/bin/which
COPY single/start.sh /usr/local/bin/kset-local-start
COPY single/healthcheck.sh /usr/local/bin/kset-local-healthcheck

RUN chmod +x /usr/local/bin/which /usr/local/bin/kset-local-start /usr/local/bin/kset-local-healthcheck && \
    mkdir -p /home/rocketmq/store /home/rocketmq/logs /data /var/lib/postgresql/data && \
    chown -R 3000:3000 /home/rocketmq && chown -R 3001:3001 /data

ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:/usr/pgsql-17/bin:${PATH} \
    BASE_DIR=/home/nacos \
    MODE=standalone \
    NACOS_AUTH_ENABLE=false \
    JVM_XMS=128m \
    JVM_XMX=256m \
    JVM_XMN=64m \
    TZ=Asia/Shanghai \
    MYSQL_ALLOW_EMPTY_PASSWORD=yes

ENTRYPOINT ["/usr/local/bin/kset-local-start"]

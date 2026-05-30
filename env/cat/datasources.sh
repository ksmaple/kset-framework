#!/usr/bin/env bash
set -e

CAT_HOME=${CAT_HOME:-/data/appdatas/cat}
MYSQL_URL=${MYSQL_URL:-mysql}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_USERNAME=${MYSQL_USERNAME:-root}
MYSQL_PASSWD=${MYSQL_PASSWD:-}
MYSQL_SCHEMA=${MYSQL_SCHEMA:-cat}
CAT_SERVERS=${CAT_SERVERS:-${SERVER_IP:-127.0.0.1}}
CAT_TCP_PORT=${CAT_TCP_PORT:-2280}
CAT_HTTP_PORT=${CAT_HTTP_PORT:-8080}
CAT_LOCAL_MODE=${CAT_LOCAL_MODE:-false}
CAT_JOB_MACHINE=${CAT_JOB_MACHINE:-false}
CAT_ALERT_MACHINE=${CAT_ALERT_MACHINE:-false}
CAT_HDFS_MACHINE=${CAT_HDFS_MACHINE:-false}
CAT_STORAGE_DIR=${CAT_STORAGE_DIR:-${CAT_HOME}/bucket/}

mkdir -p "${CAT_HOME}"

escape_xml() {
  printf '%s' "$1" \
    | sed -e 's/&/\&amp;/g' \
          -e 's/</\&lt;/g' \
          -e 's/>/\&gt;/g' \
          -e 's/"/\&quot;/g' \
          -e "s/'/\&apos;/g"
}

server_ip() {
  printf '%s' "$1" | awk -F: '{print $1}'
}

server_tcp_port() {
  local value
  value=$(printf '%s' "$1" | awk -F: '{print $2}')
  printf '%s' "${value:-$CAT_TCP_PORT}"
}

server_http_port() {
  local value
  value=$(printf '%s' "$1" | awk -F: '{print $3}')
  printf '%s' "${value:-$CAT_HTTP_PORT}"
}

write_datasources_xml() {
  cat > "${CAT_HOME}/datasources.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>

<data-sources>
    <data-source id="cat">
        <maximum-pool-size>${MYSQL_MAX_POOL_SIZE:-3}</maximum-pool-size>
        <connection-timeout>${MYSQL_CONNECTION_TIMEOUT:-1s}</connection-timeout>
        <idle-timeout>${MYSQL_IDLE_TIMEOUT:-10m}</idle-timeout>
        <statement-cache-size>${MYSQL_STATEMENT_CACHE_SIZE:-1000}</statement-cache-size>
        <properties>
            <driver>${MYSQL_DRIVER:-com.mysql.jdbc.Driver}</driver>
            <url><![CDATA[jdbc:mysql://${MYSQL_URL}:${MYSQL_PORT}/${MYSQL_SCHEMA}]]></url>
            <user>$(escape_xml "${MYSQL_USERNAME}")</user>
            <password>$(escape_xml "${MYSQL_PASSWD}")</password>
            <connectionProperties><![CDATA[${MYSQL_CONNECTION_PROPERTIES:-useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&socketTimeout=120000}]]></connectionProperties>
        </properties>
    </data-source>
</data-sources>
EOF
}

write_client_xml() {
  if [ -f "${CAT_HOME}/client.xml" ]; then
    echo "client.xml already exists, skip generation"
    return
  fi

  cat > "${CAT_HOME}/client.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<config mode="client">
    <servers>
EOF

  IFS=',' read -ra servers <<< "${CAT_SERVERS}"
  for server in "${servers[@]}"; do
    ip=$(server_ip "$server")
    tcp_port=$(server_tcp_port "$server")
    http_port=$(server_http_port "$server")
    if [ -n "$ip" ]; then
      cat >> "${CAT_HOME}/client.xml" <<EOF
        <server ip="$(escape_xml "$ip")" port="$(escape_xml "$tcp_port")" http-port="$(escape_xml "$http_port")"/>
EOF
    fi
  done

  cat >> "${CAT_HOME}/client.xml" <<EOF
    </servers>
</config>
EOF
}

write_server_xml() {
  remote_servers=""
  IFS=',' read -ra servers <<< "${CAT_SERVERS}"
  for server in "${servers[@]}"; do
    ip=$(server_ip "$server")
    http_port=$(server_http_port "$server")
    if [ -n "$ip" ]; then
      if [ -n "$remote_servers" ]; then
        remote_servers="${remote_servers},"
      fi
      remote_servers="${remote_servers}${ip}:${http_port}"
    fi
  done

  cat > "${CAT_HOME}/server.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<config local-mode="${CAT_LOCAL_MODE}" hdfs-machine="${CAT_HDFS_MACHINE}" job-machine="${CAT_JOB_MACHINE}" alert-machine="${CAT_ALERT_MACHINE}">
    <storage local-base-dir="${CAT_STORAGE_DIR}" max-hdfs-storage-time="${CAT_MAX_HDFS_STORAGE_TIME:-15}" local-report-storage-time="${CAT_LOCAL_REPORT_STORAGE_TIME:-7}" local-logivew-storage-time="${CAT_LOCAL_LOGVIEW_STORAGE_TIME:-7}"/>
    <console default-domain="${CAT_DEFAULT_DOMAIN:-Cat}" show-cat-domain="${CAT_SHOW_CAT_DOMAIN:-true}">
        <remote-servers>$(escape_xml "$remote_servers")</remote-servers>
    </console>
</config>
EOF
}

write_datasources_xml
write_client_xml
write_server_xml

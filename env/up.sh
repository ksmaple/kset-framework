#!/usr/bin/env bash
# 一键启动本地中间件（Nacos / Redis / PostgreSQL / CAT）
set -euo pipefail

BUILD=false
NO_CAT=false

for arg in "$@"; do
  case "${arg}" in
    --build) BUILD=true ;;
    --no-cat) NO_CAT=true ;;
    *)
      echo "未知参数: ${arg}"
      echo "用法: env/up.sh [--build] [--no-cat]"
      exit 1
      ;;
  esac
done

ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ENV_DIR}"

bash "${ENV_DIR}/sync-config.sh"

if [[ ! -f "${ENV_DIR}/.env" ]]; then
  echo "缺少 env/.env，请先执行: cp env/config/.env.example env/config/.env" >&2
  exit 1
fi

COMPOSE=(docker compose --env-file .env -f docker-compose.yml)
if [[ "${NO_CAT}" != "true" ]]; then
  COMPOSE+=(-f cat/docker-compose.yml)
fi

UP=("${COMPOSE[@]}" up -d)
if [[ "${BUILD}" == "true" ]]; then
  UP+=(--build)
fi

echo "docker ${UP[*]}"
"${UP[@]}"
"${COMPOSE[@]}" ps

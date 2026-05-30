#!/usr/bin/env bash
# 停止本地中间件
set -euo pipefail

NO_CAT=false
VOLUMES=false

for arg in "$@"; do
  case "${arg}" in
    --no-cat) NO_CAT=true ;;
    --volumes) VOLUMES=true ;;
    *)
      echo "未知参数: ${arg}"
      echo "用法: env/down.sh [--no-cat] [--volumes]"
      exit 1
      ;;
  esac
done

ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ENV_DIR}"

COMPOSE=(docker compose)
if [[ -f "${ENV_DIR}/.env" ]]; then
  COMPOSE+=(--env-file .env)
fi
COMPOSE+=(-f docker-compose.yml)

if [[ "${NO_CAT}" != "true" ]]; then
  COMPOSE+=(-f cat/docker-compose.yml)
fi

DOWN=("${COMPOSE[@]}" down)
if [[ "${VOLUMES}" == "true" ]]; then
  DOWN+=(-v)
fi

echo "docker ${DOWN[*]}"
"${DOWN[@]}"

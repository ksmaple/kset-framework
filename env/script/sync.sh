#!/usr/bin/env bash
# 同步公共环境需要的运行配置。
set -euo pipefail

QUIET=false
if [[ "${1:-}" == "--quiet" ]]; then
  QUIET=true
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${ENV_DIR}/.." && pwd)"

info() {
  if [[ "${QUIET}" != "true" ]]; then
    echo "$1"
  fi
}

sync_file() {
  local source="$1"
  local target="$2"
  local src="${REPO_ROOT}/${source}"
  local dst="${REPO_ROOT}/${target}"

  if [[ "${target}" == /* ]]; then
    dst="${target}"
  fi

  if [[ ! -f "${src}" ]]; then
    info "skip (missing): ${source}"
    return
  fi

  mkdir -p "$(dirname "${dst}")"
  cp -f "${src}" "${dst}"
  info "sync: ${source} -> ${target}"
}

if [[ ! -f "${ENV_DIR}/.env" && -f "${ENV_DIR}/.env.example" ]]; then
  cp -f "${ENV_DIR}/.env.example" "${ENV_DIR}/.env"
  info "init: env/.env.example -> env/.env"
fi

sync_file "env/cat/client/client.xml" "/data/appdatas/cat/client.xml"

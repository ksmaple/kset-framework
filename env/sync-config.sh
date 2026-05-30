#!/usr/bin/env bash
# 将 env/config 下的配置同步到运行目录
set -euo pipefail

QUIET=false
if [[ "${1:-}" == "--quiet" ]]; then
  QUIET=true
fi

ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${ENV_DIR}/.." && pwd)"
CONFIG_DIR="${ENV_DIR}/config"

info() {
  if [[ "${QUIET}" != "true" ]]; then
    echo "$1"
  fi
}

ensure_dir() {
  mkdir -p "$1"
}

sync_file() {
  local relative_src="$1"
  local relative_dst="$2"
  local src="${CONFIG_DIR}/${relative_src}"
  local dst="${REPO_ROOT}/${relative_dst}"

  if [[ ! -f "${src}" ]]; then
    info "skip (missing): ${relative_src}"
    return
  fi

  ensure_dir "$(dirname "${dst}")"
  cp -f "${src}" "${dst}"
  info "sync: config/${relative_src} -> ${relative_dst}"
}

ENV_TARGET="${ENV_DIR}/.env"
ENV_OVERRIDE="${CONFIG_DIR}/.env"
ENV_EXAMPLE="${CONFIG_DIR}/.env.example"

if [[ -f "${ENV_OVERRIDE}" ]]; then
  cp -f "${ENV_OVERRIDE}" "${ENV_TARGET}"
  info "sync: config/.env -> env/.env"
elif [[ ! -f "${ENV_TARGET}" && -f "${ENV_EXAMPLE}" ]]; then
  cp -f "${ENV_EXAMPLE}" "${ENV_TARGET}"
  info "init: config/.env.example -> env/.env"
fi

sync_file "cat/client.xml" "env/cat/appdatas/client.xml"
sync_file "cat/client.xml" "kset-demo/env/cat/client.xml"
sync_file "cat/client.xml" "data/appdatas/cat/client.xml"

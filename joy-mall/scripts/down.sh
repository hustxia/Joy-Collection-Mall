#!/usr/bin/env bash
# V6 D-03：停止并移除容器（保留数据卷；清库用 reset.sh）
set -euo pipefail

cd "$(dirname "$0")/.."
docker compose down "$@"

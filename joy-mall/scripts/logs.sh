#!/usr/bin/env bash
# V6 D-03：跟随全部容器日志（默认最近 200 行）
# 用法：bash scripts/logs.sh [服务名]  例如 bash scripts/logs.sh mall-order
set -euo pipefail

cd "$(dirname "$0")/.."
docker compose logs -f --tail=200 "$@"

#!/usr/bin/env bash
# V6 D-03：清空数据卷（数据库 + 上传文件）后重建整套环境
# 注意：会删除所有业务数据，仅用于演示/自测环境
set -euo pipefail

cd "$(dirname "$0")/.."

read -r -p "确认清空数据库与上传文件并重建？输入 yes 继续：" answer
if [ "$answer" != "yes" ]; then
    echo "已取消"
    exit 0
fi

docker compose down -v
bash scripts/up.sh

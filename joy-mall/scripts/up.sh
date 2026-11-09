#!/usr/bin/env bash
# V6 D-03：打包 + 构建镜像 + 拉起整套环境，并等到网关健康
# 用法：bash scripts/up.sh
set -euo pipefail

cd "$(dirname "$0")/.."

echo "[1/3] 打包后端（跳过测试）…"
mvn -f be/mall-cloud/pom.xml -DskipTests -q package

echo "[2/3] 构建镜像并启动…"
docker compose up -d --build

echo "[3/3] 等待网关健康聚合（最多 180s）…"
for _ in $(seq 1 60); do
    body=$(curl -fsS http://127.0.0.1:8080/health/aggregate 2>/dev/null || true)
    if echo "$body" | grep -q '"status":"UP"'; then
        echo "全部服务 UP：$body"
        echo "后台 http://127.0.0.1:9431  PC http://127.0.0.1:9432  H5 http://127.0.0.1:9531"
        exit 0
    fi
    sleep 3
done

echo "等待超时，最近日志："
docker compose logs --tail=80
exit 1

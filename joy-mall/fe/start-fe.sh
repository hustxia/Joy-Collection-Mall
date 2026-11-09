#!/usr/bin/env bash
# 本地启动悦集商城三个前端(dev server)，API 全部代理到网关 http://127.0.0.1:8080
#   mall-web (后台 admin) -> http://localhost:9431
#   mall-pc  (PC 商城)     -> http://localhost:9432
#   mall-shop(H5 移动端)   -> http://localhost:9531
# 关键：npm 必须是本脚本的直接子进程，wait 才能等住，后台任务才不退出
FE=/d/workspace/project/fe
mkdir -p "$FE/logs"
cd "$FE/mall-web" && nohup npm run serve > "$FE/logs/mall-web.log" 2>&1 &
PID1=$!
echo "mall-web(admin) pid $PID1 -> 9431"
cd "$FE/mall-pc" && nohup npm run serve > "$FE/logs/mall-pc.log" 2>&1 &
PID2=$!
echo "mall-pc pid $PID2 -> 9432"
cd "$FE/mall-shop" && nohup npm run dev > "$FE/logs/mall-shop.log" 2>&1 &
PID3=$!
echo "mall-shop(h5) pid $PID3 -> 9531"
echo "all fe launching... waiting on pids $PID1 $PID2 $PID3"
wait

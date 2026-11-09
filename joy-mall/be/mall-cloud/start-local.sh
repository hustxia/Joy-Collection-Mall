#!/usr/bin/env bash
# 本地启动悦集商城 9 微服务 + 网关（不依赖 Docker）
# V10 加 merchant(8207)，V11/V14 加 support(8208)、message(8209)
# 关键：显式 --server.port 覆盖 jar 内被改写的端口(原 57962 冲突)
set -e
JAVA=/d/tool/jdk-21.0.12.1+1/bin/java
BASE=/d/workspace/project/be/mall-cloud
cd "$BASE"
mkdir -p uploads logs
# name=port:jar
SVC="mall-gateway:8080:mall-gateway-1.0.0.jar
mall-system:8201:mall-system-1.0.0.jar
mall-goods:8202:mall-goods-1.0.0.jar
mall-member:8203:mall-member-1.0.0.jar
mall-order:8204:mall-order-1.0.0.jar
mall-marketing:8205:mall-marketing-1.0.0.jar
mall-refund:8206:mall-refund-1.0.0.jar
mall-merchant:8207:mall-merchant-1.0.0.jar
mall-support:8208:mall-support-1.0.0.jar
mall-message:8209:mall-message-1.0.0.jar
mall-ai:8210:mall-ai-1.0.0.jar"
while IFS=: read -r name port jar; do
  [ -z "$name" ] && continue
  jp="$BASE/$name/target/$jar"
  if [ ! -f "$jp" ]; then echo "ERROR: missing $jp"; continue; fi
  nohup "$JAVA" -Xms128m -Xmx512m \
    -DDB_USERNAME=root -DDB_PASSWORD=root \
    -DAUTH_CAPTCHA_EXPOSE_ANSWER=true \
    -DFILE_UPLOAD_DIR="$BASE/uploads" \
    -Dserver.port="$port" \
    -jar "$jp" --server.port="$port" \
    > "$BASE/logs/$name.log" 2>&1 &
  echo "started $name on :$port (pid $!)"
done <<< "$SVC"
echo "============================================"
echo "gateway=8080 system=8201 goods=8202 member=8203"
echo "order=8204 marketing=8205 refund=8206 merchant=8207"
echo "support=8208 message=8209 ai=8210"
echo "keeping process alive (wait) so services stay up..."
wait

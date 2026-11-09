# V6 D-01：mall-cloud 服务通用镜像（分层 jar）
#
# 构建上下文固定为 be/mall-cloud，用 JAR_FILE 指定要打进镜像的模块产物：
#   docker build -f Dockerfile --build-arg JAR_FILE=mall-goods/target/mall-goods-1.0.0.jar .
#
# 分层（spring-boot layertools）的意义：依赖层只在 pom 变化时失效，
# 改业务代码只重建最后的 application 层，本地重建通常几秒。
FROM eclipse-temurin:21-jre AS builder
ARG JAR_FILE
WORKDIR /app
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"
# curl 供 compose healthcheck 使用（镜像本身只多几 MB）
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r mall \
 && useradd -r -g mall mall \
 && mkdir -p /app/uploads \
 && chown -R mall:mall /app
WORKDIR /app
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./
USER mall
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

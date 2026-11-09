# V6 D-02：三端静态产物 + 反向代理
# 构建上下文为仓库根目录：docker build -f deploy/nginx/Dockerfile .
#
# 三端各自独立端口（与本地 dev 端口一致，冒烟脚本零改动）：
#   9431 后台管理（mall-web，Vue CLI）
#   9432 PC 商城（mall-pc）
#   9531 移动端 H5（mall-shop，Vite）
FROM node:20-alpine AS web
WORKDIR /src
COPY fe/mall-web/package*.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY fe/mall-web/ ./
RUN npm run build

FROM node:20-alpine AS pc
WORKDIR /src
COPY fe/mall-pc/package*.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY fe/mall-pc/ ./
RUN npm run build

FROM node:20-alpine AS h5
WORKDIR /src
COPY fe/mall-shop/package*.json ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY fe/mall-shop/ ./
RUN npm run build

FROM nginx:1.25-alpine
COPY deploy/nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=web /src/dist /usr/share/nginx/html/admin
COPY --from=pc /src/dist /usr/share/nginx/html/pc
COPY --from=h5 /src/dist /usr/share/nginx/html/h5

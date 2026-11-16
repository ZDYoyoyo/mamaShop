# syntax=docker/dockerfile:1
#
# 正式環境的 Nginx 映像檔：前端網頁＋反向代理（D03）
#   第 1 階段 build：用 Node 把 Vue 專案建置成靜態檔（dist/）
#   第 2 階段：只放 Nginx、設定檔和 dist/，不含 Node 與原始碼
#
# 建置 context 是 repo 根目錄（docker-compose.prod.yml 指定），所以路徑都從根目錄寫起。

# ===== 第 1 階段：建置前端 =====
FROM node:22-alpine AS build
WORKDIR /app

# 先只複製 package*.json 安裝依賴：沒改依賴時這層用快取，不用每次重新下載
COPY frontend/package.json frontend/package-lock.json ./
# npm ci：完全照 package-lock.json 安裝（不會改版本），適合建置
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ===== 第 2 階段：Nginx =====
FROM nginx:1.27-alpine

# HTTP 的設定（預設）。TLS_MODE=https 時，啟動時會用下面的範本產生 HTTPS 的設定蓋掉它（D04）
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY deploy/nginx/https.conf.template /etc/nginx/templates-https/default.conf.template
# 兩種模式共用的片段
COPY deploy/nginx/site.conf deploy/nginx/security-headers.conf deploy/nginx/acme-and-health.conf /etc/nginx/snippets/
COPY --from=build /app/dist /usr/share/nginx/html

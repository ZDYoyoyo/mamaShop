# mamaShop

家人實際使用的電商網站：前台給顧客購物，後台給家人管理商品與訂單。

> 🚧 開發中，目前進度請見 [`docs/PLAN.md`](docs/PLAN.md)。

## 技術棧

- 後端：Java 21、Spring Boot 3、Spring Data JPA、PostgreSQL、Flyway、Spring Security＋JWT
- 前端：Vue 3、Vite、Vue Router、Pinia、Axios、Element Plus
- 部署：Docker Compose、Nginx、Let's Encrypt、GitHub Actions

## 目錄結構

```
mamaShop/
├── backend/           # Spring Boot
├── frontend/          # Vue 3（前台與後台同一個 app，用路由區分）
├── deploy/            # 正式環境的 docker-compose、Nginx 設定、備份腳本
├── docs/PLAN.md       # 專案計畫與任務清單
├── CLAUDE.md          # AI 協作規則
├── .env.example       # 環境變數範本（複製成 .env）
└── docker-compose.yml # 本機開發用
```

## 本機啟動

需要：Java 21、Docker（Maven 不用另外裝，用專案內附的 `mvnw`）

```bash
# 1. 建立本機設定檔，視需要修改密碼
cp .env.example .env

# 2. 啟動 PostgreSQL
docker compose up -d db

# 3. 啟動後端（http://localhost:8080）
cd backend && ./mvnw spring-boot:run
```

後端測試需要資料庫在執行中：`cd backend && ./mvnw test`

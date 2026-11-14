# mamaShop

家人實際使用的電商網站：前台給顧客購物，後台給家人管理商品與訂單。

> 🚧 開發中，目前進度請見 [`docs/PLAN.md`](docs/PLAN.md)。

## 技術棧

- 後端：Java 21、Spring Boot 4、Spring Data JPA、PostgreSQL、Flyway、Spring Security＋JWT
- 快取與訊息佇列：Redis（商品快取、防重複下單）、RabbitMQ（訂單通知信、延遲任務、失敗佇列）
- 金流與物流：綠界 ECPay（信用卡線上付款、超商電子地圖選店、超商物流單與狀態通知）
- 前端：Vue 3、Vite、Vue Router、Pinia、Axios、Element Plus
- 部署：Docker Compose、Nginx、Let's Encrypt、GitHub Actions

每個任務的設計理由、和計畫不同的決定、踩到的坑，都記在 [`docs/NOTES.md`](docs/NOTES.md)。

## 目錄結構

```
mamaShop/
├── backend/           # Spring Boot
├── frontend/          # Vue 3（前台與後台同一個 app，用路由區分）
├── deploy/            # 正式環境：docker-compose.prod.yml、Nginx（nginx/）、主機設定（server/）、檢查腳本（scripts/）
├── loadtest/         # k6 壓力測試腳本與基準數據（R00）
├── docs/PLAN.md       # 專案計畫與任務清單
├── docs/NOTES.md      # 每個任務的設計理由、刻意沒做的、之後要注意的事
├── docs/deploy/       # 部署操作指南（D01 起，每個指令都有說明）
├── CLAUDE.md          # AI 協作規則
├── .env.example       # 環境變數範本（複製成 .env）
└── docker-compose.yml # 本機開發用
```

## 本機啟動

需要：Java 21、Docker（Maven 不用另外裝，用專案內附的 `mvnw`）

```bash
# 1. 建立本機設定檔，修改密碼並產生 JWT_SECRET（openssl rand -base64 48）
cp .env.example .env

# 2. 啟動後端需要的服務：PostgreSQL、Redis、RabbitMQ、Mailpit（本機的假寄信伺服器）
docker compose up -d db redis rabbitmq mailpit

# 3. 啟動後端（http://localhost:8080）
cd backend && ./mvnw spring-boot:run
```

後端測試需要上面第 2 步的服務在執行中：`cd backend && ./mvnw test`

`.env.example` 裡的綠界設定（`ECPAY_*`、`ECPAY_LOGISTICS_*`）是綠界**公開的測試商店**，不會真的扣款或寄件：結帳可以選「線上付款」（測試卡號 `4311-9522-2222-2222`、安全碼 `222`）、超商取貨可以用電子地圖選門市。不想用就把 `ECPAY_ENABLED`、`ECPAY_LOGISTICS_ENABLED` 改成 `false`。本機收不到綠界的付款／物流通知（綠界連不到 localhost），模擬通知的指令在 `docs/NOTES.md` 的 P01、P03。

啟動時會依 `.env` 的 `ADMIN_EMAIL`／`ADMIN_PASSWORD` 建立預設管理員（已存在則略過）。`.env` 設定 `APP_PROFILE=dev` 時會自動載入測試商品。想把本機資料庫清空重建：`docker compose down -v`（`-v` 會刪除資料庫的 volume），再重新 `docker compose up -d db redis`。Redis 是商品快取（R01），沒啟動網站也能用，但快取的測試需要它。RabbitMQ、Mailpit 是訂單通知（M01）：寄出的信在 http://localhost:8025 看（不會真的寄出），RabbitMQ 的管理介面在 http://localhost:15672。

### 用 Docker 啟動整套服務

不需要在本機裝 Java，只要 Docker：

```bash
cp .env.example .env          # 同上，修改密碼與 JWT_SECRET
docker compose up --build     # 建置後端映像檔並啟動所有服務（資料庫、Redis、RabbitMQ、Mailpit、後端）
```

- 後端：http://localhost:8080（只綁定本機）
- 上傳的商品圖片存在 Docker volume `uploads-data`，重建容器不會消失
- 改了程式碼要重新 `docker compose up --build`；開發時建議用上面的 `mvnw spring-boot:run`，比較快
- 兩種方式都用 8080 port，不要同時開

### API 文件（Swagger UI）

`.env` 設定 `API_DOCS_ENABLED=true` 後啟動後端，打開 http://localhost:8080/swagger-ui.html

1. 在「會員：註冊與登入」呼叫登入 API，複製回傳的 `accessToken`
2. 按右上角 **Authorize**，貼上 token（不用加 `Bearer`）
3. 標有鎖頭的 API 就會自動帶上 token，可以直接按 Try it out 測試

正式環境不要開啟（API 文件會列出所有後台 API）。

### 前端

需要 Node.js 22 以上。後端要先啟動（上面任一種方式）。

```bash
cd frontend
npm ci          # 第一次或依賴變更後
npm run dev     # http://localhost:5173
```

詳見 [`frontend/README.md`](frontend/README.md)。

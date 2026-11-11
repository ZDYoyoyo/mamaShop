# 全端電商專案計畫

目標：做出一個家人能實際使用、已上線的電商網站，同時作為求職作品集。
前台給顧客購物，後台給家人管理商品與訂單，後端用 Spring Boot，部署到雲端 Linux 主機並啟用 HTTPS。

先求「能上線、能用」，再加 Redis 與 RabbitMQ。上線後的真實使用經驗，比功能多更有說服力。

---

## 開工前先和家人確認

這些答案會影響資料表與流程，先問清楚再開始 B02：

- [ ] 賣什麼商品？商品需不需要規格（尺寸、顏色、口味）？
- [ ] 運送方式：宅配、超商取貨、面交？運費怎麼算？
- [ ] 付款方式：銀行轉帳、貨到付款，還是需要信用卡線上付款？
- [ ] 需不需要開發票？
- [ ] 誰負責管理後台？大概多久處理一次訂單？

> 本計畫預設：無商品規格、宅配固定運費、付款用「銀行轉帳」與「貨到付款」，由家人在後台確認收款。
> 信用卡線上金流需要向金流業者申請商店帳號，開發票與蒐集顧客個資也有法規要求，上線前請以官方資訊為準，必要時諮詢專業人士。

---

## 技術選型

| 層 | 選擇 |
| --- | --- |
| 後端 | Java 21、Spring Boot 3.x、Maven、Spring Data JPA、PostgreSQL、Flyway、Spring Security＋JWT |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Axios、Element Plus（後台元件） |
| 測試 | JUnit 5、Mockito、MockMvc |
| 部署 | Docker Compose、Nginx（靜態檔＋反向代理）、Let's Encrypt、GitHub Actions |
| 之後加入 | Redis、RabbitMQ |

前端選 Vue 是因為上手門檻低、台灣職缺常見，Element Plus 也很適合做後台。

## Repo 結構

```
shop/
├── backend/           # Spring Boot
├── frontend/          # Vue 3（前台與後台同一個 app，用路由區分）
├── deploy/            # 正式環境的 docker-compose、Nginx 設定、備份腳本
├── docs/PLAN.md
├── CLAUDE.md
├── docker-compose.yml # 本機開發用
└── README.md
```

---

## 資料表設計

**users**：`id`、`email` UNIQUE、`password_hash`、`name`、`phone`、`role`（`USER`／`ADMIN`）、`created_at`、`updated_at`

**categories**：`id`、`name`、`sort_order`

**products**：`id`、`category_id` FK、`name`、`description`、`price` NUMERIC(10,2)、`stock` INT CHECK (`stock >= 0`)、`image_path`、`status`（`ON_SALE`／`OFF_SALE`）、`created_at`、`updated_at`

**orders**
- `id`、`order_no` UNIQUE、`user_id` FK
- `items_amount`、`shipping_fee`、`total_amount` NUMERIC(10,2)
- `recipient_name`、`recipient_phone`、`shipping_address`、`note`
- `payment_method`（`BANK_TRANSFER`／`COD`）
- `transfer_last5`（顧客回填的轉帳帳號後五碼）
- `tracking_no`（物流單號）
- `status`（`PENDING_PAYMENT`／`PAID`／`SHIPPED`／`COMPLETED`／`CANCELLED`）
- `created_at`、`paid_at`、`shipped_at`、`completed_at`、`cancelled_at`

**order_items**：`id`、`order_id` FK、`product_id` FK、`product_name`（快照）、`unit_price`（快照）、`quantity`、`subtotal`

**訂單狀態流程**
- 銀行轉帳：`PENDING_PAYMENT` →（顧客回填後五碼，家人確認入帳）→ `PAID` → `SHIPPED` → `COMPLETED`
- 貨到付款：建立後直接視為待出貨（`PAID` 之前不需確認），出貨 → 送達後 `COMPLETED`
- 只有 `PENDING_PAYMENT`（轉帳）或尚未出貨的貨到付款訂單可取消，取消時歸還庫存

> 面試重點：價格快照、狀態機集中檢查、取消時歸還庫存要和建立訂單一樣在交易內完成。

---

## 第 1 階段：後端核心

每個任務開一個分支（例如 `feature/b05-product-api`），完成後發 PR 再合併。

- [ ] **B00 建立 repo**：monorepo 結構、`.gitignore`、放入本檔與 `CLAUDE.md`
- [ ] **B01 初始化後端**：start.spring.io 產生 `backend/`；本機 `docker-compose.yml` 先放 PostgreSQL；設定讀環境變數。完成條件：能啟動並連上資料庫
- [ ] **B02 Schema**：Flyway `V1__init_schema.sql` 建表、外鍵、唯一索引；`V2__seed_data.sql` 放測試資料。完成條件：啟動後自動建表
- [ ] **B03 Entity 與 Repository**：狀態用 Enum（`EnumType.STRING`），`ddl-auto=validate`
- [ ] **B04 共用基礎**：統一回應格式、自訂例外、全域例外處理、參數驗證錯誤訊息
- [ ] **B05 商品查詢 API**：分類列表、商品列表（分頁、分類、關鍵字）、商品詳情，只回 DTO
- [ ] **B06 會員與 JWT**：註冊（BCrypt）、登入回 JWT、JWT Filter、路徑權限、預設 ADMIN 帳號（密碼讀環境變數）、CORS 允許本機前端。完成條件：未登入 401、權限不足 403
- [ ] **B07 後台商品管理＋圖片上傳**：分類 CRUD、商品新增／修改／上下架；圖片存到伺服器目錄，限制檔案類型與大小，檔名用 UUID。完成條件：上傳後前台能顯示圖片
- [ ] **B08 建立訂單（建議自己寫）**：一個交易內完成檢查商品 → 條件更新扣庫存（`WHERE stock >= :qty`）→ 建立訂單與明細 → 計算運費與總額。完成條件：庫存不足時整筆回滾
- [ ] **B09 顧客訂單功能**：我的訂單、訂單詳情（只能看自己的）、回填轉帳後五碼、取消訂單（歸還庫存）
- [ ] **B10 後台訂單管理**：訂單列表（狀態篩選、分頁）、確認收款、出貨（填物流單號）、完成
- [ ] **B11 測試**：`OrderService` 單元測試、Controller 權限與參數測試、多執行緒搶購併發測試（庫存 10、搶 50 次，驗證成功 10 筆）
- [ ] **B12 Docker 化**：多階段 `Dockerfile`、compose 加入後端、`.env.example`
- [ ] **B13 API 文件**：springdoc-openapi，Swagger UI 可帶 JWT 測試

## 第 2 階段：前台

- [ ] **F01 初始化前端**：Vite 建立 Vue 3 專案；Vue Router、Pinia、Axios；Axios 攔截器自動帶 JWT、401 時導回登入；Vite proxy 轉發 `/api` 到後端
- [ ] **F02 商品瀏覽**：首頁、分類與搜尋、商品列表（分頁）、商品詳情
- [ ] **F03 購物車**：Pinia 管理，存在 localStorage；加入、改數量、移除、小計
- [ ] **F04 會員**：註冊、登入、登出、需登入頁面的路由守衛
- [ ] **F05 結帳**：填收件資訊、選付款方式、送出訂單；轉帳訂單顯示匯款資訊
- [ ] **F06 我的訂單**：列表、詳情、回填後五碼、取消
- [ ] **F07 手機版面**：家人和顧客多半用手機，所有頁面都要在手機上好用
- [ ] **F08 體驗細節**：載入狀態、錯誤訊息、空狀態（沒有商品、沒有訂單）

## 第 3 階段：後台

- [ ] **A01 後台框架**：`/admin` 路由、Element Plus 版面、只有 ADMIN 能進
- [ ] **A02 商品管理**：列表、新增／編輯（含圖片上傳）、上下架、庫存調整
- [ ] **A03 分類管理**
- [ ] **A04 訂單管理**：依狀態篩選、查看明細、確認收款、出貨、完成
- [ ] **A05 首頁概覽**：今日訂單數、待確認收款、待出貨、低庫存商品

> 後台是家人每天要用的，介面以「看得懂、不會按錯」為優先，先請家人試用再調整。

## 第 4 階段：上線

- [ ] **D01 主機**：租一台 Linux VPS；SSH 金鑰登入、關閉密碼登入；防火牆只開 22、80、443
- [ ] **D02 網域**：購買網域、設定 DNS 指向主機
- [ ] **D03 正式環境部署**：`deploy/docker-compose.prod.yml` 包含 Nginx、後端、PostgreSQL；Nginx 提供前端靜態檔並把 `/api` 轉給後端；資料庫不對外開放；伺服器上的 `.env` 不進版控
- [ ] **D04 HTTPS**：Let's Encrypt 憑證與自動續期
- [ ] **D05 備份**：每日 `pg_dump` 與圖片目錄備份、保留天數、複製到主機以外的地方；**實際做一次還原測試**
- [ ] **D06 CI**：GitHub Actions 在 PR 時自動跑測試與前端建置；之後可加自動部署
- [ ] **D07 基本監控**：Spring Boot Actuator 健康檢查、Docker log 輪替、外部服務定期檢查網站是否正常
- [ ] **D08 上線檢查**：更換預設管理員密碼、隱私權政策頁面、用手機完整走一次下單到出貨、請家人試用一週再正式開放

> 這一階段是補強 Linux 的最好機會。遇到的每個問題（連不上、權限錯誤、憑證失敗）都記下來，面試時就是真實的排查經驗。

## 第 5 階段：Redis

- [ ] **R01 商品快取**：商品列表與詳情加快取，後台修改時清除對應快取
- [ ] **R02 防重複下單**：結帳頁取得一次性 token，送出訂單時用 Redis 確保同一個 token 只能成功一次（對照 Mission 的資料庫唯一索引做法）
- [ ] **R03 限時搶購（選做）**：用 Redis 預扣庫存，再非同步寫入資料庫

## 第 6 階段：RabbitMQ

- [ ] **M01 訂單通知**：下單、確認收款、出貨時，透過佇列非同步寄 Email 給顧客，寄信失敗不影響下單
- [ ] **M02 逾時自動取消**：轉帳訂單超過期限未付款，透過 TTL＋死信佇列自動取消並歸還庫存
- [ ] **M03 失敗處理**：訊息重試與失敗佇列，確保通知不會默默遺失

> 誠實面對規模：家人的網站流量不大，Redis 與 RabbitMQ 主要是為了學習與預備擴展。README 裡直接這樣寫，面試官反而會覺得你判斷清楚。

---

## README 要寫的內容

- 專案簡介與線上網址、測試帳號（另開一個展示用的帳號，不要用正式管理員）
- 架構圖：瀏覽器 → Nginx → Spring Boot → PostgreSQL（之後加上 Redis、RabbitMQ）
- 技術棧與選擇理由
- 設計重點：價格快照、庫存條件更新、併發測試結果、訂單狀態機、備份與還原
- 實際使用情況：上線多久、家人怎麼使用、根據回饋改了什麼
- 遇到的問題與解法
- 本機啟動方式

# 全端電商專案計畫

目標：做出一個家人能實際使用、已上線的電商網站，同時作為求職作品集。
前台給顧客購物，後台給家人管理商品與訂單，後端用 Spring Boot，部署到雲端 Linux 主機並啟用 HTTPS。

先求「能上線、能用」，再加 Redis 與 RabbitMQ。上線後的真實使用經驗，比功能多更有說服力。

---

## 開工前先和家人確認

這些答案會影響資料表與流程，先問清楚再開始 B02：

- [x] 賣什麼商品？商品需不需要規格（尺寸、顏色、口味）？包包或布，但實際上不拘
- [x] 運送方式：宅配、超商取貨、面交？運費怎麼算？宅配，超取都行，運費賣家自定義
- [x] 付款方式：銀行轉帳、貨到付款，還是需要信用卡線上付款？貨到付款，或者接第三方金流收錢，要避免會直接影響到賣家銀行帳戶的以防詐騙
- [x] 需不需要開發票？應該還不用
- [x] 誰負責管理後台？大概多久處理一次訂單？家人，或者未來會打包給其他人使用，視情況可能還有個超級admin可以管理所有賣家

**根據以上答案的決定**

- **商品無規格**：每個商品獨立上架（例如同款包的不同顏色就分成不同商品），尺寸、材質等資訊寫在商品描述。
- **運送方式**：宅配、超商取貨兩種，運費由家人在後台設定，不寫死在程式裡。超商取貨第 1 階段先讓顧客**手動填寫門市**（超商品牌、門市名稱、店號），家人自行到超商寄件；電子地圖選店與物流單串接留到第 7 階段。
- **付款方式**：第 1 階段只做**貨到付款**（宅配貨到付款／超商取貨付款）。**不做銀行轉帳**，避免顧客直接匯款到家人帳戶、帳戶被詐騙集團利用。第三方線上金流留到第 7 階段，資料表先保留擴充空間（`payment_method`、`payment_status`、`PENDING_PAYMENT` 狀態）。
- **發票**：暫不開立。
- **單一商店、多位管理員**：不做多賣家，大家一起管理同一間店；每位家人有自己的 ADMIN 帳號分開登入，訂單狀態變更會記錄是誰操作的。

> 線上金流需要向金流業者申請商店帳號，開發票與蒐集顧客個資也有法規要求，上線前請以官方資訊為準，必要時諮詢專業人士。

---

## 技術選型

| 層 | 選擇 |
| --- | --- |
| 後端 | Java 21、Spring Boot 4.x、Maven、Spring Data JPA、PostgreSQL、Flyway、Spring Security＋JWT |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Axios、Element Plus（後台元件） |
| 測試 | JUnit 5、Mockito、MockMvc |
| 部署 | Docker Compose、Nginx（靜態檔＋反向代理）、Let's Encrypt、GitHub Actions |
| 之後加入 | Redis、RabbitMQ |

前端選 Vue 是因為上手門檻低、台灣職缺常見，Element Plus 也很適合做後台。

## Repo 結構

```
mamaShop/
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

**users**：`id`、`email` UNIQUE、`password_hash`、`name`、`phone`、`role`（`USER`／`ADMIN`）、`enabled`、`created_at`、`updated_at`
- 可以有多個 `ADMIN`（每位家人一個帳號）；`enabled` 用來停用帳號而不刪除，保留操作紀錄

**categories**：`id`、`name`、`sort_order`

**products**：`id`、`category_id` FK、`name`、`description`、`price` NUMERIC(10,2)、`stock` INT CHECK (`stock >= 0`)、`image_path`、`status`（`ON_SALE`／`OFF_SALE`）、`created_at`、`updated_at`
- 無規格：顏色、尺寸不同就分成不同商品，其他資訊寫在 `description`

**shipping_methods**：`id`、`code` UNIQUE（`HOME_DELIVERY`／`CVS_PICKUP`）、`name`、`fee` NUMERIC(10,2)、`enabled`、`sort_order`
- 運費由後台設定；下單時把當下運費快照到 `orders.shipping_fee`，之後改運費不影響舊訂單

**orders**
- `id`、`order_no` UNIQUE、`user_id` FK
- `items_amount`、`shipping_fee`、`total_amount` NUMERIC(10,2)
- `recipient_name`、`recipient_phone`、`note`
- `shipping_method`（`HOME_DELIVERY`／`CVS_PICKUP`）
- 宅配：`shipping_address`
- 超商取貨：`cvs_brand`（`SEVEN`／`FAMILY`／`HILIFE`／`OK`）、`cvs_store_code`、`cvs_store_name`（第 1 階段顧客手動填寫）
- `payment_method`（`COD`；之後接金流再加 `ONLINE`）
- `payment_status`（`UNPAID`／`PAID`／`REFUNDED`）
- `status`（見下方訂單狀態）
- `tracking_no`（物流單號）
- `version`（樂觀鎖，避免兩位管理員同時處理同一張訂單互相覆蓋）
- `created_at`、`updated_at`、`confirmed_at`、`shipped_at`、`delivered_at`、`completed_at`、`cancelled_at`、`returned_at`、`paid_at`

**order_items**：`id`、`order_id` FK、`product_id` FK、`product_name`（快照）、`unit_price`（快照）、`quantity`、`subtotal`

**order_status_histories**：`id`、`order_id` FK、`from_status`、`to_status`、`operator_id` FK（顧客或管理員；系統自動處理時為 NULL）、`note`、`created_at`
- 多位管理員一起管理，需要知道「誰在什麼時候把訂單改成什麼狀態」

**訂單狀態**

訂單狀態（出貨流程）與付款狀態分成兩個欄位：貨到付款是「先出貨、後收錢」，線上金流是「先收錢、後出貨」，拆開後兩種付款方式共用同一套出貨流程。

| 狀態 | 意義 | 觸發者 |
| --- | --- | --- |
| `PENDING_PAYMENT` | 等待線上付款（第 7 階段接金流後才使用） | 系統 |
| `PENDING_CONFIRM` | 待確認：貨到付款訂單建立後的初始狀態 | 下單 |
| `PROCESSING` | 已確認、備貨中（待出貨） | 管理員 |
| `SHIPPED` | 已出貨，填物流單號 | 管理員 |
| `DELIVERED` | 已送達／已到超商門市 | 管理員 |
| `COMPLETED` | 已完成（顧客簽收或取件付款） | 管理員 |
| `CANCELLED` | 已取消，歸還庫存 | 顧客或管理員 |
| `RETURNED` | 退回／超商未取件 | 管理員 |

**狀態流程**
- 貨到付款：`PENDING_CONFIRM` →（家人確認訂單，必要時電話確認）→ `PROCESSING` → `SHIPPED` → `DELIVERED` → `COMPLETED`（此時 `payment_status` 改為 `PAID`）
- 線上付款（第 7 階段）：`PENDING_PAYMENT` →（金流通知付款成功，`payment_status` 改為 `PAID`）→ `PROCESSING` → 之後同上
- 取消：顧客只能在 `PENDING_CONFIRM`（或 `PENDING_PAYMENT`）時取消；管理員在出貨前（`SHIPPED` 之前）都能取消。取消時在同一個交易內歸還庫存
- 退回：`SHIPPED`／`DELIVERED` → `RETURNED`（例如超商未取件）；是否加回庫存由管理員決定，因為退回商品可能已損壞

**設計理由**
- `PENDING_CONFIRM`：貨到付款最常見的風險是惡意下單、棄單不取貨，先確認再出貨可以少寄冤枉貨
- `DELIVERED`、`RETURNED`：超商取貨有「到店但沒來拿」的情況，讓這類訂單有地方歸類，也能統計棄單

> 面試重點：價格與運費快照、狀態機集中檢查（所有狀態轉換都經過同一個方法，例如 `OrderStatus.canTransitionTo()`）、取消時歸還庫存要和建立訂單一樣在交易內完成、樂觀鎖處理多人同時操作、狀態變更紀錄。

---

## 第 1 階段：後端核心

每個任務開一個分支（例如 `feature/b05-product-api`），完成後發 PR 再合併。

- [x] **B00 建立 repo**：monorepo 結構、`.gitignore`、放入本檔與 `CLAUDE.md`
- [x] **B01 初始化後端**：start.spring.io 產生 `backend/`；本機 `docker-compose.yml` 先放 PostgreSQL；設定讀環境變數。完成條件：能啟動並連上資料庫
- [x] **B02 Schema**：Flyway `V1__init_schema.sql` 建表、外鍵、唯一索引、CHECK 限制；測試資料放 `db/testdata/R__seed_data.sql`，只在 `dev` profile 載入，正式環境不會寫入假商品。完成條件：啟動後自動建表
- [x] **B03 Entity 與 Repository**：狀態用 Enum（`EnumType.STRING`），`ddl-auto=validate`
- [x] **B04 共用基礎**：統一回應格式、自訂例外、全域例外處理、參數驗證錯誤訊息
- [x] **B05 商品查詢 API**：分類列表、商品列表（分頁、分類、關鍵字）、商品詳情、可用的運送方式與運費，只回 DTO
- [x] **B06 會員與 JWT**：註冊（BCrypt）、登入回 JWT、JWT Filter、路徑權限、預設 ADMIN 帳號（密碼讀環境變數）、CORS 允許本機前端。完成條件：未登入 401、權限不足 403
- [x] **B07 後台商品管理＋圖片上傳**：分類 CRUD、商品新增／修改／上下架、運送方式與運費設定；圖片存到伺服器目錄，限制檔案類型與大小，檔名用 UUID。完成條件：上傳後前台能顯示圖片
- [x] **B08 建立訂單**：一個交易內完成檢查商品 → 條件更新扣庫存（`WHERE stock >= :qty`）→ 建立訂單與明細 → 依運送方式計算運費與總額 → 寫入第一筆狀態紀錄。依運送方式驗證收件資訊（宅配必填地址、超商必填門市）。完成條件：庫存不足時整筆回滾
- [x] **B09 顧客訂單功能**：我的訂單、訂單詳情（只能看自己的）、取消訂單（僅限待確認，歸還庫存）
- [x] **B10 後台訂單管理**：訂單列表（狀態、運送方式篩選、分頁）、確認訂單、出貨（填物流單號）、送達、完成（同時標記已收款）、取消、退回；每次狀態變更寫入 `order_status_histories`（記錄操作的管理員），用樂觀鎖處理多人同時操作
- [x] **B11 測試**：`OrderService` 單元測試、Controller 權限與參數測試、~~多執行緒搶購併發測試（庫存 10、搶 50 次，驗證成功 10 筆）~~（已在 B08 完成）
- [x] **B12 Docker 化**：多階段 `Dockerfile`、compose 加入後端、`.env.example`
- [x] **B13 API 文件**：springdoc-openapi，Swagger UI 可帶 JWT 測試
- [x] **B14 管理員帳號管理**：ADMIN 可以新增其他 ADMIN 帳號、停用帳號（不刪除，保留操作紀錄）、重設密碼；不能停用自己

## 第 2 階段：前台

- [x] **F01 初始化前端**：Vite 建立 Vue 3 專案；Vue Router、Pinia、Axios；Axios 攔截器自動帶 JWT、401 時導回登入；Vite proxy 轉發 `/api` 到後端
- [x] **F02 商品瀏覽**：首頁、分類與搜尋、商品列表（分頁）、商品詳情
- [x] **F03 購物車**：Pinia 管理，存在 localStorage；加入、改數量、移除、小計
- [x] **F04 會員**：註冊、登入、登出、需登入頁面的路由守衛
- [x] **F05 結帳**：選運送方式（宅配填地址、超商取貨手動填門市）、填收件資訊、顯示運費與總額、送出訂單（貨到付款）
- [x] **F06 我的訂單**：列表、詳情（含狀態進度）、取消（僅限待確認）
- [x] **F07 手機版面**：家人和顧客多半用手機，所有頁面都要在手機上好用
- [x] **F08 體驗細節**：載入狀態、錯誤訊息、空狀態（沒有商品、沒有訂單）

## 第 3 階段：後台

- [x] **A01 後台框架**：`/admin` 路由、Element Plus 版面、只有 ADMIN 能進
- [x] **A02 商品管理**：列表、新增／編輯（含圖片上傳）、上下架、庫存調整
- [x] **A03 分類與運費設定**
- [x] **A04 訂單管理**：依狀態篩選、查看明細與狀態紀錄（誰在何時操作）、確認、出貨、送達、完成、取消、退回
- [x] **A05 首頁概覽**：今日訂單數、待確認、待出貨、未取件／退回、低庫存商品
- [x] **A06 管理員帳號**：管理員列表、新增、停用、重設密碼

> 後台是家人每天要用的，介面以「看得懂、不會按錯」為優先，先請家人試用再調整。

## 第 4 階段：上線

- [ ] **D01 主機**：租一台 Linux VPS；SSH 金鑰登入、關閉密碼登入；防火牆只開 22、80、443（📄 操作指南與檢查腳本已完成：`docs/deploy/D01-server.md`，待實際租主機操作）
- [ ] **D02 網域**：購買網域、設定 DNS 指向主機
- [ ] **D03 正式環境部署**：`deploy/docker-compose.prod.yml` 包含 Nginx、後端、PostgreSQL；Nginx 提供前端靜態檔並把 `/api` 轉給後端；資料庫不對外開放；伺服器上的 `.env` 不進版控（📄 設定檔與部署指南已完成：`docs/deploy/D03-deploy.md`，已在本機用正式設定完整驗證，待主機實際部署）
- [ ] **D04 HTTPS**：Let's Encrypt 憑證與自動續期
- [ ] **D05 備份**：每日 `pg_dump` 與圖片目錄備份、保留天數、複製到主機以外的地方；**實際做一次還原測試**
- [x] **D06 CI**：GitHub Actions 在 PR 時自動跑測試與前端建置；之後可加自動部署
- [ ] **D07 基本監控**：Spring Boot Actuator 健康檢查、Docker log 輪替、外部服務定期檢查網站是否正常（✅ 健康檢查、Docker 健康檢查、log 輪替已完成；⏳ 外部定期檢查待網站上線後照 `docs/deploy/D07-monitoring.md` 設定）
- [ ] **D08 上線檢查**：更換預設管理員密碼、隱私權政策頁面、用手機完整走一次下單到出貨、請家人試用一週再正式開放

> 這一階段是補強 Linux 的最好機會。遇到的每個問題（連不上、權限錯誤、憑證失敗）都記下來，面試時就是真實的排查經驗。

## 第 5 階段：Redis

- [x] **R00 壓力測試基準**：用 k6 寫腳本（放在 `loadtest/`）壓測商品列表與商品詳情 API，記錄 QPS、p95 回應時間、錯誤率，作為 R01 加快取前的基準。在本機或測試環境跑，不要壓正式站
- [x] **R01 商品快取**：商品列表與詳情加快取，後台修改時清除對應快取；用 R00 的同一支腳本再跑一次，比較加快取前後的數據
- [ ] **R02 防重複下單**：結帳頁取得一次性 token，送出訂單時用 Redis 確保同一個 token 只能成功一次（對照 Mission 的資料庫唯一索引做法）
- [ ] **R03 限時搶購（選做）**：用 Redis 預扣庫存，再非同步寫入資料庫

## 第 6 階段：RabbitMQ

- [ ] **M01 訂單通知**：下單、確認訂單、出貨時，透過佇列非同步寄 Email 給顧客，寄信失敗不影響下單
- [ ] **M02 逾時自動處理**：透過 TTL＋死信佇列實作延遲任務。接金流後用於「線上付款訂單逾時未付款自動取消並歸還庫存」；接金流前可先用於「已送達超過 N 天自動完成」
- [ ] **M03 失敗處理**：訊息重試與失敗佇列，確保通知不會默默遺失

> 誠實面對規模：家人的網站流量不大，Redis 與 RabbitMQ 主要是為了學習與預備擴展。README 裡直接這樣寫，面試官反而會覺得你判斷清楚。

## 第 7 階段：金流與物流串接

主要功能完成、上線穩定後再回來做。都需要向業者申請商店帳號，先確認費用與申請條件。

- [ ] **P01 第三方線上金流**：串接金流業者（信用卡等），使用 `PENDING_PAYMENT` 狀態；付款結果以伺服器端通知（webhook）為準並驗證簽章，不能只相信前端導回的結果；處理重複通知（冪等）
- [ ] **P02 超商電子地圖選店**：結帳時用物流業者的電子地圖選門市，取代手動填寫
- [ ] **P03 物流單與狀態同步**：建立物流單、取得物流單號，並接收物流狀態通知自動更新「已到店」「已取件」「未取件退回」

---

## README 要寫的內容

- 專案簡介與線上網址、測試帳號（另開一個展示用的帳號，不要用正式管理員）
- 架構圖：瀏覽器 → Nginx → Spring Boot → PostgreSQL（之後加上 Redis、RabbitMQ）
- 技術棧與選擇理由
- 設計重點：價格與運費快照、庫存條件更新、併發測試結果、壓測數據（R00／R01 快取前後對照）、訂單狀態機與狀態紀錄、樂觀鎖、不收轉帳的防詐考量、備份與還原
- 實際使用情況：上線多久、家人怎麼使用、根據回饋改了什麼
- 遇到的問題與解法
- 本機啟動方式

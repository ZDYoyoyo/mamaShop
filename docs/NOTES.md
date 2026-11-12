# 任務紀錄與注意事項

每個任務完成後在這裡記錄：**和 PLAN 不同的決定**、**刻意沒做的**、**之後要注意的事**。
review PR 時搭配這份看；上線前（D08）再整份檢查一次。

狀態標記：🔲 待決定／待處理　✅ 已處理　ℹ️ 僅供參考

---

## 尚未 review 的 PR

PR #1–#17 已 merge，但你還沒看過。B 系列完成後一起研究。建議依序看，每個 PR 的重點列在下方各任務。

| PR | 任務 | 狀態 |
|---|---|---|
| [#1](https://github.com/ZDYoyoyo/mamaShop/pull/1) | 更新 PLAN（付款、運送、訂單狀態） | 已 merge，未 review |
| [#2](https://github.com/ZDYoyoyo/mamaShop/pull/2) | B00 repo 結構 | 已 merge，未 review |
| [#3](https://github.com/ZDYoyoyo/mamaShop/pull/3) | B01 初始化後端 | 已 merge，未 review |
| [#4](https://github.com/ZDYoyoyo/mamaShop/pull/4) | B02 Schema | 已 merge，未 review |
| [#5](https://github.com/ZDYoyoyo/mamaShop/pull/5) | B03 Entity | 已 merge，未 review |
| [#6](https://github.com/ZDYoyoyo/mamaShop/pull/6) | B04 共用基礎 | 已 merge，未 review |
| [#7](https://github.com/ZDYoyoyo/mamaShop/pull/7) | B05 商品查詢 API | 已 merge，未 review |
| [#8](https://github.com/ZDYoyoyo/mamaShop/pull/8) | B06 會員與 JWT | 已 merge，未 review |
| [#9](https://github.com/ZDYoyoyo/mamaShop/pull/9) | 新增本文件 | 已 merge，未 review |
| [#10](https://github.com/ZDYoyoyo/mamaShop/pull/10) | B07 後台商品管理＋圖片上傳 | 已 merge，未 review |
| [#11](https://github.com/ZDYoyoyo/mamaShop/pull/11) | B08 建立訂單 | 已 merge，未 review |
| [#12](https://github.com/ZDYoyoyo/mamaShop/pull/12) | B09 顧客訂單功能 | 已 merge，未 review |
| [#13](https://github.com/ZDYoyoyo/mamaShop/pull/13) | B10 後台訂單管理 | 已 merge，未 review |
| [#14](https://github.com/ZDYoyoyo/mamaShop/pull/14) | B11 測試 | 已 merge，未 review |
| [#15](https://github.com/ZDYoyoyo/mamaShop/pull/15) | B12 Docker 化 | 已 merge，未 review |
| [#16](https://github.com/ZDYoyoyo/mamaShop/pull/16) | B13 API 文件 | 已 merge，未 review |
| [#17](https://github.com/ZDYoyoyo/mamaShop/pull/17) | B14 管理員帳號管理 | 已 merge，未 review |

> #10～#17 已依序 merge。

---

## 上線前一定要處理（D08 檢查清單）

- 🔲 **運費**：`shipping_methods` 目前是暫定值（宅配 100、超商 60），請家人確認後在後台調整（B07 之後才有後台）
- 🔲 **預設管理員密碼**：上線後用 `PUT /api/admin/admins/{id}/password` 修改（B14），並從伺服器環境變數移除 `ADMIN_PASSWORD`
- 🔲 **`JWT_SECRET`**：正式環境要用 `openssl rand -base64 48` 產生新的，不要沿用本機的
- 🔲 **正式環境不要設定 `APP_PROFILE=dev`**，否則會寫入測試商品
- 🔲 **上傳目錄要掛 Docker volume 並納入備份**（D03、D05），否則重建容器時商品圖片會消失
- 🔲 **登入失敗次數限制**：目前沒有，可能被暴力破解（建議 Nginx rate limit 或 R 階段用 Redis）

---

## 全域

- ℹ️ **Spring Boot 4.1.2**：原計畫 3.x，但 3.x 開源支援已結束，改用 4.x。網路教學多是 3.x，差異：starter 名稱拆細（`spring-boot-starter-webmvc`、`spring-boot-starter-flyway`）、Jackson 3（套件名 `tools.jackson`）、測試註解的套件路徑不同
- ℹ️ **`.env` 不要寫中文**：Spring 用 ISO-8859-1 讀 `.env`，中文會變亂碼（實際踩過，見 B06）
- ℹ️ **後端測試需要資料庫在跑**：`docker compose up -d db` 之後才能 `mvnw test`
- 🔲 **CI（D06）**：因為測試要連資料庫，GitHub Actions 要另外起 PostgreSQL service，或改用 Testcontainers
- ℹ️ **跑測試會在本機資料庫建立預設管理員**：`@SpringBootTest` 啟動時會執行 `AdminAccountInitializer`，這一步不在測試交易內，不會 rollback（B06 之後）

---

## B00 建立 repo

- 🔲 **沒有 `.gitattributes`**：Windows 上 Git 可能把 shell 腳本轉成 CRLF，放到 Linux 或 Docker 執行會失敗（D 階段的部署、備份腳本）。建議加一行 `* text=auto eol=lf`。`backend/` 裡 start.spring.io 附的那份只管 `mvnw`
- 🔲 `frontend/.gitkeep`、`deploy/.gitkeep` 是佔位用的，F01、D03 放入檔案後刪除

## B01 初始化後端

- ℹ️ 資料庫帳密沒設定時，錯誤訊息會是 `password authentication failed for user "${DB_USERNAME}"`，看到這個就是 `.env` 沒讀到
- ℹ️ `spring.config.import` 同時讀 `./.env` 和 `../.env`：從 `backend/` 或 repo 根目錄啟動都可以
- ℹ️ `open-in-view: false`：Controller 裡不能再讀 Entity 的 LAZY 關聯，DTO 要在 Service 裡組好

## B02 Schema

- ℹ️ **和 PLAN 不同**：測試資料沒有放 `V2__seed_data.sql`，改放 `db/testdata/R__seed_data.sql`，只有 `dev` profile 會載入，避免正式環境出現假商品
- ℹ️ **已執行過的 migration 不能修改**（Flyway 會因 checksum 不符拒絕啟動）。要改 schema 請新增 `V2__...`。本機想重來可以 `docker compose down -v`
- ℹ️ 資料庫的 email UNIQUE 是區分大小寫的；程式一律轉小寫後存（B06）。**手動用 SQL 新增會員時也要用小寫**
- ℹ️ `payment_method` 目前只允許 `COD`，接金流（P01）時要寫 migration 修改 CHECK
- 🔲 超商品牌只有 7-11、全家、萊爾富、OK；要新增要寫 migration

### 和 B08 有關的限制

ℹ️ B08 原本計畫自己寫，後來改由 Claude 實作（B 系列完成後再整體研究）。


- `order_items` 有 `UNIQUE (order_id, product_id)`：購物車同一商品出現兩次要先合併數量
- `orders.status` 沒有預設值，建立時要明確設定 `PENDING_CONFIRM`
- `total_amount = items_amount + shipping_fee`、`subtotal = unit_price * quantity` 都是**完全相等**的 CHECK，`BigDecimal` 計算要精確
- 宅配必填 `shipping_address`；超商取貨必填品牌、店號、店名（資料庫 CHECK 會擋）

## B03 Entity 與 Repository

- ℹ️ **沒用 Lombok**：getter／setter 手寫
- ℹ️ 沒有覆寫 `equals`／`hashCode`：Entity 不要在存檔前放進 `HashSet`
- ℹ️ `updated_at` 由 Hibernate 的 `@UpdateTimestamp` 維護，**直接用 SQL 更新資料時不會自動更新**
- ℹ️ `OrderItem` 沒有自己的 Repository：透過 `Order.addItem()` 加入，存 `Order` 時一起存（cascade）
- 🔲 訂單狀態轉換規則（`canTransitionTo`）還沒寫，在 B09／B10 實作

## B04 共用基礎

- ℹ️ 回應格式：`{ success, data }` 或 `{ success, error: { code, message, fieldErrors } }`，HTTP 狀態碼照實回傳
- ℹ️ `DataIntegrityViolationException`（例如違反唯一鍵）沒有特別處理，會變成 500。**業務邏輯要先檢查**，例如註冊時先查 Email 是否存在
- ℹ️ 各模組的錯誤代碼（庫存不足、狀態不允許…）在實作該功能的任務加到 `ErrorCode`
- ℹ️ 錯誤訊息只有繁體中文，沒有做多語系

## B05 商品查詢 API

- ✅ **圖片網址**：B07 決定為 `/uploads/{image_path}`
- 🔲 **要不要讓顧客看到精確庫存**：目前回傳 `stock` 數字。可以改成只顯示「已售完」或「剩少量」，請和家人討論
- 🔲 **售完的商品**目前仍會出現在列表（`stock = 0` 但 `ON_SALE`），前端要顯示「已售完」；或改成不顯示
- ℹ️ 關鍵字只搜尋商品名稱，不搜尋描述
- ℹ️ 排序只有「新上架優先」，沒有價格排序（範圍外）
- ℹ️ 不存在的 `categoryId` 回傳空列表，不是 404
- ℹ️ Windows cmd 的 curl 送中文參數會亂碼，搜尋功能請用 Swagger UI（B13）或前端測

## B06 會員與 JWT

- ℹ️ **jjwt 0.13 + jjwt-gson**：jjwt 的 Jackson 模組還是 Jackson 2，為了不和 Spring Boot 4 的 Jackson 3 混用，改用 Gson 模組
- ℹ️ **401 和 404**：沒登入時打不存在的路徑會回 401，不是 404（Security 先擋）
- ℹ️ **沒有伺服器端登出**：token 在有效期限（預設 1 天）內都有效；要立刻讓某人失效，可以停用帳號
- ℹ️ **更換 `JWT_SECRET` 會讓所有人被登出**
- ℹ️ 密碼限制 8–72 個英數字或符號（不能用中文，BCrypt 72 bytes 上限）
- 🔲 **沒做**：refresh token、忘記密碼、Email 驗證（需要寄信，可以在 M01 之後再考慮）
- 🔲 **前端 token 存放位置**：在 F01 決定（localStorage 有 XSS 風險，要評估）
- ℹ️ `/api/users/me` 是額外加的（PLAN 沒有列），用來驗證登入狀態，F04 也會用到

## B07 後台商品管理＋圖片上傳

新增的後台 API（都在 `/api/admin/**`，限 ADMIN）：

| API | 說明 |
|---|---|
| `GET/POST /api/admin/categories`、`PUT/DELETE /api/admin/categories/{id}` | 分類 CRUD |
| `GET /api/admin/products`、`GET /api/admin/products/{id}` | 商品列表（含下架，可依狀態篩選）、詳情 |
| `POST /api/admin/products`、`PUT /api/admin/products/{id}` | 新增、修改基本資料 |
| `PATCH /api/admin/products/{id}/status` | 上架／下架 |
| `PATCH /api/admin/products/{id}/stock` | 調整庫存（增減量） |
| `POST /api/admin/products/{id}/image` | 上傳／更換圖片（multipart，欄位 `file`） |
| `GET /api/admin/shipping-methods`、`PUT /api/admin/shipping-methods/{id}` | 運送方式與運費 |

- ℹ️ **和 PLAN 不同：庫存不能「直接設定成某個數字」**，只能用增減量調整（`{"delta": 5}`）。直接設定會覆蓋掉同時間顧客下單扣掉的庫存（lost update）。修改商品基本資料（PUT）也不會動到庫存
- ℹ️ **新商品預設下架**：建好、上傳圖片、確認無誤後再上架
- ℹ️ **商品不提供刪除**：舊訂單明細會參照商品，不賣了就下架
- ℹ️ **分類只能刪除沒有商品的**；分類名稱不能重複
- ℹ️ **運送方式不能新增或刪除**（種類由程式定義），只能改名稱、運費、啟用、排序；至少要保留一種啟用中
- ℹ️ 圖片只接受 **JPG、PNG、WebP**，上限 **10MB**；用檔案內容判斷格式，不相信副檔名
- ℹ️ 圖片檔名一律改成 UUID；換圖片時，舊檔在資料庫 commit 成功後才刪除，失敗則刪除新檔
- ℹ️ `/uploads/**` 不用登入就能讀（商品圖片本來就公開），瀏覽器快取 1 年（檔名不會重複，所以安全）
- ℹ️ 本機上傳的檔案在 `backend/uploads/`（已在 `.gitignore`）
- ℹ️ MockMvc 測試不會經過 Tomcat 的上傳大小限制，所以程式內也檢查一次大小；Tomcat 那層用 curl 實測過（11MB → 413）
- 🔲 **iPhone 預設的 HEIC 格式不支援**：家人用 iPhone 拍照要把相機設成「最相容」，或在後台前端（A02）上傳前轉檔
- 🔲 **照片的 EXIF 沒有移除**：手機照片可能含拍攝地點（GPS），在家拍的商品照會洩漏住址。建議 A02 上傳前在前端壓縮（用 canvas 重新輸出會去掉 EXIF），或之後在後端重新編碼
- 🔲 **沒有縮圖或壓縮**：手機照片動輒 3–5MB，列表頁會很慢。同樣建議 A02 上傳前壓縮
- 🔲 **每個商品只有一張圖片**（PLAN 的 `image_path` 是單一欄位）；要多張圖需要新的資料表
- ℹ️ `ProductRepository.findAll(spec, pageable)` 加了 `@EntityGraph`，前台商品列表（B05）的查詢也會一起 JOIN 分類，查詢數仍是 2 個

## B08 建立訂單

完整設計說明：[`docs/B08-order-design.md`](B08-order-design.md)（併發、交易、回滾、實測結果、面試問題）

- ℹ️ `POST /api/orders`（需登入）。請求只有商品 id 與數量，價格一律以伺服器資料為準
- ℹ️ 扣庫存用條件更新 `WHERE stock >= :qty`，並依商品 id 排序避免死結。兩者都用「暫時改成錯誤寫法」實測過：錯誤寫法會超賣 5 倍／死結
- ℹ️ 訂單編號：`M` + 台灣日期 + 10 位隨機數（例如 `M2611128402719365`），對外不暴露資料庫 id
- ℹ️ 建立後狀態 `PENDING_CONFIRM`、付款 `COD`／`UNPAID`；寫入第一筆狀態紀錄（操作者＝顧客）
- ℹ️ **B11 的併發測試提前做了**（`OrderConcurrencyTest`：庫存 10、50 人搶，剛好 10 筆成功；另加死結測試）。B11 只剩 Service 單元測試與 Controller 權限測試
- ℹ️ 訂單相關測試沒有用 `@Transactional`（要測真正的 commit／rollback），資料在 `@AfterEach` 自己清除
- 🔲 **重複送出**：連點兩下會建立兩筆訂單。F05 前端送出後要停用按鈕；R02 再從後端用一次性 token 擋
- 🔲 **訂單編號重複**機率極低（同日 100 筆約百萬分之一），發生時整筆回滾並回 500，顧客重送即可
- 🔲 **下單後沒有通知**：家人要自己到後台看新訂單（A05 首頁概覽會顯示待確認數量；M01 才有 Email 通知）
- ℹ️ 管理員帳號也能下單（沒有限制只有 USER 能買）
- ℹ️ 收件人手機只接受台灣手機格式（09 開頭 10 碼），市話不行

## B09 顧客訂單功能

| API | 說明 |
|---|---|
| `GET /api/orders?page=1&size=10` | 我的訂單（新到舊），含第一個商品名稱與總件數 |
| `GET /api/orders/{orderNo}` | 訂單詳情，含各狀態時間（前端畫進度用） |
| `POST /api/orders/{orderNo}/cancel` | 取消訂單，歸還庫存 |

- ℹ️ **狀態機集中**：`OrderStatus.canTransitionTo()` 定義所有合法轉換，`Order.transitionTo()` 是改狀態的唯一入口（同時記錄時間）。B10 的確認、出貨、完成也會走這裡
- ℹ️ **顧客只能取消「待確認／待付款」**；家人確認後就要聯絡賣家（管理員在出貨前都能取消，B10）
- ℹ️ **別人的訂單回 404 不是 403**；查詢條件直接帶 `userId`，不是查出來再比對
- ℹ️ **對外用 `orderNo`**，不用資料庫 id
- ℹ️ **取消與家人確認同時發生**：`orders` 的 `@Version` 樂觀鎖會讓後送出的那個失敗（409），不會「已確認卻被取消」也不會重複歸還庫存。測試：10 個執行緒同時取消同一筆訂單，只有 1 筆成功、庫存只加回一次
- ℹ️ 扣庫存／歸還庫存集中到 `OrderStockService`（B08 的扣庫存從 `OrderService` 搬過來），都用 `Propagation.MANDATORY`：一定要在呼叫端的交易內執行
- ℹ️ 取消時，就算商品已下架，庫存也會加回去
- ℹ️ **新增 `hibernate.default_batch_fetch_size: 50`**（全域設定）：LAZY 關聯改成批次用 `IN` 查詢。訂單列表實測：開啟 3 個查詢、關閉 7 個
- 🔲 **取消沒有填原因**：狀態紀錄的 note 固定是「顧客取消」。如果家人想知道原因，可以之後加選填欄位
- 🔲 **取消後沒有通知家人**（M01 之後）

## B10 後台訂單管理

| API（`/api/admin/orders`） | 狀態變化 | 附帶效果 |
|---|---|---|
| `GET /`、`GET /{orderNo}` | — | 列表可依狀態、運送方式篩選；詳情含下單會員、狀態紀錄、版本號 |
| `POST /{orderNo}/confirm` | 待確認／待付款 → 備貨中 | |
| `POST /{orderNo}/ship` | 備貨中 → 已出貨 | 必填物流單號 |
| `POST /{orderNo}/deliver` | 已出貨 → 已送達 | |
| `POST /{orderNo}/complete` | 已送達 → 已完成 | 付款狀態改為已付款（貨到付款在簽收時收錢） |
| `POST /{orderNo}/cancel` | 出貨前 → 已取消 | 歸還庫存 |
| `POST /{orderNo}/return` | 已出貨／已送達 → 已退回 | `restock` 決定是否歸還庫存 |

- ℹ️ **每個操作都要帶 `version`（畫面上看到的版本號）**：和伺服器不同就回 409「資料已被其他人修改，請重新整理」。前端（A04）每次操作後要用回應裡的新版本號
- ℹ️ **兩層防護**：版本號比對擋「畫面資料過時」（A 看著舊畫面按取消，B 剛確認）；`@Version` 擋「同一瞬間送出」。實測拿掉 `@Version`：10 個同時操作全部「成功」、互相覆蓋
- ℹ️ 每個操作都寫入狀態紀錄（操作的管理員、時間、備註）；退回時備註會自動加上「已歸還庫存／未歸還庫存」
- ℹ️ PLAN 原本寫「確認收款」，因為不收轉帳，改成「確認訂單」；收款在「完成」時一起標記
- ℹ️ 列表預設新到舊
- 🔲 **列表沒有關鍵字搜尋**（訂單編號、收件人、電話）：顧客打電話來時要用編號查詳情。需要的話 A04 時再加
- 🔲 **退回的訂單付款狀態維持「未付款」**；接金流（P01）後，已付款的訂單取消／退回要處理退款（`REFUNDED`）
- 🔲 **出貨後填錯物流單號無法修改**（只能在出貨時填一次）
- 🔲 **送達／完成需要家人手動按**：超商取貨沒有串接物流狀態（P03 才自動）

## B11 測試

全部 **255 個測試**（`mvnw test`）。測試的分層：

| 類型 | 啟動什麼 | 例子 | 用途 |
|---|---|---|---|
| 純單元測試 | 什麼都不啟動（Mockito） | `OrderServiceTest`、`AdminOrderServiceTest`、`OrderStatusTest`、`ImageTypeTest` | Service 邏輯、狀態機；毫秒級 |
| Web 層切片 | 只有 Controller（`@WebMvcTest`） | `OrderControllerParamTest`、`ProductControllerTest` | 參數驗證、預設值、從 token 取 id |
| 整合測試 | 完整應用＋真的資料庫 | `CreateOrderIntegrationTest`、`AdminOrderIntegrationTest` | SQL、交易回滾、資料庫限制 |
| 併發測試 | 完整應用＋多執行緒 | `OrderConcurrencyTest` | 超賣、死結、樂觀鎖 |
| 權限總表 | 完整應用 | `AccessControlTest` | 32 支 API × 3 種身分 |

- ℹ️ **權限總表 `AccessControlTest`**：列出每支 API 是「公開／登入／管理員」，用未登入、顧客、管理員三種身分逐一驗證。另外會掃描所有已註冊的 API，**新增 API 沒加進總表，測試就會失敗**。之後每加一支 API 都要更新這張表
- ℹ️ **反向驗證**：把 `/api/admin/**` 暫時改成「登入即可」→ 21 個失敗；總表漏列一支 API → 完整性檢查失敗並列出漏掉的那支
- ℹ️ **修正了 B06 的偶發失敗測試**：竄改 JWT 的測試原本是換掉簽章最後一個字元，但那個字元有 4 個 bit 是 Base64 補位，`A` → `B` 只改到補位，簽章其實沒變，約 1/64 的機率測試失敗。改成竄改簽章中間的字元，並新增「把 payload 的會員 id 改成別人」的偽造測試
- ℹ️ 併發測試在 B08–B10 已經做了（庫存搶購、死結、重複取消、管理員同時操作）
- ℹ️ Controller 參數測試只關掉 Security filter，直接設定 `SecurityContextHolder` 模擬登入者
- 🔲 **CI（D06）**：整合測試需要 PostgreSQL；GitHub Actions 要加 `services: postgres`，或改用 Testcontainers
- 🔲 **沒有測試覆蓋率報告**（JaCoCo）；需要的話 D06 時一起加

## B12 Docker 化

- ℹ️ `backend/Dockerfile`：多階段建置。第 1 階段 `eclipse-temurin:21-jdk-alpine` 用 `mvnw` 打包；第 2 階段 `eclipse-temurin:21-jre-alpine` 只放 jar
- ℹ️ 先複製 `pom.xml` 下載依賴、再複製原始碼：只改程式碼時，依賴那一層直接用快取
- ℹ️ 以非 root 使用者 `spring` 執行；映像檔裡沒有原始碼、Maven、javac
- ℹ️ `-XX:MaxRAMPercentage=75`：JVM 依容器記憶體上限決定 heap
- ℹ️ `docker-compose.yml` 加入 `backend`：等 `db` 的 healthcheck 通過才啟動；容器內用 `DB_HOST=db` 連資料庫；上傳目錄掛 volume `uploads-data`
- ℹ️ 映像檔建置時**不跑測試**（測試需要資料庫），由 `mvnw test` 與 CI（D06）負責
- ℹ️ **`.env.example` 沒有新增變數**：compose 直接從 `.env` 取值傳給後端，只有 `DB_HOST` 在 compose 裡固定為 `db`
- ℹ️ `mvnw` 在 Windows checkout 時：`backend/.gitattributes` 已強制 LF 換行，Dockerfile 也會 `chmod +x`，不會因 CRLF 或權限在 Linux 容器裡執行失敗
- ℹ️ 在 Claude 的雲端環境驗證時，因為網路代理會重新簽 TLS 憑證，另外用一份「多信任代理 CA」的 Dockerfile 建置（只在暫存目錄，沒有 commit）。在你的電腦上直接用 `docker compose up --build` 即可
- 🔲 **映像檔約 400MB**（JRE + Spring Boot 依賴）。之後可以用 Spring Boot 的 layered jar 讓「只改程式碼」時推送更少的資料，D03 部署時再評估
- 🔲 **容器時區是 UTC**：log 時間會比台灣慢 8 小時（資料庫存的是 `TIMESTAMPTZ`，訂單編號也用台灣日期，資料本身不受影響）
- 🔲 **沒有後端的 healthcheck**：D07 加入 Actuator 後再補
- 🔲 **正式環境的 compose 在 D03**：會加 Nginx、資料庫不對外開 port、`restart` 策略、log 輪替

## B13 API 文件

- ℹ️ springdoc-openapi 3.2.0（對應 Spring Boot 4）：自動從 Controller 產生 OpenAPI 規格與 Swagger UI；參數型別、驗證規則（`@Size`、`@Min`…）自動出現在文件上
- ℹ️ 每個 Controller 加 `@Tag`（中文分組）、每支 API 加 `@Operation(summary)`；需要登入的 Controller 加 `@SecurityRequirement`，Swagger UI 上會顯示鎖頭並自動帶 token
- ℹ️ `@AuthenticationPrincipal` 的參數不會出現在文件上（測試有驗證）
- ℹ️ **預設關閉**：`API_DOCS_ENABLED` 沒設定或為 false 時 `/swagger-ui.html`、`/v3/api-docs` 都是 404。本機 `.env` 設為 true
- ℹ️ 權限總表（`AccessControlTest`）排除了 API 文件的路徑，因為它們是否存在取決於設定；改由 `ApiDocsTest` 分別測開啟／關閉
- ℹ️ 新增 API 時記得加 `@Operation(summary = "...")`，否則 Swagger UI 只會顯示方法名稱
- 🔲 **沒有為每個錯誤情況寫回應範例**（例如 409 的內容），只有成功回應的結構；需要的話可以加 `@ApiResponse`
- 🔲 **如果想讓面試官線上試用 API**：可以在正式環境開啟，但建議另外加 Nginx 的 Basic Auth 保護，或只開展示站

## B14 管理員帳號管理

| API（`/api/admin/admins`） | 說明 |
|---|---|
| `GET /` | 管理員列表 |
| `POST /` | 新增管理員 |
| `PATCH /{id}/enabled` | 停用／啟用（不能停用自己） |
| `PUT /{id}/password` | 重設密碼 |

- ℹ️ **新增 Flyway V2**：`users.password_changed_at`。專案第一個 schema 變更，示範「不改 V1、新增 V2」
- ℹ️ **和 PLAN 不同（多做的）：重設密碼後，之前發出的 token 全部失效**。JWT 無狀態，伺服器不知道發過哪些 token；改成比對 token 的發行時間（`iat`）是否早於密碼修改時間。否則重設密碼後，被盜用的 token 仍可用到過期（最長 1 天），重設就失去意義
- ℹ️ 比對時把密碼修改時間取到「秒」（`iat` 只精確到秒），避免同一秒內改密碼又重新登入的新 token 被誤判
- ℹ️ **停用立即生效**：B06 的 Filter 本來就每次請求讀會員狀態
- ℹ️ 只管理 ADMIN 帳號；顧客帳號的 id 會回 404
- ℹ️ 不提供刪除：狀態紀錄還參照管理員帳號，刪了就不知道是誰操作的
- ℹ️ 密碼規則抽成 `@ValidPassword`（組合註解），註冊、新增管理員、重設密碼共用
- ℹ️ 反向驗證：拿掉 Filter 的密碼時間檢查 → `resetPassword_invalidatesTokensIssuedBefore` 失敗
- 🔲 **改自己的密碼不需要輸入舊密碼**：有人拿到已登入的電腦就能改密碼。之後可以加「修改自己的密碼」API 並要求舊密碼
- 🔲 **顧客沒有改密碼／忘記密碼功能**（需要寄信，M01 之後）
- 🔲 **管理員之間沒有權限分級**：任何管理員都能新增、停用其他管理員、重設任何管理員的密碼。家人使用沒問題；如果之後給其他人用，要加超級管理員角色

---

# 第 2 階段：前台

## F01 初始化前端

- ℹ️ 用 Vue 官方的 `create-vue` 建立（Vue 3.5、Vue Router 5、Pinia 4、Vite 8、Vitest、ESLint），再加 Axios
- ℹ️ **用 JavaScript，沒有用 TypeScript**：降低學習負擔。之後想加可以逐步轉換
- ℹ️ **Vite proxy**：開發時 `/api`、`/uploads` 轉給 `localhost:8080`，瀏覽器只看到 `localhost:5173`，不會遇到 CORS。前端程式一律用相對路徑 `/api`，正式環境由 Nginx 做一樣的轉發（D03）
- ℹ️ **`api/http.js`**（axios 攔截器）：自動帶 `Authorization`；成功時直接回傳 `data`；錯誤統一轉成 `ApiError`（`status`、`code`、`message`、`fieldErrors`）；401 時登出並導向登入頁，帶 `redirect` 參數。登入 API 自己的 401（密碼錯誤）要傳 `skipAuthRedirect: true`
- ℹ️ **token 存在 localStorage**（`stores/auth.js`）：重新整理、開新分頁都保持登入。風險是 XSS：Vue 預設會跳脫輸出、**專案不使用 `v-html`**、正式環境加 CSP（D03）。改用 httpOnly cookie 要改後端（CSRF 防護），目前不值得
- ℹ️ 路由用 `createWebHistory`（網址沒有 `#`）。**正式環境 Nginx 要設定 `try_files $uri /index.html`**，否則直接開 `/products/1` 會 404（D03）
- ℹ️ 首頁目前只顯示分類列表，用來確認整條連線是通的；F02 會換成真正的首頁。登入頁是空殼，F04 實作；需要登入的路由守衛也在 F04
- ℹ️ **npm 10 的 bug**：`npm install` 在解析選用的 peer dependency 時會出現 `Cannot read properties of null (reading 'edgesOut')`。已用 npm 11 產生 `package-lock.json`，**之後請用 `npm ci`**（照 lock 檔安裝，npm 10 也沒問題）
- ℹ️ `npm audit`：正式依賴 0 個漏洞；開發工具有 11 個 moderate（不會打包進網站）
- ℹ️ `frontend/.gitattributes`（create-vue 附的）已設定 LF 換行
- 🔲 **UI 元件庫**：PLAN 寫 Element Plus 用在後台（A01）。前台要不要也用，F02 時決定
- 🔲 **CI（D06）** 要加 `npm ci`、`npm run lint`、`npm run test:unit -- --run`、`npm run build`

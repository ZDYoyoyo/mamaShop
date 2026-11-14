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
- ✅ **UI 元件庫**：前台也用 Element Plus（F02 決定）
- 🔲 **CI（D06）** 要加 `npm ci`、`npm run lint`、`npm run test:unit -- --run`、`npm run build`

## F02 商品瀏覽

| 網址 | 頁面 |
|---|---|
| `/` | 首頁：搜尋、分類入口、最新上架 8 件 |
| `/products?categoryId=&keyword=&page=` | 商品列表：分類、關鍵字、分頁（每頁 12 件） |
| `/products/:id` | 商品詳情（下架或不存在顯示「找不到這個商品」） |

- ℹ️ **前台也用 Element Plus**（你選的第 1 種），**按需載入**（`unplugin-vue-components`）：只打包用到的元件，JS 約 145KB（gzip）
- ℹ️ **主題色改成棕色**：覆蓋 `--el-color-primary` 系列變數。選擇器要用 `html:root`，因為 Element Plus 的 CSS 晚載入，`:root` 會被它蓋回藍色（實際踩過）
- ℹ️ **篩選條件存在網址上**：重新整理、分享連結、按「上一頁」都正確。在某個分類裡搜尋時，會在該分類內搜尋
- ℹ️ **連續快速切換分類**：舊請求比新請求晚回來時會被丟掉，不會蓋掉正確結果（有測試）
- ℹ️ **元件結構**：`ProductCard`（一個商品）、`ProductGrid`（格狀列表＋載入骨架＋空狀態）、`ProductImage`（沒圖片顯示「暫無圖片」）、`CategoryChips`（可左右滑的分類按鈕）、`SearchBox`；API 呼叫集中在 `api/catalog.js`
- ℹ️ **商品描述用 `white-space: pre-wrap` 顯示換行，不用 `v-html`**（避免 XSS，有測試）
- ℹ️ 手機一排 2 個、平板 3 個、電腦 4 個
- ℹ️ 金額只用來「顯示」（`formatPrice`），前端不做金額計算
- 🔲 **庫存顯示規則**（待和家人確認）：售完顯示「已售完」、剩 5 件以下顯示「僅剩 N 件」、其餘不顯示。門檻在 `utils/format.js` 的 `LOW_STOCK_THRESHOLD`
- 🔲 **售完的商品仍會出現在列表**（淡化＋「已售完」標籤）。如果希望直接隱藏，要改後端查詢
- 🔲 **首頁的標語**「包包與布料／每一件都是家裡用心挑選、製作的商品」是暫定的，請家人提供正式文案
- ✅ **「加入購物車」按鈕**：F03 已加到商品詳情頁
- ℹ️ 測試踩到的兩個坑（已修正並在程式裡註解）：
  - `watch(..., { immediate: true })` 寫在它用到的 `let` 變數宣告之前 → 一進頁面就錯（真的 bug，瀏覽器裡也會壞）
  - `beforeEach(() => mock.mockReset())` 沒加大括號 → 回傳的函式被 Vitest 當成清理函式，每個測試結束都多呼叫一次

## F03 購物車

- ℹ️ **`stores/cart.js`（Pinia）**：品項存 `{ productId, name, price, imageUrl, stock, quantity, status }`，存在 localStorage（`mamashop.cart`）
- ℹ️ 加入同一商品會累加；數量限制在 1～庫存，且單一商品最多 99（和後端 B08 一致）
- ℹ️ **打開購物車頁時向後端更新每個商品**（`refresh()`）：改價、庫存不足（自動調低數量）、售完、下架都會提示。網路錯誤不改變狀態，避免誤判成下架
- ℹ️ **小計用「分」的整數計算**，避免 JavaScript 小數誤差（0.1 × 3）。金額只用來顯示，**結帳以後端計算為準**
- ℹ️ 售完／下架的品項會標示、不算進小計，可以刪除
- ℹ️ header 的購物車圖示顯示總件數（`el-badge`），Pinia store 改變時自動更新
- ℹ️ `ElMessage`（提示訊息）是函式不是元件，按需載入不會自動帶入，要在檔案裡明確 import 元件和樣式
- 🔲 **購物車不分會員**：存在瀏覽器，不需要登入；登出不會清空。同一台電腦換人用會看到上一個人的購物車（家用網站可接受；要分會員需要後端購物車 API）
- ✅ **「前往結帳」按鈕**：F05 已啟用
- 🔲 **購物車沒有跨裝置同步**（手機加的，電腦看不到）
- 🔲 每次打開購物車頁，每個品項各呼叫一次商品 API（品項多時請求多）；需要的話之後加後端「一次查多個商品」的 API

## F04 會員

| 網址 | 頁面 |
|---|---|
| `/login` | 登入（`?redirect=` 登入後回到原本的頁面） |
| `/register` | 註冊，成功後自動登入 |

- ℹ️ **路由守衛（`router/guards.js`）**：路由加 `meta: { requiresAuth: true }` 就需要登入，未登入導向 `/login?redirect=原網址`；`guestOnly`（登入、註冊頁）已登入時回首頁。目前還沒有需要登入的頁面，F05 結帳、F06 我的訂單會用到
- ℹ️ **前端守衛只是使用者體驗**：真正的保護在後端（沒有有效 token 一律 401）
- ℹ️ **防止 open redirect**：`safeRedirect()` 只接受站內路徑（`/` 開頭、不是 `//`），`/login?redirect=https://釣魚網站` 登入後會回首頁
- ℹ️ **打開網站時檢查 token**（`auth.loadProfile()`）：token 失效（過期、帳號停用、密碼重設）就安靜登出，不強制跳到登入頁；網路錯誤則保留登入狀態
- ℹ️ 登入 API 用 `skipAuthRedirect`：密碼錯誤的 401 不會觸發「token 失效 → 導向登入頁」
- ℹ️ 表單驗證規則（`utils/validation.js`）和後端一致：密碼 8～72 個英數符號、手機 09 開頭 10 碼；後端回傳的欄位錯誤（例如 Email 已註冊）顯示在對應欄位下方
- ℹ️ 登入只檢查有沒有填，不檢查密碼格式（不透露密碼規則給嘗試登入的人）
- ℹ️ header：已登入顯示名字（下拉選單：登出），未登入顯示「登入」
- ℹ️ **測試技巧**：Element Plus 表單驗證內部用 `setTimeout`，測試要用 `vi.waitFor(() => expect(...))` 等結果出現；「確認沒有送出」的斷言要放在「驗證訊息出現」之後，否則可能在驗證跑完前就檢查而誤判通過
- 🔲 **沒有「忘記密碼」**（需要寄信，M01 之後）
- 🔲 **沒有會員資料頁**（改名字、電話、改密碼）：範圍外，需要的話之後加
- 🔲 **登入沒有次數限制**（後端 B06 已記錄）

## F05 結帳

| 網址 | 頁面 |
|---|---|
| `/checkout` | 結帳（需要登入）：商品、運送方式、收件資訊、付款方式、預估總額 |
| `/checkout/complete/:orderNo` | 訂單完成（需要登入），向後端查詢訂單 |

- ℹ️ **未登入按「前往結帳」→ 登入頁 → 登入後自動回到結帳頁**（F04 的路由守衛）
- ℹ️ **只送商品 id 與數量**，價格與總額由後端計算；頁面上的金額標示「實際金額以訂單成立時為準」
- ℹ️ **送出時停用按鈕＋程式內再擋一次**：連點兩下只會送出一次（有測試）。後端的一次性 token 在 R02
- ℹ️ 進入結帳頁時先 `cart.refresh()` 更新價格與庫存；**下單瞬間庫存不足**（被別人買走）時顯示訊息並更新購物車，不清空
- ℹ️ 成功後**只移除已下單的品項**（售完、下架的留在購物車）
- ℹ️ 收件人預設帶入會員的姓名、手機，可以改（送禮）
- ℹ️ 宅配顯示地址欄位、超商取貨顯示超商／店號／門市名稱（`v-if` 切換，隱藏的欄位不會被驗證）；和後端 `@ValidShippingInfo` 一致
- ℹ️ 完成頁向後端查詢訂單（`GET /api/orders/{orderNo}`），重新整理也看得到，顯示的是後端實際成立的金額
- ℹ️ 實測：3 次完整流程（未登入 → 登入 → 結帳 → 完成）都成功，庫存 50 → 44，取消後恢復 50
- 🔲 **超商門市要顧客自己查店號**（頁面提示到超商官網查詢）；電子地圖選店在 P02
- 🔲 **沒有「常用地址」**：每次都要重新填地址
- 🔲 **下單後家人不會收到通知**（M01）；顧客也沒有確認信
- ℹ️ 驗證過程中曾有一次「登入後回到首頁而不是結帳頁」，之後重跑 5 次都無法重現，判斷是當時測試腳本在頁面切換時就操作造成的。上線前的手機實測（D08）請再留意一次

## F06 我的訂單

| 網址 | 頁面 |
|---|---|
| `/orders` | 我的訂單列表（需要登入），頁碼放在網址 `?page=`，每頁 10 筆 |
| `/orders/:orderNo` | 訂單詳情（需要登入）：進度、物流單號、商品、金額、收件與付款資訊、取消 |

- ℹ️ **只能看自己的訂單由後端保證**（B09：別人的訂單回 404，不是 403，不透露訂單存在）；前端看到 404 一律顯示「找不到這筆訂單」
- ℹ️ **進度條**：已下單 → 已確認 → 已出貨 → 已送達 → 已完成，每一步顯示時間；已取消／已退回不顯示進度，改顯示一行說明與時間。手機（< 640px）直向排列
- ℹ️ **取消只在「待確認／待付款」時顯示按鈕**，按下後再跳確認框。判斷規則和後端 `OrderStatus` 一致，但最後由後端決定：如果按下的瞬間家人剛好確認了訂單，後端回 409，前端顯示原因並**重新讀取訂單**，畫面變成最新狀態（有測試）
- ℹ️ **時間一律用台灣時間顯示**（`formatDateTime` 指定 `Asia/Taipei`），不受瀏覽器時區影響；用 `formatToParts` 自己組字串，避免不同瀏覽器的分隔符號不同，並用 `h23` 避免午夜顯示成 24 點
- ℹ️ 列表切換頁碼時，用「最後一次請求」判斷丟掉較慢回來的舊結果，避免快速換頁時畫面顯示錯頁
- ℹ️ header 登入後的下拉選單加上「我的訂單」；訂單完成頁加上「查看訂單」按鈕
- ℹ️ 實測（真實後端）：列表顯示兩筆訂單、備貨中的訂單完成 2 步且沒有取消按鈕、取消待確認訂單後顯示「已取消」與時間、不存在的訂單顯示「找不到這筆訂單」，瀏覽器無錯誤；測試資料先用管理員取消（還原庫存）再刪除
- 🔲 **沒有「再買一次」、沒有依狀態篩選**：範圍外
- 🔲 **取消不能填原因**：後端顧客取消 API 沒有原因欄位
- 🔲 **訂單狀態改變不會通知顧客**（M01）
- ⚠️ **開發環境的小狀況**：Vite dev server 第一次用到新的 Element Plus 元件時會重新最佳化依賴並重新整理頁面，自動化測試剛好在這時候操作會失敗，重跑即可。正式版（`npm run build`）不會發生

## F07 手機版面

做法：用 Playwright 模擬手機（320px、375px 寬，觸控），逐頁檢查三件事：有沒有左右捲動（內容超出畫面）、輸入框字體是否小於 16px、可點的東西是否太小。頁面：首頁、商品列表、搜尋、商品詳情、購物車、結帳（宅配／超商）、我的訂單、訂單詳情、登入、註冊、404、header 下拉選單。

- ℹ️ **輸入框在觸控裝置上改成 16px**（`main.css`）：iPhone Safari 遇到小於 16px 的輸入框，點進去會自動放大整個頁面。用 `@media (pointer: coarse)` 判斷「用手指操作」，iPad 也適用；電腦維持 14px
- ℹ️ **沒有用 `maximum-scale=1` 關掉縮放**：那樣可以避免自動放大，但也讓看不清楚的人不能用兩指放大，對長輩不友善
- ℹ️ **購物車在 320px 寬會超出畫面**（右邊小計被擠出去）：窄螢幕改成小計放在數量下面靠右；grid 中間欄用 `minmax(0, 1fr)` 讓長商品名換行而不是撐寬
- ℹ️ **可點範圍加大**：header 的連結、名字、購物車至少 44px 高；購物車的數量按鈕改成預設大小、「刪除」加內距；分類按鈕從 small（24px）改成預設（32px）；結帳的運送方式改成大尺寸（40px）；「修改購物車」「← 我的訂單」加上下內距
- ℹ️ **名字很長時 header 不會被擠爆**：名字最多顯示 7 個字，超過顯示「…」（測試過 11 個字的名字在 320px 寬）
- ℹ️ 分類按鈕列在手機上本來就是可以左右滑動（F02），不算超出畫面
- ℹ️ 修改後 320px、375px 所有頁面都沒有左右捲動、沒有小於 16px 的輸入框；電腦版（1280px）外觀不變
- 🔲 **文字中的連結**（登入頁的「註冊」、商品詳情的分類、404 的「回首頁」）高度約 20px：是段落中的文字連結，維持原樣
- 🔲 **CSS 版面沒有自動化測試**：jsdom 不會真的排版，無法測寬度。檢查腳本是一次性的，之後改版面請用手機或瀏覽器的手機模式看一次
- ⚠️ **還沒在真的手機上測過**（雲端環境只能模擬）。上線前（D08）請用 iPhone 和 Android 各實際操作一次完整流程，特別是 iPhone 點輸入框不會放大

## F08 體驗細節

F02～F06 做頁面時已經有載入中（骨架畫面）、錯誤、空狀態，F08 把每一頁檢查一次，補上「出錯之後怎麼辦」：

- ℹ️ **載入失敗可以「重新載入」**（共用元件 `LoadError.vue`）：首頁、商品列表、商品詳情、結帳、訂單完成、我的訂單、訂單詳情。原本只顯示紅色錯誤訊息，顧客只能自己重新整理整頁（手機上不好找）
- ℹ️ **後端沒開或重新啟動中（502／503／504）**：顯示「伺服器暫時無法使用，請稍後再試」，不是「系統發生錯誤」。部署新版本時後端會重啟幾秒，這段時間進來的人會看到這個訊息，按重新載入就好
- ℹ️ **搜尋或分類沒有商品時**有「看全部商品」按鈕；沒有任何篩選條件時不顯示（已經是全部了）
- ℹ️ **購物車連不到後端時提示**「目前無法確認最新的價格與庫存，送出訂單時會再確認」。原本會安靜地顯示舊資料
- ℹ️ **登入過期時提示**「登入已過期，請重新登入」再導向登入頁；原本沒登入就進需要登入的頁面不會顯示這句
- ℹ️ **頁面程式下載失敗**（`router/chunkError.js`）：除了首頁，每一頁的程式是進到那一頁才下載。網路不穩或剛部署新版本（舊檔名已經不存在）時下載會失敗，原本畫面完全沒反應。現在會重新整理到要去的頁面（載入最新版本）；10 秒內又失敗就不再重整（避免無限重新整理），改顯示「頁面載入失敗，請檢查網路後再試一次」
- ℹ️ 實測（正式 build，`vite preview`，手機尺寸）：後端關閉 → 首頁顯示載入失敗 → 啟動後端按重新載入 → 顯示商品；搜尋不存在的字 → 看全部商品；擋掉購物車頁的程式檔 → 重新整理一次後顯示錯誤訊息；登入後讓 token 失效 → 點我的訂單 → 登入頁＋過期提示；瀏覽器無錯誤
- 🔲 **換頁時沒有頂端進度條**：每頁程式很小（幾 KB），目前感覺不到等待；網路很慢時才會注意到
- 🔲 **沒有離線提示**（例如「目前沒有網路」橫幅）：每個請求失敗時已經會說「無法連線到伺服器，請檢查網路」
- ⚠️ **部署注意（D03）：瀏覽器送 POST 時會帶 `Origin` header**。實測時前端在 4173 port、後端 CORS 只允許 5173，結果 GET 都正常、登入（POST）卻失敗。正式環境經過 Nginx 轉發時，後端看到的 Host 可能和 Origin 不同，Spring 會把它當成跨來源請求檢查：`CORS_ALLOWED_ORIGINS` 要設定成正式網域（例如 `https://example.com`），或讓 Spring 讀 `X-Forwarded-*` header（`server.forward-headers-strategy`）。上線前要實際測一次登入、下單

## A01 後台框架

| 網址 | 頁面 |
|---|---|
| `/admin` | 後台首頁（只有管理員）：A01 只有歡迎訊息，A05 加入概覽數字 |

- ℹ️ **兩種版面**：前台 header 從 `App.vue` 移到 `layouts/StoreLayout.vue`；後台用 `layouts/AdminLayout.vue`（左側選單＋上方列）。`App.vue` 只負責選版面：路由 meta 有 `layout: 'admin'` 就用後台版面
- ℹ️ **後台頁面一律是 `/admin` 的子路由**：權限標在 `/admin` 上（`requiresAuth`、`requiresAdmin`），子路由自動繼承，之後新增的後台頁面不會忘記加權限。有測試檢查沒有寫在 `/admin` 外面的後台路由
- ℹ️ **權限**：未登入 → 登入頁（登入後回到後台）；已登入但不是管理員 → 顯示「找不到這個頁面」、網址不變，不讓顧客知道有後台。**前端檢查只是體驗**，真正的保護是後端 `/api/admin/**` 回 403（B06）
- ℹ️ 角色來自登入時存的會員資料；打開網站時 `loadProfile()` 會向後端更新一次。就算有人改 localStorage 把自己改成 ADMIN，也只看得到空的後台框架，所有後台 API 仍然是 403
- ℹ️ **後台程式分開打包**（`() => import(...)`）：一般顧客不會下載到後台的程式
- ℹ️ **手機也能用後台**（家人多半用手機）：小於 768px 時選單收起來，按左上角「☰」從左邊滑出，點選單項目後自動關閉
- ℹ️ 管理員在前台的會員選單多一個「管理後台」；後台上方有「回前台」「登出」，後台登出後到登入頁
- ℹ️ 選單項目寫在 `AdminLayout.vue` 的 `ADMIN_MENU`，A02～A06 每個功能加一行
- ℹ️ 實測（正式 build）：未登入開 /admin → 登入頁 → 管理員登入回到 /admin、沒有前台 header；一般會員看不到「管理後台」、開 /admin 顯示找不到頁面；手機選單抽屜正常、沒有水平捲動；瀏覽器無錯誤
- 🔲 **沒有分角色權限**（例如只能看訂單、不能改商品）：目前只有 ADMIN 一種，PLAN 決定所有家人一起管理

## A02 後台商品管理

| 網址 | 頁面 |
|---|---|
| `/admin/products` | 商品列表（含下架）：搜尋、分類、狀態篩選、分頁；每列可以上下架、調整庫存、編輯 |
| `/admin/products/new` | 新增商品 |
| `/admin/products/:id/edit` | 編輯商品 |

- ℹ️ **列表用卡片式而不是表格**（el-table）：家人多半用手機，表格在手機上要左右捲動
- ℹ️ **上下架直接在列表用開關切換**；失敗時開關自動回到原本的位置（開關綁的是後端回傳的狀態）
- ℹ️ **庫存調整用「進貨／減少＋數量」**，送增減量給後端（B07 的決定，避免蓋掉同時間顧客下單扣的庫存）。對話框顯示「調整後」預覽，減到負數時不能送出；送出後以後端回傳的數字為準
- ℹ️ **庫存只在新增時填初始數量**；編輯頁只顯示目前庫存與狀態，提示到列表調整（PUT 本來就不會改庫存）
- ℹ️ **新增後是下架狀態**（B07 的決定），提示「確認無誤後在列表上架」
- ℹ️ **圖片在瀏覽器先處理再上傳**（`utils/image.js`），解決 B07 留下的三個問題：
  - 長邊縮到 1600px、存成 JPG（品質 0.85）：實測 4032×3024、7.7MB 的照片 → 1200×1600、888KB（測試圖是隨機雜訊，壓縮效果最差；一般照片會更小）
  - **EXIF 全部移除**（包含 GPS 拍攝地點）：canvas 重畫只留像素。實測上傳後伺服器上的檔案 EXIF 0 個欄位
  - 依照 EXIF 方向轉正（直拍的照片不會變橫的），實測正確
  - iPhone 的 HEIC：Safari 讀得懂，重新輸出就是 JPG。Chrome 讀不了 HEIC 時顯示「無法讀取這張圖片，請改用 JPG 或 PNG」
- ℹ️ **新增時圖片上傳失敗**：商品已經建立了，帶到編輯頁讓管理員重新選圖片（避免再按一次「建立」產生重複商品）。編輯時失敗則留在原頁、提示再按一次儲存
- ℹ️ **改了沒儲存就離開會先確認**（返回、點選單都算）
- ℹ️ 編輯頁的左側選單仍然亮「商品」（路由 `meta.menu`）
- ℹ️ 上架後前台搜尋得到、下架後搜尋不到，實測正確
- 🔲 **沒有刪除商品**（B07 決定不提供，不賣了就下架）
- 🔲 **分類選單用前台的 `/api/categories`**：A03 做分類管理時再看要不要改成後台 API
- 🔲 **沒有批次操作**（一次上下架多個）：商品數量少，用不到
- 🔲 **沒有防止兩個人同時編輯同一個商品**：後面儲存的會蓋掉前面的（商品沒有版本欄位，B07 沒加樂觀鎖）。家人同時編輯同一個商品的機會很低，庫存已經用增減量避免了真正會出錯的情況
- ⚠️ 上傳失敗的圖片或換掉的舊圖片由後端清理（B07）；測試時上傳的檔案已手動刪除

## A03 分類與運費設定

| 網址 | 頁面 |
|---|---|
| `/admin/categories` | 分類：列表（依排序）、新增、修改（名稱、排序）、刪除 |
| `/admin/shipping` | 運費設定：宅配、超商取貨各一張卡片，改名稱、運費、啟用、排序 |

- ℹ️ **分類新增與修改共用一個對話框**；新增時排序預設排在最後面。名稱重複（後端 409）顯示在名稱欄位下方
- ℹ️ **刪除分類先確認**；底下還有商品（包含下架的）時後端拒絕並說明原因
- ℹ️ **後端錯誤訊息修改**：`CATEGORY_HAS_PRODUCTS` 原本是「請先移動或**刪除**商品」，但商品不能刪除（B07），改成「分類底下還有商品（包含下架的），請先把商品改到其他分類」。後端 279 個測試全部通過
- ℹ️ **運送方式每張卡片各自儲存**；沒改東西時「儲存」不能按，改了可以「還原」。儲存失敗時保留輸入
- ℹ️ **只剩一種啟用中時，開關直接鎖住**並說明原因；後端也會檢查（兩位管理員同時各停用一種時，由後端擋下）
- ℹ️ 頁面上說明「修改運費只影響之後的新訂單」：訂單成立時就把運費存進訂單（B08）
- ℹ️ 運費輸入框只接受整數（台灣運費沒有小數）；後端仍接受兩位小數
- ⚠️ **修正 A02 的 bug：價格不是 10 的倍數時無法儲存**。Element Plus 的數字輸入框是原生 `<input type="number" step="10">`，瀏覽器內建的表單檢查認為 1299、1280.5 這種「不是 step 倍數」的值不合法，**直接擋下送出、手機上沒有任何提示**。A02 實測剛好用了 1280、1180 所以沒發現；A03 改運費 65 時發現。修正：有數字輸入框的表單加 `novalidate`，只用 Element Plus 的驗證規則。已實測存 1299.5 成功，並加了測試確認表單有 `novalidate`
- ℹ️ 登入、註冊頁的 Email 欄位（`type="email"`）也會被瀏覽器檢查，但瀏覽器會顯示提示，不算 bug，維持原樣
- ℹ️ 實測（正式 build、手機尺寸）：新增分類 → 前台出現在最後；改排序 0 → 前台排第一；重複名稱提示；刪除有商品的分類被拒；運費改 65 → 前台結帳的運費跟著變；停用宅配 → 前台只剩超商、超商開關鎖住；最後都還原
- ⚠️ 雲端環境的 Maven 快取曾記錄一次下載失敗（`junit-platform-launcher`），清掉 `~/.m2` 裡那個套件的 `.lastUpdated` 與 `_remote.repositories` 後恢復。本機遇到「present, but unavailable」可以用同樣方式處理
- 🔲 **分類沒有顯示商品數量**：後端分類 API 沒有回傳，刪除時才會知道底下有沒有商品
- 🔲 **排序要手動填數字**，沒有拖曳排序：分類只有幾個，填數字就夠了

## A04 後台訂單管理

| 網址 | 頁面 |
|---|---|
| `/admin/orders` | 訂單列表：狀態標籤、運送方式篩選、分頁（新到舊）；輸入訂單編號直接打開 |
| `/admin/orders/:orderNo` | 訂單詳情：進度、操作按鈕、商品金額、收件資訊、下單會員、狀態紀錄 |

- ℹ️ **操作按鈕依狀態顯示**（`utils/adminOrderActions.js`），和後端 `OrderStatus.canTransitionTo` 一致：待確認 → 確認／取消；備貨中 → 出貨／取消；已出貨 → 已送達／退回；已送達 → 完成／退回；結束的訂單沒有按鈕。前端只決定顯示哪些按鈕，規則仍由後端把關
- ℹ️ **每個操作都先跳對話框說明會發生什麼**（例如「確認後顧客就不能自己取消」「取消後商品加回庫存，無法復原」），可以填備註（記在狀態紀錄）。後台以「不會按錯」為優先
- ℹ️ **出貨必填物流單號；退回一定要選「是否加回庫存」**，沒有預設值，避免把損壞的商品加回去賣，或忘了把好的加回去
- ℹ️ **樂觀鎖（B10）在前端的處理**：操作時帶上畫面上的版本號；成功後用回應裡的新版本號。後端回 409（別人剛改過，或狀態已經不允許）時，提示原因並**自動重新讀取**，讓家人看到最新狀態再決定。實測：兩支手機同時打開同一筆待確認訂單，B 先確認、A 用舊畫面按取消 → A 看到「資料已被其他人修改」，畫面變成備貨中，訂單**沒有**被誤取消
- ℹ️ **查訂單編號**（B10 提到的需求）：列表上方輸入編號直接打開詳情，自動轉大寫、去掉空白；不需要改後端
- ℹ️ 收件人與下單會員不同時（送禮），列表顯示「王媽媽（王小明 訂）」
- ℹ️ 收件人與會員的電話可以直接點擊撥打（`tel:`），Email 可以點擊寄信
- ℹ️ 商品名稱連到後台的商品編輯頁
- ℹ️ 從列表點進詳情再按「← 訂單列表」，會回到原本的狀態標籤與頁碼（用 vue-router 記錄的上一頁）
- ℹ️ 狀態標籤沒有「待付款」：只有接金流（P01）後才會出現，到時再加
- ℹ️ 實測（正式 build、手機尺寸）：一筆訂單走完確認 → 出貨（沒填單號被擋）→ 送達 → 完成（付款狀態變已付款）；一筆退回並加回庫存（庫存 9 → 10）；兩人同時操作；狀態紀錄 5 筆含備註；測試訂單已刪除、庫存已還原
- 🔲 **列表沒有快速操作**（例如在列表直接按確認）：每筆都要點進詳情。訂單量不大，先確保每個操作都看過詳情再按
- 🔲 **沒有各狀態的數量**（例如「待確認 3」）：後端沒有提供，A05 概覽會顯示
- 🔲 **沒有列印出貨單／揀貨單**
- 🔲 **出貨後物流單號不能改**（後端 B10 的限制）

## A05 後台首頁概覽

新增的後端 API：`GET /api/admin/dashboard`（限 ADMIN，新的 `dashboard` 模組）

| 數字 | 怎麼算 | 點下去打開 |
|---|---|---|
| 待確認 | 狀態 = 待確認 | 訂單列表（待確認） |
| 待出貨 | 狀態 = 備貨中 | 訂單列表（備貨中） |
| 超商未取件 | 超商取貨、狀態 = 已送達（到門市、還沒完成） | 訂單列表（已送達＋超商取貨） |
| 今日訂單 | 台灣時間今天 00:00 之後建立（含之後被取消的） | 訂單列表（全部） |
| 最近退回 | 最近 30 天內退回 | 訂單列表（已退回） |
| 低庫存商品 | 上架中、庫存 ≤ 5（含售完），庫存少的在前，最多列 20 件 | 商品編輯頁 |

- ℹ️ **後端新增模組 `dashboard`**：概覽同時讀訂單與商品，不屬於任何一個既有模組。只有 controller／service／dto，沒有自己的 entity，查詢用 order、product 的 repository
- ℹ️ **一個 API 回傳所有數字**，而不是前端呼叫好幾次列表 API 再看 `totalElements`：少很多次請求，數字也在同一個交易裡算出來
- ℹ️ **每個數字都是 COUNT 查詢**（Spring Data 的 `countBy...` 衍生查詢），不載入訂單資料；先不做快取，R 系列壓測時再看
- ℹ️ **「今天」用台灣時間**：伺服器（Docker）時區是 UTC，直接用 `LocalDate.now()` 會在台灣早上 8 點才換日。改成算出「台灣今天 00:00」的 `Instant` 再和 `created_at` 比。注入 `Clock` 讓測試可以固定時間，單元測試驗證了台灣 00:30（UTC 前一天 16:30）已經算新的一天
- ℹ️ **「最近退回」只算 30 天**：退回的總數只會一直增加，沒有參考價值
- ℹ️ 低庫存門檻 5 和前台「僅剩 N 件」一致（後端 `AdminDashboardService.LOW_STOCK_THRESHOLD`、前端 `LOW_STOCK_THRESHOLD`，改的話兩邊一起改）；下架商品不算
- ℹ️ 待確認、待出貨、超商未取件**有數量時用橘色提醒**；今日訂單、最近退回只是資訊，不提醒
- ℹ️ 有「重新整理」按鈕與更新時間；重新整理失敗時保留原本的數字並顯示「更新失敗」
- ℹ️ 測試：單元測試（換日點、各項數量）＋整合測試（測試資料庫可能有其他資料，比較建立測試資料前後的差）＋權限矩陣加入新 API。後端 287 個測試全部通過
- ℹ️ 整合測試放在 `order` 套件，是為了共用 `OrderTestData`（package-private）
- ℹ️ 實測（正式 build、手機尺寸）：建立 3 筆測試訂單後，概覽數字和直接查資料庫的結果一致（今日 3、待確認 3、低庫存 3）；點數字打開正確篩選的列表；測試資料已清除、庫存已還原
- 🔲 **數字不會自動更新**：要按重新整理或重新進入頁面。之後有新訂單通知（M01）再考慮
- 🔲 **沒有營業額、圖表**：PLAN 沒有要求
- 🔲 `created_at` 單獨沒有索引（只有 `(status, created_at)` 和 `(user_id, created_at)`），「今日訂單」會掃整張表。訂單量小沒有影響，R00 壓測時再看要不要加

## A06 後台管理員帳號

| 網址 | 頁面 |
|---|---|
| `/admin/accounts` | 管理員列表、新增、停用／啟用、重設密碼（B14 的 API） |

- ℹ️ **自己的帳號標示「你」，沒有停用按鈕**（後端也會擋：`CANNOT_DISABLE_SELF`）
- ℹ️ **停用前先確認**（「對方會立刻無法登入」）；啟用不用確認
- ℹ️ **新增管理員**：Email、姓名、手機（選填）、初始密碼（輸入兩次）。依 B14 的流程，建立後請對方登入，再用「重設密碼」改成自己的密碼。Email 已註冊時錯誤顯示在 Email 欄位下方
- ℹ️ **重設密碼**：新密碼輸入兩次；對話框提醒「該帳號所有裝置的登入都會失效」
- ℹ️ **重設自己的密碼**：提醒「要用新密碼重新登入」；成功後目前的 token 已經失效（B14：發行時間早於密碼修改時間），所以直接登出並帶到登入頁（登入後回到管理員頁），不讓使用者在下一個動作才遇到莫名其妙的 401
- ℹ️ 不提供刪除（B14：狀態紀錄要保留是誰操作的）
- ℹ️ 有 Email 輸入框的表單加 `novalidate`（同 A03：只用 Element Plus 的驗證）
- ℹ️ 實測（正式 build、兩支手機）：新增管理員 → 對方登入成功；重設對方密碼 → 對方下一個動作被帶到登入頁並提示「登入已過期」、舊密碼不能登入、新密碼可以；對方重設自己的密碼 → 直接回登入頁；停用對方 → 對方下一個動作被登出、無法登入；測試帳號已刪除
- ⚠️ **被停用的帳號登入時看到的是「Email 或密碼錯誤」**：後端（B06）刻意不透露帳號是否存在或被停用。家人如果說「密碼明明是對的卻登不進去」，先到這裡看帳號是不是被停用了
- 🔲 **修改自己的密碼不需要輸入舊密碼**（B14 已記錄）：有人拿到已登入的手機就能改密碼
- 🔲 **沒有權限分級**（B14 已記錄）

> 第 3 階段（後台 A01～A06）完成。

---

# 第 4 階段：上線

## D01 主機

> 租主機需要你本人操作（付費、帳號），所以這個任務的產出是**操作指南＋設定檔＋檢查腳本**，在 Ubuntu 24.04 容器裡完整演練過。實際租好主機、照指南做完、`check-server.sh` 全部 PASS，才算完成。

| 檔案 | 用途 |
|---|---|
| `docs/deploy/D01-server.md` | 操作指南：產生金鑰（Windows）→ 建立 deploy 帳號 → 只允許金鑰登入 → ufw → 自動更新 → swap → 時區 → 檢查；含被鎖在外面的處理與常見問題 |
| `deploy/server/sshd-hardening.conf` | SSH 設定，放到 `/etc/ssh/sshd_config.d/01-mamashop-hardening.conf` |
| `deploy/scripts/check-server.sh` | 只讀檢查，每項 PASS／FAIL／WARN，有 FAIL 時結束代碼 1 |

- ℹ️ **SSH 設定放 `sshd_config.d/` 而不是改主設定檔**：系統更新時比較不會衝突，也容易還原（刪掉檔案就好）
- ⚠️ **檔名必須 `01-` 開頭**：sshd 同一個設定「先讀到的為準」，Ubuntu 雲端主機的 `50-cloud-init.conf` 會打開密碼登入。容器實測：取名 `99-` 時 `sshd -T` 顯示 `passwordauthentication yes`（設定被蓋掉）；取名 `01-` 才是 `no`
- ⚠️ **Docker 公開的 port 會繞過 ufw**（直接改 iptables）。D03 的正式環境只有 Nginx 公開 80、443，資料庫與後端不寫 `ports`。檢查腳本除了看 ufw，也用 `ss` 檢查「實際對所有網卡監聽的 port」，實測模擬 5432 對外開放時會 FAIL
- ℹ️ **檢查用 `sshd -T`（實際生效的設定）**，不是 grep 設定檔：多個設定檔互相覆蓋時，只有 `sshd -T` 是準的
- ℹ️ 指南的順序是為了**不把自己鎖在外面**：先建立 deploy 並確認能登入、sudo 正常，才關閉 root 登入；改 SSH 設定時保留舊連線、另開視窗測試；ufw 先允許 OpenSSH 再啟用
- ℹ️ 容器演練結果：強化前密碼登入、root 登入 FAIL；強化後實際 SSH 連線——deploy 金鑰登入成功、root 被拒、密碼登入被拒（不會問密碼）；檢查腳本全部 PASS（容器無法開 swap，只有 WARN）；shellcheck 沒有問題
- ℹ️ 主機規格建議 2GB 記憶體（最少 1GB＋swap）、Ubuntu 24.04 LTS、台灣／東京／新加坡
- 🔲 **沒有 fail2ban**：已經只允許金鑰登入，暴力猜密碼不會成功；log 會比較多但不影響安全。需要的話之後再加
- 🔲 **SSH 沒有換 port**：換 port 只能減少 log 裡的掃描紀錄，不增加安全性，還要多記一個 port
- 🔲 雲端廠商的防火牆（Security Group）依廠商不同，指南只提醒「也設定成只開 22、80、443」

## D03 正式環境部署

> 和 D01 一樣，實際部署要等主機。這個任務的產出是**正式環境的設定檔＋部署指南**，並且在本機用正式設定（`deploy/docker-compose.prod.yml`、正式的 Nginx 設定）完整跑過一次。

| 檔案 | 用途 |
|---|---|
| `deploy/docker-compose.prod.yml` | db、backend、web（Nginx）三個服務；只有 web 對外開 80 |
| `deploy/nginx/Dockerfile` | 多階段：Node 建置前端 → Nginx 映像檔（75MB） |
| `deploy/nginx/default.conf` | SPA 路由、快取、gzip、`/api` 轉發、`/uploads` 直接讀 volume、413 回 JSON |
| `deploy/nginx/security-headers.conf` | CSP、nosniff、X-Frame-Options、Referrer-Policy、Permissions-Policy |
| `deploy/.env.example` | 正式環境設定範本（`deploy/.env` 不進版控，`.gitignore` 已涵蓋） |
| `.dockerignore`（根目錄） | 前端建置 context 用 repo 根目錄，只放行 `frontend/`、`deploy/nginx/`（不把 `.env` 送進 Docker） |
| `docs/deploy/D03-deploy.md` | 安裝 Docker → deploy key 下載程式碼 → 建立 `.env` → 建置啟動 → 檢查 → 更新流程、常用指令、常見問題 |

### 設計

- ℹ️ **資料庫、後端完全不寫 `ports`**：只有 Docker 內部網路連得到（Docker 公開的 port 會繞過 ufw，D01）。實測：主機連不到 8080、`docker port` 顯示兩者都沒有公開
- ℹ️ **前端與 API 同一個網址**（Nginx 依路徑分流），瀏覽器不會遇到 CORS；`CORS_ALLOWED_ORIGINS` 設成網站網址只是保險
- ⚠️ **`SERVER_FORWARD_HEADERS_STRATEGY=native`（F08 留下的問題）**：Nginx 送 `X-Forwarded-Proto/Host/Port`，後端用 Tomcat 的 RemoteIpValve 讀取（只相信內部網段來的 header）。實測在 Docker 網路裡模擬 HTTPS：帶 `X-Forwarded-Proto: https` 時登入 200；不帶時同一個請求被 Spring Security 當成跨來源 → 403。D04 改成 HTTPS 後一定要有這個設定
- ℹ️ **商品圖片由 Nginx 直接讀 volume**（唯讀掛載），不經過 JVM；快取一年（檔名是 UUID）。`location /uploads/` 與 `alias /srv/uploads/` 都以 `/` 結尾，避免經典的 alias 路徑穿越設定錯誤；實測 `..`、`%2f` 都回前端首頁，讀不到其他檔案；目錄列表 403
- ⚠️ **Nginx 預設只收 1MB 的請求**，上傳圖片會 413。`/api` 放寬到 11MB（後端上限 10MB）；超過時 Nginx 回和後端相同格式的 JSON（預設是 HTML，前端只會顯示「系統發生錯誤」）。實測 7.7MB 上傳成功、12MB 回 413 JSON
- ℹ️ **`proxy_pass` 用變數＋`resolver 127.0.0.11`**：直接寫 `proxy_pass http://backend:8080` 只在 Nginx 啟動時查一次 IP，後端容器重建換了 IP 就一直 502，要重啟 Nginx。實測：強迫後端重建後 IP 從 .3 變 .5，重啟期間回 502，啟動完成後不用重啟 Nginx 就恢復 200
- ⚠️ **`add_header` 的繼承陷阱**：location 裡有自己的 `add_header`，外層的就全部不繼承。安全 header 抽成 `security-headers.conf`，每個 location 都 `include`
- ℹ️ **API 回應的 header 去重**：Spring Security 也會送 `X-Content-Type-Options`、`X-Frame-Options`，`/api` 用 `proxy_hide_header` 隱藏後端的，只留 Nginx 的一份（同一個 header 出現兩次，有些瀏覽器會整個忽略）
- ℹ️ **CSP**（F04 的 NOTES 提到「正式環境再加 CSP」）：`script-src 'self'`（Vite 建置的 index.html 沒有內嵌程式）；`style-src` 需要 `'unsafe-inline'`（Element Plus 用 style 屬性）；`img-src` 加 `data:`（圖示）、`blob:`（A02 上傳前預覽）。實測在手機尺寸走完整流程（首頁、註冊、下單、後台概覽、確認訂單、選圖預覽、上傳），**沒有任何 CSP 違規**
- ℹ️ **快取**：`index.html` no-cache（改版後重新整理就拿到新的 JS 檔名）；`/assets/` 檔名有 hash，快取一年 immutable；JS 有 gzip
- ℹ️ **後端記憶體上限 768MB**（`mem_limit`），JVM 依此用 75% 當 heap；避免吃光 2GB 主機的記憶體
- ℹ️ 正式環境 `APP_PROFILE` 空白（不載入測試資料）、`API_DOCS_ENABLED=false`（實測 `/v3/api-docs` 404）；第一個管理員由 `.env` 建立
- ℹ️ Compose 專案名稱 `mamashop-prod`：和本機開發（`mamashop`）的容器、volume 分開，在同一台電腦測試不會動到開發資料庫
- ℹ️ 指南建議**後端、前端分開建置**：2GB 主機同時跑 Maven 和 npm 容易記憶體不足

### 測試方式的限制

- ⚠️ 雲端開發環境的網路要透過代理伺服器，Docker 建置時：前端用「多信任代理 CA」的測試用 Dockerfile 建置成功（和正式檔案只差兩行）；後端的 Maven wrapper 用 busybox `wget` 下載 Maven，不支援這個代理的連線方式，所以改成在主機上建好 jar、用**和 `backend/Dockerfile` 相同的執行階段**建映像檔。`backend/Dockerfile` 的建置階段在 B12 已經驗證過。測試用的檔案都在 scratchpad，沒有 commit
- 啟動、Nginx、網路、volume、header 等 D03 新增的部分，都是用正式的 `deploy/docker-compose.prod.yml` 與 Nginx 設定測試

### 刻意沒做

- 🔲 **沒有零停機更新**：更新後端時約 10～20 秒回 502（前端顯示「伺服器暫時無法使用」）。家庭小店可以接受；需要的話之後用兩個後端容器輪流更新
- 🔲 **在主機上建置映像檔**：簡單但慢、吃記憶體。D06（CI）之後可以改成 GitHub Actions 建好映像檔、主機只負責下載
- 🔲 HTTPS、HSTS 在 D04；log 輪替、健康檢查在 D07；備份在 D05

## D06 CI（GitHub Actions）

`.github/workflows/ci.yml`：PR、推到 main、手動觸發時執行。三個 job 平行跑：

| Job | 做什麼 |
|---|---|
| 後端測試 | PostgreSQL 17 service container＋`./mvnw -B test`；失敗時上傳 surefire 報告（保留 7 天） |
| 前端 | `npm ci` → oxlint、eslint（只檢查不修正、警告也算失敗）→ vitest → `npm run build` |
| 正式環境映像檔 | 檢查 `docker-compose.prod.yml`（用範本＋假密碼）→ 建置後端、Nginx 映像檔 → `nginx -t` |

- ℹ️ **CI 的後端測試跑在全新的空資料庫、沒有 `APP_PROFILE`（不載入測試商品）、只靠環境變數**。先在本機用一樣的條件模擬：287 個測試全部通過，確認測試沒有偷偷依賴本機的測試資料；測試結束後資料庫 0 筆商品（測試自己建、自己清）
- ℹ️ **JWT_SECRET 每次隨機產生**（`openssl rand` 寫進 `$GITHUB_ENV`），不寫死在 workflow 裡
- ℹ️ **lint 只檢查**：`package.json` 的 `lint` 會 `--fix`，CI 裡自動修了也沒人看到，所以直接呼叫 `oxlint --deny-warnings`、`eslint --max-warnings 0`
- ℹ️ **docker job 補上 D03 在雲端環境測不到的部分**：GitHub 的機器沒有代理問題，`backend/Dockerfile` 的建置階段（Maven wrapper 下載）在這裡完整驗證
- ℹ️ 同一個 PR 推新 commit 時取消還在跑的舊執行（`concurrency`）；權限只給 `contents: read`；Maven、npm 都有快取
- ℹ️ 本機用 `actionlint` 檢查過 workflow 語法；compose config、`nginx -t` 兩個步驟也在本機模擬過
- 🔲 **還沒設定「CI 沒過不能合併」**：要在 GitHub repo → Settings → Branches → Add branch protection rule（`main`）→ 勾選 Require status checks to pass，選這三個 job。這是 repo 設定，需要你自己在網頁上勾
- 🔲 **沒有自動部署**（PLAN 寫「之後可加」）：主機（D01）還沒準備好。之後可以在 main 通過 CI 後，用 SSH 連到主機執行 D03 的更新指令；或在 CI 建好映像檔推到 GitHub Container Registry，主機只負責下載（比在 2GB 主機上建置快很多）
- ℹ️ **第一次執行的實際結果**：三個 job 約 1.5 分鐘全部通過；log 確認 `Tests run: 287, Failures: 0`、`No active profile set`（沒有載入測試資料）。推新 commit 時舊的執行被自動取消（concurrency 有效）
- ℹ️ 第一次執行時 GitHub 警告 `actions/checkout@v4`、`setup-java@v4` 用的 Node.js 20 已淘汰，改成 `@v5`（Node.js 24）
- 🔲 **action 用大版本標籤**（`@v5`）而不是固定的 commit SHA：比較好讀、會自動拿到修正版；代價是理論上 action 被竄改時會受影響。這個專案只用 GitHub 官方的 action，風險低

## D07 基本監控

| 項目 | 狀態 |
|---|---|
| 健康檢查 API `GET /api/actuator/health` | ✅ |
| Docker 健康檢查（db、backend、web）、web 等 backend healthy 才啟動 | ✅ |
| Docker log 輪替（每個容器 3 × 10MB） | ✅ |
| 外部定期檢查＋通知 | ⏳ 要等網站上線；`docs/deploy/D07-monitoring.md` 有 UptimeRobot 的設定步驟 |

- ℹ️ **新增依賴 `spring-boot-starter-actuator`**。只暴露 `health`；`show-details: never`（只回 UP／DOWN，不公開資料庫種類、硬碟空間）；關閉 Kubernetes 的 probes 群組（Spring Boot 4 預設會在回應加 `groups`，測試抓到的）
- ℹ️ **路徑改成 `/api/actuator`**（`management.endpoints.web.base-path`）：正式環境的 Nginx 已經把 `/api` 轉給後端，不用另外設定 Nginx
- ℹ️ Spring Security 只放行 `GET /api/actuator/health`；其他 actuator 端點維持「預設要登入」。就算是管理員，`env`、`heapdump` 等也是 404（沒有暴露）
- ℹ️ **權限另外測**（`HealthEndpointTest`，10 個）：AccessControlTest 的完整性檢查只掃 Controller，看不到 Actuator 的端點。後端測試共 297 個全部通過
- ℹ️ **Docker 健康檢查**：backend 用映像檔內建的 busybox `wget` 打 health（每 30 秒、連續 3 次失敗 → unhealthy、啟動後 60 秒內失敗不算）；web 打首頁；db 原本就有 `pg_isready`
- ℹ️ **log 輪替寫在 compose 裡**（YAML anchor `x-logging` 給三個服務共用），跟著版控走，不用另外改主機的 Docker 設定
- ℹ️ 實測（正式設定）：啟動順序 db healthy → backend healthy → web；經過 Nginx 打 health 回 `{"status":"UP"}`、其他端點 401；**停掉資料庫：health 約 30 秒後回 503 DOWN（等連線池逾時），backend 變 unhealthy；資料庫恢復後 backend 自己恢復 healthy，不用重啟**；三個容器的 log 設定都是 json-file 3 × 10MB
- ℹ️ 本機下載 actuator 時 Maven Central 回 429（請求太多，雲端環境的代理造成），等幾分鐘後恢復
- ⚠️ **`unhealthy` 不會自動重啟容器**：Docker 的 restart 只在程式結束時生效。實測資料庫恢復後後端會自己恢復，所以先不加自動重啟（例如 autoheal），避免問題在資料庫時一直重啟後端反而更亂
- 🔲 **資料庫斷線時 health 要等 30 秒才回應**（Hikari 預設的連線逾時）。外部監控會把「逾時」也當成掛掉，一樣會通知，所以維持預設；縮短逾時會影響尖峰時的正常請求（R00 壓測時再看）
- 🔲 **沒有收集 log、沒有指標儀表板**（例如 Prometheus＋Grafana）：家庭小店用不到，PLAN 也只要求「基本」監控

---

# 第 5 階段：Redis

## R00 壓力測試基準

| 檔案 | 用途 |
|---|---|
| `loadtest/products.js` | k6 腳本：40% 列表（1～5 頁、一半篩選分類）、10% 關鍵字搜尋、50% 詳情；只讀不寫 |
| `loadtest/seed.sql`／`cleanup.sql` | 1000 個測試商品（`LT-` 開頭）的建立與刪除；重複執行 seed 不會重複新增 |
| `loadtest/README.md` | 執行步驟（Windows cmd）、怎麼看結果、**基準數據與解讀** |

**基準（4 vCPU 雲端開發機、正式環境設定、1000 個商品、暖機 30 秒後各測 60 秒）**：

| VUS | QPS | p95 | p99 | 錯誤率 |
|---|---|---|---|---|
| 10 | 1,302 | 15.0 ms | 20.6 ms | 0% |
| 50 | 1,386 | 70.3 ms | 90.3 ms | 0% |
| 100 | 1,348 | 158.1 ms | 216.5 ms | 0% |

- ℹ️ **10 個使用者時 QPS 就飽和了**，之後只有回應時間增加。回應時間 ≈ 併發數 ÷ QPS（利特爾法則），實測吻合
- ℹ️ **瓶頸是 CPU**：50 VUS 時後端 153%、資料庫 103%、k6 54%、Nginx 31%，合計約 340%／400%。R01 的快取主要省下資料庫查詢（列表每次 2 個查詢、詳情 1 個）與一部分後端工作
- ℹ️ **k6 用 Docker 執行**（`grafana/k6:1.4.2`，固定版本），加入整套服務的 Docker 網路直接打 `http://web`：不用安裝 k6，Windows、Linux 指令一樣
- ℹ️ **打的是正式環境設定的整套服務**（經過 Nginx），不是直接打後端：比較接近上線後的實際路徑
- ℹ️ **固定 VUS、不等待**（constant-vus、沒有 sleep）：每次條件一樣，R01 才能公平比較；量的是「最多撐多少」而不是模擬真實顧客的瀏覽速度
- ℹ️ **先暖機 30 秒再測**：JIT 還沒編譯、資料庫快取是空的時候數字會特別差
- ℹ️ 腳本在 `setup()` 先抓分類與商品 id，不把 id 寫死；上架商品少於 100 個時直接停止並提示先執行 seed.sql
- ℹ️ 依 API 加 tag（list／search／detail），結果分開列；門檻：錯誤率 < 1%、列表與搜尋 p95 < 500 ms、詳情 p95 < 300 ms
- ⚠️ k6 的 `--summary-export` 在 Linux 會因為 k6 容器不是 root 而寫不進掛載的資料夾（permission denied）；README 改成把輸出導到檔案（`> loadtest\results\xxx.txt`），Windows、Linux 都可以用
- ⚠️ **數字只能和同一台機器、同樣步驟的結果比較**：k6 與服務在同一台機器互搶 CPU；正式主機（1～2 vCPU）的絕對數字會低很多
- ℹ️ 以實際需求來說，家庭小店的尖峰大約每秒幾個請求，目前的效能綽綽有餘；R00／R01 的目的是**量化快取的效果**
- 🔲 **沒有壓測結帳、後台**：寫入的效能瓶頸在資料庫鎖（B08 已用併發測試驗證正確性），PLAN 的 R00 只要求商品列表與詳情
- 🔲 **沒有在正式規格的主機上壓測**：要另外開一台同規格的測試主機（不能壓正式站）

## R01 商品快取（Redis）

### 做了什麼

- 新增 `spring-boot-starter-cache`、`spring-boot-starter-data-redis`；本機與正式環境的 compose 加上 `redis:7.4-alpine`（不寫硬碟、最多 64MB、`allkeys-lru`；正式環境沒有 ports）
- **讀取**：`ProductService` 的商品詳情（key＝id）與**沒有關鍵字的**列表（key＝`分類:頁碼:每頁筆數`）標 `@Cacheable`
- **清除**：`ProductCacheEvictor` 集中處理，三個地方呼叫：後台改商品（新增、基本資料、上下架、庫存、圖片）、訂單的庫存異動（`OrderStockService` 扣庫存、歸還庫存）、分類改名
- Redis 的 key 長這樣：`mamashop:productDetail::5`、`mamashop:productList::all:1:12`

### 設計（和傳統 Spring MVC 的差別：Spring Cache 抽象層）

- ℹ️ **`@Cacheable` 是 AOP**（和 `@Transactional` 一樣靠代理）：呼叫方法前先查快取，有就直接回傳、方法根本不執行。Service 的程式碼一行都不用改；換成 Caffeine 或關掉快取只要改設定。注意：同一個類別裡自己呼叫自己（self-invocation）不會經過代理，快取不會生效
- ℹ️ **不自己建立 CacheManager**，而是用 `RedisCacheManagerBuilderCustomizer` 調整 Spring Boot 自動建立的那個：`spring.cache.type=none` 就自動換成不快取的版本（測試用）
- ℹ️ **值用 JSON 存、每種快取指定型別**：record 是 final 類別，通用的 JSON serializer 不會在 JSON 裡記錄型別，讀回來是 Map 而轉型失敗；JDK 序列化要每個 DTO 實作 Serializable，也不好讀。用 Jackson 3 的 `JacksonJsonRedisSerializer` 指定型別，`redis-cli` 也看得懂內容
- ℹ️ **清除列表用 SCAN 不用 KEYS**（`BatchStrategies.scan`）：KEYS 在 key 很多時會卡住整個 Redis（單執行緒）
- ⚠️ **清除時用 `evictIfPresent()`／`invalidate()`，不用 `evict()`／`clear()`**：Spring 的 Cache 介面規定後兩者「可以延後或非同步執行」，呼叫完不保證已經清掉。**測試實際抓到**：用 `clear()` 時，改完庫存馬上查列表還是舊的
- ℹ️ **交易 commit 之後才清除**：commit 前清的話，別的請求可能在空檔讀到還沒 commit 的舊資料又存回快取；交易 rollback（例如庫存不足下單失敗）就不清。用 `TransactionSynchronization.afterCommit` 自己控制，而不是 RedisCacheManager 內建的 `transactionAware()`：內建的在 commit 後清除失敗時會把例外丟給呼叫端，變成「訂單成立了但顧客看到 500」
- ℹ️ **列表一律全部清除**：一個商品的變動可能影響很多頁（排序、上下架、分類篩選），逐頁計算容易出錯；家庭小店的商品變動不頻繁
- ℹ️ **關鍵字搜尋不快取**：關鍵字可以是任意文字，每個都存會塞滿 Redis（也可以被拿來攻擊），命中率又低
- ℹ️ **Redis 掛掉時網站照常運作**：`LoggingCacheErrorHandler` 讓快取讀寫失敗只記 log、改查資料庫；清除失敗也只記 log；連線逾時 0.5 秒，不會把網站拖慢；**健康檢查排除 Redis**（`management.health.redis.enabled=false`），快取掛了不會讓整個網站被判定為 DOWN
- ℹ️ **TTL 10 分鐘**：正常情況下變動時就清除，TTL 是保險（例如直接改資料庫、或清除失敗時）
- ⚠️ **一個已知的小空檔**：商品在 commit 後、清除前的極短時間內，如果有人剛好讀到舊資料並存回快取，舊資料會留到 TTL（最多 10 分鐘）。只影響顯示的庫存數字；**下單時的庫存檢查在資料庫（B08 的條件更新），不受快取影響，不會超賣**

### 測試

- ℹ️ **其他測試預設不快取**（`src/test/resources/application.properties` 設 `spring.cache.type=none`）：很多測試用 SQL 直接改資料再檢查 API，有快取的話會拿到舊資料
- ℹ️ `ProductCacheIntegrationTest`（9 個，真的 Redis）：用 SQL 繞過程式改資料庫，再查一次還是舊的 → 證明有快取；後台修改、下架、下單、取消、調整庫存、分類改名都會清；下單失敗（rollback）不清；關鍵字搜尋不快取
- ℹ️ `ProductCacheRedisDownTest`：Redis 連不到時讀寫都正常、沒有變慢
- ⚠️ **測試的連線數**：每個設定不同的測試類別會各自建立 Spring context，各有一個連線池；新增兩個後超過 PostgreSQL 的 100 條上限（`too many clients already`）。測試設定連線池最多 4 條
- ℹ️ CI 的後端 job 加上 Redis service container。後端測試共 307 個全部通過（連續兩次）

### 壓測結果（和 R00 同一台機器、同樣步驟，詳見 `loadtest/README.md`）

| VUS | QPS | p95 |
|---|---|---|
| 50 | 1,386 → **2,214**（+60%） | 70.3 → **46.7 ms** |
| 100 | 1,348 → **2,365**（+75%） | 158.1 → **83.8 ms** |

- 資料庫 CPU 103% → 39%；**沒快取的搜尋幾乎沒變**（對照組），證明改善來自快取
- Redis 命中率 100%，944 個 key 只用 2 MB

### 之後要注意

- 🔲 **新增會改商品資料的功能時，記得呼叫 `ProductCacheEvictor`**，否則前台最多 10 分鐘看到舊資料
- 🔲 本機用 `docker compose down -v` 清空資料庫時，Redis 的快取還在（不同容器）：重建資料後商品 id 可能相同但內容不同，最多 10 分鐘內看到舊的。要立刻清：`docker compose exec redis redis-cli FLUSHALL`
- 🔲 Redis 沒有設定密碼：只在 Docker 內部網路（正式環境沒有 ports），外面連不到

## R02 防重複下單（Redis 一次性識別碼）

### 流程

1. 進入結帳頁：`POST /api/orders/checkout-token` → `{ token, expiresInSeconds: 1800 }`（隨機 UUID）。Redis 存 `mamashop:checkout-token:{會員id}:{token}` = `NEW`，30 分鐘過期
2. 送出訂單：`POST /api/orders`，header `Idempotency-Key: token`
3. 後端用 **Lua 腳本原子地「認領」**：

| Redis 裡的值 | 結果 |
|---|---|
| `NEW` | 改成 `PROCESSING`，建立訂單。成功 → `DONE:訂單編號`；失敗（例如庫存不足）→ 還原成 `NEW` |
| `PROCESSING` | 同一個識別碼正在處理（連點）→ 409 `ORDER_IN_PROGRESS` |
| `DONE:訂單編號` | 已經成功過（網路重送）→ **200 回傳原本那筆訂單**，不建立新的 |
| 不存在 | 過期、格式錯誤、亂填、別人的 → 409 `ORDER_TOKEN_INVALID`；前端自動換新的識別碼，請顧客再按一次 |

- 沒帶 header → 400 `ORDER_TOKEN_REQUIRED`
- 新建立回 201、重送回 200（都帶訂單內容），前端兩種都當成功

### 設計

- ℹ️ **`OrderService` 完全沒改**：新的 `OrderSubmissionService` 包在外面（認領 → `createOrder` → 標記完成／還原）。B08 的交易設計、既有的訂單測試都不受影響
- ℹ️ **`OrderSubmissionService` 刻意不加 `@Transactional`**：`createOrder` 回傳時它自己的交易已經 commit，這時才把識別碼標成 `DONE`。包在同一個交易裡的話，標成完成時訂單可能還沒真的寫進資料庫
- ⚠️ **認領一定要原子**：「讀出狀態 → 判斷 → 改成 PROCESSING」用 Lua 腳本在 Redis 裡一次完成（Redis 單執行緒，執行腳本時不會插入其他指令）。**反向驗證**：改成不原子的 GET＋SET，10 個執行緒同時送同一個識別碼，連續 3 次**全部 10 筆都建立了訂單**；用 Lua 是 1 筆
- ℹ️ **失敗時還原成 `NEW`**：庫存不足、資料有誤時，顧客修正後可以用同一個識別碼再送（前端也這樣做）
- ℹ️ **識別碼綁會員**（key 裡有會員 id）：拿別人的識別碼等於不存在，也不可能透過重送拿到別人的訂單
- ℹ️ **只接受 UUID 格式**：不把任意字串（很長、奇怪字元）拿來當 Redis key
- ℹ️ header 名稱用業界慣例的 `Idempotency-Key`（例如 Stripe API）；CORS 允許的 header 加上它
- ⚠️ **Redis 掛掉時「放行」（fail-open）**：發識別碼時照樣回一個（只是沒存），下單時跳過重複檢查、照常下單（記 warn log）。防重複是保護，不是下單的必要條件；Redis 壞掉時寧可冒一點重複下單的風險（前端仍會擋連點，F05），也不要讓所有人都不能下單
- ⚠️ **已知的小空檔**：訂單 commit 之後、標成 `DONE` 之前，如果後端剛好當機，識別碼會停在 `PROCESSING`，重送會得到「處理中」直到 30 分鐘過期（訂單本身已經成立，顧客到「我的訂單」看得到）。要完全消除得把識別碼和訂單寫在同一個交易裡（下面的資料庫做法）

### 和「資料庫唯一索引」做法的比較（PLAN：對照 Mission）

資料庫做法：orders 表加 `idempotency_key` 欄位＋唯一索引，INSERT 撞到唯一鍵就查出原本那筆回傳。

| | Redis 一次性識別碼（本專案） | 資料庫唯一索引 |
|---|---|---|
| 一致性 | 識別碼與訂單分開存，有上面那個極小空檔 | **和訂單在同一個交易**，完全一致 |
| 重複請求什麼時候被擋 | **一開始**（還沒查商品、扣庫存） | 最後 INSERT 訂單時；在那之前已經扣了庫存，撞到才 rollback，白做一次 |
| 「處理中」狀態 | 有（`PROCESSING`），可以明確告訴顧客 | 沒有：第二個請求會等第一個交易結束（鎖），然後失敗 |
| 識別碼清理 | TTL 自動過期 | 永久留在訂單表（也可以當作稽核紀錄） |
| 額外依賴 | 需要 Redis（掛掉時要決定放行或擋下） | 不需要 |
| Schema | 不用改 | 要加欄位與唯一索引（Flyway migration） |

**選 Redis 的理由**：PLAN 指定練習 Redis；重複的請求在最前面就擋下，不會白白扣庫存再 rollback；有「處理中」可以告訴顧客。**如果只能選一種、又不想多一個依賴，資料庫唯一索引更簡單也更一致**——兩種可以並用（Redis 擋掉大部分、資料庫唯一索引當最後防線），這個專案的規模用不到。

### 測試

- ℹ️ `OrderIdempotencyIntegrationTest`（7 個，真的 Redis）：同一個識別碼送兩次 → 201、200 同一筆，資料庫 1 筆、庫存只扣一次；10 個執行緒同時送 → 只建立 1 筆；失敗後同一個識別碼可以再送；沒帶、亂填、格式錯誤、別人的識別碼都被擋；發識別碼的端點
- ℹ️ `OrderIdempotencyRedisDownTest`：Redis 連不到時照樣可以取得識別碼、下單
- ℹ️ 既有的 `CreateOrderIntegrationTest` 改成每次下單前取得新的識別碼；權限矩陣加入新的端點。後端 318 個測試全部通過
- ℹ️ 前端：結帳頁載入時取得識別碼、送出時帶上；識別碼過期時自動換新的、第二次用新的送；其他失敗後沿用同一個（3 個測試）。前端 175 個測試通過
- ℹ️ 實測（正式環境設定）：結帳成功後原封不動重送 → 200 同一筆；同一個識別碼同時送 5 次 → 1 個 201、4 個 409「處理中」；資料庫訂單數、庫存扣減都正確；瀏覽器無錯誤
- ⚠️ 實測時 Docker Hub 回 429（請求太多），重建映像檔改用「既有映像檔＋換掉 jar 與前端檔案」的方式（執行環境相同）

## R03 限時搶購（只寫設計說明，未實作）

> 依你的決定：家庭小店用不到搶購，先寫設計說明 `docs/R03-flash-sale-design.md`，之後需要再實作。PLAN 的 R03 維持未勾選。

- ℹ️ **為什麼需要**：B08 的條件更新正確（不超賣），但瞬間大量請求搶同一個商品時，所有交易排隊等同一列的鎖（熱點列），連線池被占滿，連帶整個網站變慢；100 件賣完後剩下的請求還是要進資料庫才知道失敗
- ℹ️ **做法**：第一關在 Redis 用 Lua 腳本原子地檢查活動時間、每人限購、扣名額、寫入佇列；只有搶到的人進第二關。第二關由背景消費者用少量連線建立訂單（削峰）
- ℹ️ **佇列推薦 Redis Stream**：扣名額與寫入佇列可以在同一個 Lua 腳本裡原子完成；用 RabbitMQ 會有雙寫問題（名額扣了、訊息沒送出），要加 Outbox 又得寫資料庫。RabbitMQ 留給 M 系列的通知（訊息來源是資料庫交易，寄信重試、死信佇列是它的強項）
- ℹ️ **失敗處理**：冪等（`flash_sale_orders` 主鍵 (活動, 會員)）、補償（名額 +1）、`XAUTOCLAIM` 重新投遞、死信、活動結束後對帳
- ℹ️ **Redis 掛掉時暫停活動**（fail-closed），和 R01、R02 的「照常運作」相反：R01、R02 的 Redis 是加速／額外保護，R03 的 Redis 是撐住搶購的核心
- ℹ️ 文件最後有實作時的任務拆分、驗證方式（含 B08 與 R03 的壓測對照）、面試問答
- ℹ️ 前面已經處理過的併發：B08 條件更新扣庫存＋依 id 排序避免死結（併發測試）、A02 庫存只送增減量、B10 樂觀鎖、R01 快取擋讀取、R02 原子認領防重複下單

---

# 第 6 階段：RabbitMQ

## M01 訂單通知（RabbitMQ＋Email）

### 流程

```
下單／確認／出貨（交易中）
  └─ ApplicationEventPublisher.publishEvent(OrderEvent)         ← 訂單模組只發事件，不知道誰處理
        │ 交易 commit 之後（@TransactionalEventListener，rollback 就不會發生）
        ▼
OrderNotificationPublisher（@Async，另一個執行緒）
  └─ RabbitTemplate → exchange mamashop.orders（topic）、routing key order.created／confirmed／shipped
        │ binding order.*
        ▼
queue mamashop.order-notifications（durable）
        ▼
OrderNotificationListener（@RabbitListener）→ 查最新的訂單 → OrderEmailComposer 組信 → JavaMailSender 寄出
```

| 事件 | 主旨 | 內容 |
|---|---|---|
| 下單 | 訂單已成立 | 品項、金額、運送方式、「確認後會再通知」 |
| 確認 | 訂單已確認，正在備貨 | 同上 |
| 出貨 | 訂單已出貨 | 物流單號、取件提醒（超商 7 天內取件付款） |

### 設計（和傳統做法的差別）

- ℹ️ **訂單模組只發「領域事件」**（`order/event/OrderEvent`），不依賴 RabbitMQ、Email；notification 模組聽事件。依賴是單向的（notification → order），之後加簡訊、LINE 通知，訂單的程式不用改
- ℹ️ **`@TransactionalEventListener`（AFTER_COMMIT）**：在交易裡發的事件，等 commit 之後才處理。下單失敗 rollback 就不會收到 → 不會寄出「訂單成立」卻查不到訂單。傳統做法常在 Service 最後直接呼叫寄信，交易還沒 commit、甚至之後 rollback，信已經寄出去了
- ℹ️ **`@Async` 送進 RabbitMQ**：顧客不用等。實測 RabbitMQ 停掉時下單仍然 41 ms 完成
- ℹ️ **訊息只放訂單編號與事件種類**（JSON），寄信時再查資料庫：拿到最新資料、訊息小、不含個資
- ℹ️ **Exchange／Queue／Binding 用 Bean 宣告**，後端啟動時自動建立；topic exchange 讓之後新增消費者（例如出貨通知物流）只要新增 queue 綁定，生產者不用改
- ℹ️ **信件不放收件地址、電話**（信可能被轉寄），只放品項、金額、物流單號與「查看訂單」連結（要登入）
- ⚠️ **JavaMail 的連線／讀取逾時預設是無限**：寄信伺服器卡住時，消費者執行緒永遠卡住、之後的信都寄不出去。設定 5 秒
- ℹ️ **`MAIL_HOST` 空白時不寄信、只記 log**：上線初期還沒選好寄信服務也能運作
- ℹ️ **RabbitMQ、寄信伺服器都排除在健康檢查之外**：通知掛了網站照常運作（同 R01 的 Redis）
- ℹ️ RabbitMQ 連線逾時 2 秒；正式環境 RabbitMQ 記憶體上限 256MB、沒有 ports、不裝管理介面；本機用 management 版本，管理介面 http://localhost:15672

### 已知的缺口（M03、之後處理）

- ⚠️ **寄信失敗的信就這樣不見了**：設定 `default-requeue-rejected=false`，失敗的訊息不放回佇列（否則同一封信無限重試、一直失敗、佔住消費者）。實測：寄信伺服器停掉時確認訂單成功，那封信的訊息被丟掉（佇列 0 筆），log 有 `MailSendException`。**M03 加上「重試幾次、還是失敗就放到失敗佇列」**
- ⚠️ **RabbitMQ 連不上時，那一則通知遺失**（只記 log）。實測：RabbitMQ 停掉時下單成功、log 有「送不進 RabbitMQ」；RabbitMQ 恢復後消費者自動重新連線，之後的信正常寄出。要完全避免得用 **Outbox 模式**（訊息和訂單寫在同一個交易的 outbox 表，再由排程送出），家庭小店的規模先記下來
- ⚠️ commit 之後、`@Async` 執行之前後端當機：同上，通知遺失
- 🔲 **正式環境的寄信服務還沒選**：需要有寄信額度、能設定 SPF／DKIM（不然信會進垃圾郵件）。例如 Gmail（應用程式密碼，每天約 500 封）、Brevo、Amazon SES、Resend。選好後填 `deploy/.env` 的 `MAIL_*`，並在 D08 實際寄一封確認
- 🔲 取消、退回、送達沒有通知（PLAN 只要求下單、確認、出貨）；只有純文字信，沒有 HTML 版型
- 🔲 管理員沒有「新訂單」通知（家人要開後台看 A05 的待確認數量）

### 測試

- ℹ️ `OrderEmailComposerTest`（3 個）：主旨、內容、金額格式、台灣時間、**不含地址電話**
- ℹ️ `OrderNotificationIntegrationTest`（真的 RabbitMQ、寄信換成 Mockito）：下單 → 確認 → 出貨各寄一封，收件人、主旨、物流單號正確；**下單失敗（rollback）不寄信**。非同步用 Mockito 的 `timeout()` 等待，不用固定的 sleep
- ℹ️ `OrderNotificationRabbitDownTest`：RabbitMQ 連不到時下單成功、1.5 秒內完成
- ℹ️ `OrderServiceTest`、`AdminOrderServiceTest`：建立、確認、出貨會發出對應的事件；庫存不足、取消不發
- ℹ️ 測試預設關閉通知（`app.notification.enabled=false`、消費者不啟動），避免每個測試的訂單都送進佇列、被別的測試拿到
- ℹ️ CI 的後端 job 加上 RabbitMQ service。後端 325 個測試全部通過
- ℹ️ 實測（本機 compose＋Mailpit）：下單、確認、出貨，Mailpit 收到 3 封，中文與版面正確；下單回應 278 ms（不用等寄信）

## M02 逾時自動處理（TTL＋死信佇列）

### 現在做的：宅配「已送達」7 天後自動完成

```
後台按「已送達」（交易中）
  └─ publishEvent(OrderDeliveredEvent)
        │ commit 之後（@TransactionalEventListener）、另一個執行緒（@Async）
        ▼
OrderAutoCompleteScheduler：只排宅配訂單
  └─ exchange mamashop.order-timeout（direct）、routing key auto-complete.wait、訊息 expiration = 7 天
        ▼
queue mamashop.order-auto-complete.wait     ← 沒有消費者，訊息放著等過期
  （x-dead-letter-exchange = mamashop.order-timeout、x-dead-letter-routing-key = auto-complete）
        │ 7 天到期 → RabbitMQ 把訊息「死信」轉出去
        ▼
queue mamashop.order-auto-complete
        ▼
OrderAutoCompleteListener → AdminOrderService.autoComplete：還是「已送達」才改成「已完成」＋已付款，狀態紀錄操作者 NULL（顯示「系統」）
```

### 設計

- ℹ️ **為什麼用 TTL＋死信，不用排程（@Scheduled 每小時掃資料庫）**：PLAN 指定的練習。兩種都可以；排程簡單、不怕訊息遺失，但要一直掃表；延遲訊息是「時間到才處理那一筆」。正式的大系統常兩個一起用（見下面的缺口）
- ℹ️ **每則訊息自己的 TTL（expiration），不是佇列的 x-message-ttl**：佇列參數宣告後不能改，之後把 7 天改成 10 天，後端啟動宣告佇列會失敗（PRECONDITION_FAILED），得先刪掉佇列（連同還在等的訊息）。每則訊息帶自己的時間，改設定只影響之後的訂單
- ⚠️ **RabbitMQ 只檢查佇列最前面那則有沒有過期**：同一個佇列的延遲都一樣（先送達先到期）就沒問題。把設定**改短**時，前面舊的 7 天訊息會擋住後面的，要等它們到期才會一起出來（晚一點完成，不會出錯）。所以一種延遲用一個等待佇列，之後「未付款自動取消」（30 分鐘）另外建一組
- ℹ️ **只自動完成宅配**：超商取貨的「已送達」是「已到店」，顧客還沒取貨付款；7 天沒取會退回。自動完成會把沒收到錢的訂單標成已付款，所以超商維持家人手動完成（看物流後台確認取貨）。消費者這邊再檢查一次狀態，排程端也只送宅配
- ℹ️ **冪等、不比對版本號**：7 天裡家人可能已經手動完成或改成已退回 → 不是「已送達」就什麼都不做。同一則訊息重複收到也一樣。和家人同時按「完成」的極端情況由 `@Version` 擋下（後寫入的 rollback），不會有兩筆「→ 已完成」
- ℹ️ **送達事件 `OrderDeliveredEvent` 和通知的 `OrderEvent` 分開**：送達目前不寄信（M01 只寄下單、確認、出貨），混在一起通知模組要多寫「不處理」的分支
- ℹ️ **新的 `timeout` 模組**：訂單模組只發事件、提供 `autoComplete`，不依賴 RabbitMQ（和 M01 的 notification 一樣的依賴方向）
- ℹ️ 設定：`app.order-timeout.auto-complete-after`（環境變數 `ORDER_AUTO_COMPLETE_AFTER`，預設 `7d`）；兩個佇列 durable、訊息 persistent，RabbitMQ 重開後還在等的任務不會消失（正式環境有 rabbitmq-data volume）

### 刻意沒做／之後要注意

- 🔲 **「線上付款逾時未付款自動取消並歸還庫存」**：要等接金流（P 系列）才有「待付款」的訂單。做法相同：下單時送一則 30 分鐘的訊息到另一個等待佇列，消費者檢查還是「待付款」就取消＋歸還庫存（`OrderStockService.restore`）。注意要和「付款成功的通知」競爭：用狀態檢查＋`@Version` 保證只有一邊成功
- ⚠️ **排定的時候 RabbitMQ 連不上 → 這筆訂單不會自動完成**（只記 log），家人照樣可以手動完成，不會出錯。要補可以加一個每天跑一次的排程：找「已送達超過 N 天」的宅配訂單補做（autoComplete 本來就冪等）。目前規模先記下來
- ⚠️ **處理失敗（例如那一刻資料庫連不上）的訊息會被丟掉**（`default-requeue-rejected=false`），同上，M03 處理重試與失敗佇列
- ⚠️ 測試時 `auto-complete-after` 縮成 2 秒；**不要在正式環境設很短的值**，家人來不及處理退貨就被自動完成
- 🔲 沒有在前台顯示「X 天後自動完成」；顧客看到的只有狀態變成「已完成」

### 測試

- ℹ️ `OrderAutoCompleteIntegrationTest`（真的 RabbitMQ，延遲縮成 2 秒，Awaitility 等待）：
  - 宅配送達 → 訊息先在等待佇列 → 約 2 秒後自動完成、已付款、狀態紀錄是「系統」
  - 時間到之前家人已手動完成 → 消費者略過，只有一筆「→ 已完成」
  - 超商取貨送達 → 不排定（等待佇列 0 筆）、狀態不變
- ℹ️ `AdminOrderServiceTest`：送達發出事件；`autoComplete` 操作者 NULL、已完成／已退回／不存在的訂單不動
- ℹ️ 測試預設關閉（`app.order-timeout.enabled=false`）。後端 331 個測試全部通過
- ℹ️ 實測：RabbitMQ 裡等待佇列的參數是 `x-dead-letter-exchange=mamashop.order-timeout`、`x-dead-letter-routing-key=auto-complete`（`docker compose exec rabbitmq rabbitmqctl list_queues name arguments messages`）；log 顯示排定後 2.04 秒自動完成

## M03 失敗處理（重試＋失敗佇列）

### 流程

```
消費者丟例外（寄信伺服器連不上、資料庫剛好重啟…）
  └─ 重試：等 2 秒 → 再試、等 6 秒 → 再試（共 3 次；listener.simple.retry）
        │ 還是失敗
        ▼ MessageRecoverer（MessagingFailureConfig）
exchange mamashop.failed（topic），routing key「error.」＋原本的 key，header 加上錯誤訊息與 stack trace
  ├─ error.order.*        → mamashop.order-notifications.failed   （M01 通知信）
  └─ error.auto-complete  → mamashop.order-auto-complete.failed   （M02 自動完成）
＋ ERROR log：「訊息重試後仍然失敗，已移到失敗佇列（routing key …，內容 {"orderNo":…}）：原因」
```

生產者這邊：`spring.rabbitmq.template.retry`，RabbitMQ 連不上時重試 2 次（等 1 秒、2 秒），在 @Async 執行緒裡，不影響回應時間。

### 設計

- ℹ️ **重試在消費者的執行緒裡做（stateless retry）**：訊息不回到佇列，在記憶體裡等一下再呼叫一次。好處是簡單、不會讓失敗的訊息在佇列裡打轉；代價是重試的那 8 秒，這個消費者不處理別的訊息（家庭小店的量沒問題）
- ℹ️ **用 RepublishMessageRecoverer，不用佇列的 x-dead-letter-exchange**：
  - 已經存在的佇列不能改參數，加 DLX 得刪掉重建（M01 的佇列已經在用）
  - 死信只知道「被拒絕」，Republish 會在 header 放 `x-exception-message`、`x-exception-stacktrace`、`x-original-routingKey`，查原因方便
- ℹ️ **每個來源一個失敗佇列**：修好之後整個佇列搬回原本的佇列就能重送（見下面的指令）
- ℹ️ **失敗佇列沒有消費者、不會過期**：一直留著直到有人處理。這就是「不會默默遺失」
- ℹ️ **重送是安全的**：通知信重送頂多多寄一封；自動完成本來就冪等（不是「已送達」就略過）
- ℹ️ 失敗的 log 有訊息內容（只有訂單編號、事件種類，沒有個資），正式環境不用開管理介面也知道是哪一筆

### 失敗佇列怎麼處理（正式環境，在主機上）

```bash
docker compose -f deploy/docker-compose.prod.yml exec rabbitmq rabbitmqctl list_queues name messages
```

- 列出每個佇列有幾則訊息。`*.failed` 不是 0 就要處理。原因看後端 log：`logs backend | grep 失敗佇列`

修好原因後（例如寄信帳密改對了、重啟後端），把失敗的訊息搬回原本的佇列重新處理：

```bash
docker compose -f deploy/docker-compose.prod.yml exec rabbitmq rabbitmq-plugins enable rabbitmq_shovel
docker compose -f deploy/docker-compose.prod.yml exec rabbitmq rabbitmqctl set_parameter shovel replay-notifications '{"src-protocol":"amqp091","src-uri":"amqp://","src-queue":"mamashop.order-notifications.failed","src-delete-after":"queue-length","dest-protocol":"amqp091","dest-uri":"amqp://","dest-queue":"mamashop.order-notifications"}'
```

- `rabbitmq-plugins enable rabbitmq_shovel`：打開 Shovel 外掛（RabbitMQ 內建，「把訊息從 A 搬到 B」的工具）。只要做一次
- `set_parameter shovel replay-notifications '...'`：建立一個叫 replay-notifications 的搬運工作
  - `src-queue` → `dest-queue`：從失敗佇列搬回通知佇列
  - `src-uri`／`dest-uri` 是 `amqp://`：同一台 RabbitMQ 內部直接連，不用帳密
  - `src-delete-after: queue-length`：**只搬現在有的這幾則**，搬完這個工作自己刪掉（不會一直搬）
- 自動完成的失敗佇列：把三個地方換成 `replay-auto-complete`、`mamashop.order-auto-complete.failed`、`mamashop.order-auto-complete`
- 本機實測：失敗佇列 2 則 → 執行後失敗佇列 0、通知佇列 2，shovel 自動刪除

不要重送、直接丟掉：`exec rabbitmq rabbitmqctl purge_queue mamashop.order-notifications.failed`

### 刻意沒做／之後要注意

- ⚠️ **送進 RabbitMQ 之前就失敗（RabbitMQ 停機超過重試時間、或 commit 後後端當機），通知還是會遺失**（M01、M02 寫的缺口）。生產者重試只能擋短暫斷線。完全避免要用 Outbox 模式，目前規模先不做
- 🔲 **沒有自動告警**：失敗佇列有訊息時不會主動通知。目前靠看 log／`list_queues`。之後可以在 D07 的監控加一項「失敗佇列訊息數 > 0」，或在後台儀表板顯示
- 🔲 沒有區分「重試也沒用」的錯誤（例如訂單被刪掉 → NotFound），一樣會重試 3 次再進失敗佇列；只多花 8 秒，不影響正確性
- ℹ️ RabbitMQ 停機期間「已經在佇列裡」的訊息不受影響（durable＋persistent），恢復後繼續處理

### 測試

- ℹ️ `OrderNotificationFailureTest`（真的 RabbitMQ，寄信用 Mockito 控制失敗次數，重試間隔縮成 100 ms）：
  - 第一次寄信失敗、第二次成功 → 只寄 2 次、失敗佇列是空的
  - 一直失敗 → 正好試 3 次，訊息進失敗佇列，內容有訂單編號，header 有錯誤訊息與原本的 routing key，之後不再重試
- ℹ️ 原本 M01、M02 的整合測試照樣通過。後端 333 個測試全部通過

## P01 第三方線上金流（綠界 ECPay 信用卡）

### 流程

```
顧客結帳選「線上付款」→ 訂單「待付款」（庫存已扣），排定 30 分鐘後未付款自動取消（M02 的延遲任務）
  │
  ├─ 前端呼叫 POST /api/orders/{orderNo}/payment
  │    後端：鎖住訂單 → 建立 payments 一筆（交易編號 = 訂單編號＋兩位數序號）→ 算檢查碼 → 回傳表單欄位
  ├─ 前端組一個 form，整頁 POST 到綠界付款頁；顧客在綠界輸入信用卡（卡號不經過我們的伺服器）
  │
  ├─ 綠界「伺服器對伺服器」通知 POST /api/payments/ecpay/notify（ReturnURL）
  │    後端：驗證檢查碼 → 商店代號 → 鎖住訂單 → 這筆已付款？（重複通知）→ 模擬付款？→ 金額相符？
  │          → 成功：付款紀錄 PAID、訂單「備貨中＋已付款」、寄「已收到付款」的信 → 回 1|OK
  │
  └─ 顧客按「返回商店」回到 /orders/{orderNo}?from=payment
       前端：還是「待付款」就每 3 秒重讀（最多 30 秒）——回到網站不代表付款成功，只看後端的狀態

30 分鐘到 → 還是「待付款」→ 取消、歸還庫存（狀態紀錄「系統」）；已付款就略過
```

### 設計（和傳統做法的差別、為什麼這樣做）

- ℹ️ **付款結果只相信綠界的伺服器通知**（PLAN 的要求）：顧客瀏覽器帶回來的東西都可以偽造，只用來「回到訂單頁」。通知的參數有**檢查碼**（SHA-256，只有綠界和我們知道 HashKey／HashIV），算出來不一樣就拒絕
- ℹ️ **檢查碼算法用綠界文件的範例當單元測試**，並在本機用測試商店**實際送到綠界測試環境**：
  - 後端產生的表單（含中文商品名）綠界接受、顯示付款頁、金額正確
  - 簽錯會回「CheckMacValue Error」
  - Java 的 URLEncoder 和綠界用的 .NET UrlEncode 差在 `! ( )`，要換回來（`EcpayCheckMacValue`）
- ℹ️ **比對檢查碼用 `MessageDigest.isEqual`**（比較時間固定），不用 `equals`（理論上可以從回應時間一個字一個字猜，timing attack）
- ℹ️ **一張訂單、多筆付款嘗試（新的 `payments` 表）**：
  - 綠界的交易編號不能重複使用，顧客關掉付款頁再付一次就要新的編號
  - 每次嘗試的結果（綠界交易編號、付款方式、失敗原因）都留著，查帳、退款用
- ℹ️ **冪等（重複通知）**：綠界沒收到 `1|OK` 會重送，也可能同時送兩次。鎖住訂單後看付款紀錄已經是 PAID 就直接回 `1|OK`，不會再寄信、再改狀態
- ℹ️ **悲觀鎖（`SELECT ... FOR UPDATE`）而不是只靠 `@Version`**：
  - 付款通知、逾時取消、顧客按「前往付款」都可能同時發生，而且都是「系統」，沒有人可以看到 409 再按一次
  - 鎖住的話，後到的一方**等**前一方做完、再看最新狀態處理
  - 後台管理員的操作還是用版本號比對：要知道「畫面上看到的是不是最新的」，這是鎖解決不了的
- ⚠️ **踩到的坑：Hibernate 的一級快取讓 `FOR UPDATE` 讀到舊資料**（併發測試抓到的）：
  - 原本查付款紀錄時 `JOIN FETCH` 了訂單，之後再用 `FOR UPDATE` 查訂單
  - 資料庫有等鎖，但 Hibernate 發現訂單已經在記憶體（persistence context）裡，**回傳舊的物件** → 用舊版本號更新 → `ObjectOptimisticLockingFailureException`
  - 修正：**先鎖、再讀**。付款通知先只查「訂單編號」（不載入 entity），第一次載入訂單就是加鎖的那一次
  - 所有流程都是「先鎖訂單、再讀付款」，順序一致也不會互相等待（deadlock）
- ℹ️ **取消後才付款成功（逾時取消和付款同時，或顧客在付款頁拖太久）**：
  - 訂單**維持已取消**，不「復活」：庫存已經還回去、可能被別人買走了
  - 標記「已付款」，狀態紀錄寫「請到綠界後台退款」，log 記 ERROR，後台訂單頁顯示紅色退款提醒
  - 同一張訂單付了兩次（開了兩個付款頁）也一樣
- ℹ️ **付款期限前 10 分鐘就不能再開付款頁**：降低「付到一半被取消」的機率
- ℹ️ **只開信用卡（`ChoosePayment=Credit`）**：
  - ATM、超商代碼要等顧客去繳費，可能超過付款期限
  - 綠界測試環境的付款頁還會顯示 Apple Pay、街口等即時付款，一樣走同一個通知
- ℹ️ **綠界測試後台的「模擬付款」（`SimulatePaid=1`）不改訂單**：沒有真的收到錢
- ℹ️ **金額有小數不能線上付款**：綠界只收整數。下單時就擋，請顧客改貨到付款
- ℹ️ 訂單模組不依賴綠界：
  - payment 模組呼叫 `AdminOrderService.onlinePaymentReceived`／`autoCancelUnpaid`
  - 逾時取消用 M02 的 TTL＋死信佇列（另一組等待佇列），失敗走 M03 的失敗佇列
- ℹ️ **CSP 的 `form-action` 加上綠界的兩個網址**：前往付款是表單 POST 到綠界，沒加的話瀏覽器會擋（D03 的 security-headers.conf）
- ℹ️ 通知信依付款方式調整文字：線上付款不寫「收到商品時付款」，成立時提醒付款期限，付款後寫「已收到您的付款」

### 設定

| 環境變數 | 本機（`.env.example`） | 正式環境（`deploy/.env`） |
|---|---|---|
| `ECPAY_ENABLED` | `true` | 簽約前 `false`（只有貨到付款） |
| `ECPAY_MERCHANT_ID`／`HASH_KEY`／`HASH_IV` | 綠界**公開的測試商店** 3002607 | 綠界廠商後台 → 系統開發管理 → 系統介接設定 |
| `ECPAY_CASHIER_URL` | 預設測試環境 | 預設正式環境（prod compose） |
| `ORDER_PAYMENT_TIMEOUT` | 30m | 30m |

- ⚠️ HashKey／HashIV 外洩 = 任何人都能偽造「付款成功」，和 `JWT_SECRET` 同等級的機密，只放在主機的 `deploy/.env`
- ⚠️ `ECPAY_ENABLED=true` 但沒填完整 → 後端**啟動就失敗**（`EcpayProperties`），不要等到顧客付款才發現
- 測試信用卡：`4311-9522-2222-2222`，有效期限任何未來的月年，安全碼 `222`（3D 驗證碼依綠界測試頁指示）

### 刻意沒做／之後要注意

- 🔲 **本機收不到綠界的通知**：ReturnURL 必須是外面連得到的網址（`SITE_URL`），本機是 localhost。本機是用 curl 自己算檢查碼「假裝綠界」送通知來驗證（NOTES 下面的指令）；**完整流程要等 D04（HTTPS 網域）上線後，用測試商店實際刷一次測試卡**
- 🔲 **退款沒有串 API**：目前是後台看到「已取消＋已付款」的提醒後，到綠界廠商後台手動退款。家庭小店的量手動就夠
- 🔲 **沒有主動查詢交易（QueryTradeInfo）**：如果綠界的通知一直送不到（我們的伺服器剛好掛了很久），訂單會停在待付款、然後被逾時取消，但錢已經扣了。綠界會重送通知幾次；之後可以加「取消前先問綠界這筆有沒有付款」
- 🔲 後台訂單列表沒有「需要退款」的篩選，要點進訂單才看得到紅色提醒
- 🔲 沒有開發票（電子發票要另外串，PLAN 有提到法規要求）
- ⚠️ **正式上線前**：和綠界簽約、確認費用與撥款方式（PLAN 的提醒），用正式商店資料先刷一筆小額再退款確認

### 測試

- ℹ️ `EcpayCheckMacValueTest`：綠界文件範例的檢查碼完全一致；竄改金額、錯的 HashKey、沒有檢查碼都不通過；大小寫不影響
- ℹ️ `PaymentIntegrationTest`（真的資料庫，通知用 MockMvc 送 form 到真的 API、不帶 JWT）：
  - 下單是待付款、表單欄位與檢查碼正確、第二次前往付款換新的交易編號
  - 別人的訂單 404、貨到付款的訂單、已付款的訂單不能再付
  - 付款成功 → 備貨中＋已付款、狀態紀錄是「系統」
  - 同一筆通知送 3 次只處理一次
  - 偽造、竄改、金額不符、模擬付款都不改訂單；付款失敗訂單維持待付款、可以再付
  - 逾時取消歸還庫存；取消後才付款 → 維持已取消、標記退款；付款後逾時 → 不取消
  - **併發**：付款通知和逾時取消同一瞬間（10 輪）→ 結果一定一致；同一筆通知同時 5 次 → 只處理一次
- ℹ️ `OrderPaymentTimeoutIntegrationTest`（真的 RabbitMQ，期限縮成 2 秒）：沒付款 → 取消、庫存還原；貨到付款不排定
- ℹ️ `OrderServiceTest`：線上付款下單是待付款並排定逾時；沒開線上付款、金額有小數會被擋
- ℹ️ 前端：結帳頁選線上付款 → 下單後前往綠界；前往付款失敗導向訂單頁；訂單頁顯示付款期限、從綠界回來會重讀狀態
- ℹ️ 後端 360、前端 182 個測試全部通過
- ℹ️ **實測**（本機後端＋綠界測試環境）：
  - API 下單 → 前往付款 → 表單送到綠界，回應「選擇支付方式」頁、金額 NT$280、中文商品名正確
  - 自己算檢查碼送通知：第一次 `1|OK`、訂單備貨中＋已付款；重複 `1|OK`；竄改 `0|CheckMacValue Error`
  - Mailpit 收到「訂單已成立（線上付款）」和「已收到您的付款」兩封
  - 手機畫面（Playwright）：訂單頁顯示付款期限與按鈕，按下後跳到綠界測試付款頁

本機模擬綠界通知（把訂單編號換成自己的；HashKey／HashIV 是公開的測試值）：

```bash
python3 - M2611140000000001 <<'PY' > notify.txt
import sys, hashlib, urllib.parse
KEY, IV = 'pwFHCqoQZGmho4w6', 'EkRm7iFT261dpevs'
p = {'MerchantID': '3002607', 'MerchantTradeNo': sys.argv[1] + '01', 'RtnCode': '1', 'RtnMsg': '交易成功',
     'TradeNo': '2511140000000001', 'TradeAmt': '280', 'PaymentType': 'Credit_CreditCard', 'SimulatePaid': '0'}
s = 'HashKey=' + KEY + '&' + '&'.join(f'{k}={p[k]}' for k in sorted(p, key=str.lower)) + '&HashIV=' + IV
e = urllib.parse.quote_plus(s).lower().replace('%21', '!').replace('%28', '(').replace('%29', ')')
p['CheckMacValue'] = hashlib.sha256(e.encode()).hexdigest().upper()
print(urllib.parse.urlencode(p))
PY
curl -X POST localhost:8080/api/payments/ecpay/notify -H 'content-type: application/x-www-form-urlencoded' --data @notify.txt
```

## P02 超商電子地圖選店（綠界物流）

### 流程

```
結帳頁：選「超商取貨」→ 選超商品牌 → 按「選擇門市」
  ├─ 表單（收件人、備註、付款方式…）暫存到 sessionStorage
  ├─ GET /api/logistics/cvs-map → { action, fields } → 整頁 form POST 到綠界電子地圖
  │     LogisticsSubType：UNIMARTC2C／FAMIC2C／HILIFEC2C／OKMARTC2C（店到店）
  │     IsCollection：貨到付款 Y（只顯示能代收貨款的門市）、線上付款 N
  ▼
各家超商的地圖頁：顧客點門市
  ▼ 顧客的瀏覽器 form POST 到 ServerReplyURL（我們的後端）
POST /api/logistics/ecpay/cvs-map-reply → 檢查格式 → 303 轉址 /checkout?cvsBrand=SEVEN&cvsStoreCode=...&cvsStoreName=...&cvsStoreAddress=...
  ▼
結帳頁：拿回暫存的表單、填入門市（顯示店名、店號、地址）、把網址參數清掉 → 顧客送出訂單
```

### 設計

- ℹ️ **為什麼要經過後端轉址**：綠界用 form POST 回傳，前端是 Nginx 上的靜態檔案，不能接 POST。後端收下來再 303 轉址到結帳頁
  - 用 303 See Other 而不是 302：明確告訴瀏覽器「改用 GET 打開」
- ℹ️ **門市回傳沒有檢查碼，當成「顧客自己填的資料」**：
  - 這是顧客的瀏覽器送的，任何人都能自己組一個，但意義和以前手動填門市一樣：選錯只影響顧客自己
  - 所以只檢查格式：品牌認得、店號只能英數字、店名 1～50 字、去掉控制字元。格式不對就帶 `cvsMapError` 回結帳頁請顧客重選
  - 下單時後端照常驗證（同一套規則），P03 建立物流單時綠界會再確認門市
- ⚠️ **踩到的坑：CORS 把綠界的回傳擋成 403**（實際用瀏覽器跑一次才發現，MockMvc 測試不會帶 Origin）：
  - 綠界的頁面讓瀏覽器 form POST 過來時，瀏覽器會帶 `Origin: https://logistics-stage.ecpay.com.tw`
  - 不在允許清單 → Spring Security 的 CorsFilter 直接回 403「Invalid CORS request」。正式環境也一樣會發生（不是只有本機）
  - 修正：這兩個綠界回呼的網址（電子地圖回傳、P01 的付款通知）**不套用 CORS**，`CorsConfigurationSource` 對它們回傳 null
  - 為什麼安全：CORS 只管「別的網站的 JavaScript 能不能讀回應」，這兩個網址本來就公開、自己驗證內容。其他 API 的 CORS 照常（有測試確認）
- ℹ️ **跳到地圖前暫存表單**：
  - 整頁跳走再回來，頁面會重新載入，填好的收件人、備註會不見
  - 暫存在 sessionStorage：只在這個分頁、關掉就消失，有姓名電話不放 localStorage；拿一次就刪；只接受認得的欄位
- ℹ️ **換超商品牌會清掉已選的門市**（門市是那一家的）
- ℹ️ **從線上付款改成貨到付款也會清掉**：之前是用「不代收貨款」選的，那間門市可能不能取貨付款
- ℹ️ **沒開電子地圖（或查詢設定失敗）就維持手動填寫**：正式環境簽約前照常運作
- ℹ️ 地圖只需要商店代號，**不需要 HashKey**；P03 建立物流單才需要
- ℹ️ 預設店到店（C2C）：個人或小商店常用，寄件時把包裹拿到附近超商寄出。B2C（大宗寄倉）要先把貨送到物流中心，沒有 OK 超商
- ℹ️ CSP `form-action` 加上綠界物流的兩個網址；前端「整頁 POST 到外部網站」抽成 `utils/postForm.js`，付款和地圖共用

### 設定

| 環境變數 | 本機（`.env.example`） | 正式環境（`deploy/.env`） |
|---|---|---|
| `ECPAY_LOGISTICS_ENABLED` | `true` | 簽約前 `false`（手動填門市） |
| `ECPAY_LOGISTICS_MERCHANT_ID` | 綠界公開的店到店測試商店 2000933 | 綠界廠商後台的商店代號 |
| `ECPAY_LOGISTICS_C2C` | `true` | 和申請的物流服務一致 |
| `ECPAY_LOGISTICS_MAP_URL` | 預設測試環境 | 預設正式環境（prod compose） |

### 刻意沒做／之後要注意

- 🔲 **沒有把門市地址存進訂單**：只在結帳頁顯示給顧客確認。店號＋店名就足夠寄件；P03 若需要再加欄位
- 🔲 「已選門市」沒有保留到下一次購物（每次結帳重新選）
- ⚠️ 綠界測試環境的地圖是「模擬頁」（固定一間測試門市、一個「確定門市」按鈕），正式環境才是各家超商真的地圖。上線後用手機實際選一次

### 測試

- ℹ️ `CvsMapServiceTest`：
  - 品牌對應物流子類型（C2C 加後綴、B2C 沒有 OK）、代收貨款、手機版
  - 回傳轉址：中文有編碼；店號有奇怪字元、不認得的品牌、店名空白或太長 → `cvsMapError`；控制字元被去掉
- ℹ️ `CvsMapReplyIntegrationTest`（MockMvc）：**帶綠界的 Origin** 也能 303 轉址；別的網站呼叫其他 API 照樣 403
- ℹ️ `AccessControlTest`：新的 3 支 API
- ℹ️ 前端：
  - 有開地圖時不顯示手動欄位、沒選門市不能送出
  - 按「選擇門市」會暫存表單並跳到地圖；沒選超商會提示
  - 從地圖回來拿回表單、填入門市、清掉網址，送出帶對的門市
  - 換品牌清掉門市；回傳有誤顯示訊息
- ℹ️ 後端 377、前端 188 個測試全部通過
- ℹ️ **實測**（本機後端＋前端＋綠界測試環境，Playwright 手機畫面）：
  - 結帳選 7-ELEVEN → 按「選擇門市」→ 跳到綠界「統一電子地圖」→ 按「確定門市」→ 回到結帳頁
  - 門市自動填入「7-ELEVEN 經貿（店號 238669）／台北市南港區三重路19號1樓」，備註還在
  - 送出訂單，資料庫存的是 238669／經貿（綠界回傳的店名後面有空白，已去掉）

## P03 物流單與狀態同步（綠界超商店到店）

### 流程

```
後台：備貨中的超商取貨訂單 →「建立物流單並出貨」
  ShipmentService（同一個交易，訂單鎖住）：
    檢查版本號、狀態 → POST 綠界 /Express/Create（MD5 簽章）→ 驗證回應的簽章
    → 存 shipments（綠界物流編號、寄件代碼、驗證碼）→ AdminOrderService.ship：已出貨、物流單號 = 寄件代碼、寄出貨通知信
家人：列印寄件單（或 7-11 ibon 輸入寄件代碼）→ 到超商寄件
綠界：POST /api/logistics/ecpay/notify（每次物流狀態改變；沒收到 1|OK 會重送，一天 3 次、共 3 天）
  驗證 MD5 簽章 → 鎖住訂單 → 更新物流單的最新狀態 → 依代碼自動更新訂單：
    2068／3032 賣家已到門市寄件      → 只記錄
    2063／2073／2098／3018／3029 到店 → 已出貨 → 已送達
    2067／3022 買家已取件            → 已完成＋已付款（取貨付款在取件時收錢）
    2074／3020 未取件、2099／3019 退回寄件門市 → 已退回（不自動加回庫存）
```

狀態代碼來源：綠界「物流貨態代碼表」（xlsx，從[物流狀態通知](https://developers.ecpay.com.tw/?p=7420)頁面下載），只取店到店（UNIMARTC2C、FAMIC2C、HILIFEC2C）。

### 設計（和傳統做法的差別、為什麼這樣做）

- ℹ️ **先用綠界測試環境實測，再寫程式**。實際打過的 API：建立物流單（7-11／全家／萊爾富）、列印寄件單、各種收件人姓名
  - **物流的簽章是 MD5，金流是 SHA-256**：同一個綠界，兩邊規定不一樣。用 SHA-256 打物流 API 會回「CheckMacValue驗證錯誤」
  - 簽章類別搬到 `common/ecpay`，加上演算法參數。綠界實際回傳的內容當成單元測試的資料
  - 收件人姓名規則（文件只寫 4～10 字元）實測出來：中文算 2、英文和空白算 1，不能有數字符號。「王小明」「John Smith」可以，「林」「Tom」「王小明2」不行
- ⚠️ **查代碼表時發現 OK 超商店到店已於 2026/7/1 停止服務**（代碼表的異動歷程）：
  - 結帳不再提供 OK 超商（前後端）
  - 電子地圖、建立物流單遇到 OK 會擋
  - 舊訂單照常顯示
  - P02 的電子地圖一起修正
- ℹ️ **結帳時就檢查收件人姓名格式**（超商取貨才檢查，宅配不限制）：建立物流單時綠界才拒絕就太晚了，家人還要聯絡顧客改名字。前後端同一套規則（`CvsNameRule`／`isValidCvsName`）
- ℹ️ **呼叫綠界時訂單是鎖住的**（同一個交易，最多等 15 秒）：
  - 兩位家人同時按、或顧客剛好取消，都不會建出兩張物流單、或「取消了卻寄出」
  - 代價是綠界慢的時候這張訂單的其他操作要等，家庭小店可以接受
  - ⚠️ 綠界建立成功、但我們之後的寫入失敗（極少見）：綠界那邊多一張沒人用的物流單，log 記 ERROR，到綠界後台刪除
- ℹ️ **一定要設逾時**（連線 5 秒、讀取 15 秒）：`RestClient` 預設無限等待，綠界沒回應時管理員的請求和訂單鎖會一直卡著
- ℹ️ **建立物流單 = 出貨**：沿用 B10 的 `ship`（版本號、狀態紀錄、出貨通知信都一樣），物流單號就是寄件代碼。7-11 是寄件代碼＋驗證碼
- ℹ️ **狀態通知「先鎖、再讀」、冪等、不倒退**（和 P01 一樣的寫法）：
  - 同一個代碼重複通知：不再寫紀錄
  - 比目前記錄的還舊（晚到）：略過
  - 「到店」的通知沒收到就先收到「已取件」：補一筆「已送達」再完成
- ℹ️ **未取件不自動加回庫存**：包裹還在路上，收到後確認商品沒壞再到商品管理調整（狀態紀錄有寫）。線上付款的訂單會加註「請到綠界後台退款」
- ℹ️ **列印寄件單**：
  - 後端組好簽章表單，後台開新分頁 POST 到綠界
  - 新分頁要在按下按鈕的當下開（還沒 await），等 API 回來才開會被瀏覽器當成彈出視窗擋掉
- ℹ️ 商品名稱固定寫「mamaShop商品」：綠界對商品名稱有字元限制，而且會印在寄件單上
- ℹ️ 呼叫綠界的 HTTP 那一層獨立成 `EcpayLogisticsGateway`，測試時換成假的；簽章、解析、訂單狀態都是真的
- ℹ️ Spring Boot 4 把 `RestClient.Builder` 的自動設定拆到另一個模組，這裡直接用 `RestClient.builder()`，不另外加依賴
- ℹ️ 綠界的回呼網址（P01 付款、P02 地圖、P03 物流通知）都不套用 CORS（見 P02）

### 設定

| 環境變數 | 本機（`.env.example`） | 正式環境（`deploy/.env`） |
|---|---|---|
| `ECPAY_LOGISTICS_HASH_KEY`／`HASH_IV` | 綠界公開的店到店測試值 | 綠界廠商後台（物流的，和金流不同） |
| `ECPAY_LOGISTICS_SENDER_NAME` | `Mama Wang`（這個檔案不能寫中文） | 家人的名字（可以寫中文） |
| `ECPAY_LOGISTICS_SENDER_CELL_PHONE` | 0911222333 | 家人的手機 |
| `ECPAY_LOGISTICS_API_URL` | 預設測試環境 | 預設正式環境（prod compose） |

- 沒填金鑰或寄件人：後台不顯示「建立物流單」，照舊手動填物流單號出貨（電子地圖不受影響）
- ⚠️ 物流的 HashKey／HashIV 外洩 = 任何人都能偽造「已取件」把訂單改成已付款，要保密
- `ECPAY_LOGISTICS_MAP_URL`（P02）改成 `ECPAY_LOGISTICS_API_URL`（物流的網址根目錄，地圖是 `/Express/map`）

### 刻意沒做／之後要注意

- 🔲 **本機收不到綠界的狀態通知**（和 P01 一樣，要外面連得到的網址）。本機用 curl 自己算 MD5 簽章「假裝綠界」送通知驗證。D04 上線後用測試商店實際寄一次，看通知是否送到
- 🔲 **宅配（黑貓、郵局）沒有串**：PLAN 只寫超商的狀態。宅配照舊手動填物流單號
- 🔲 **沒有取消物流單的 API**：物流單建好後要取消，到綠界後台處理，訂單在後台手動取消
- 🔲 萊爾富的列印寄件單在綠界測試環境回 HTTP 500（7-11、全家正常），正式環境上線後要實際印一次
- 🔲 沒有「到店」的通知信：超商會傳簡訊給收件人
- 🔲 物流狀態只保留最新一筆在 shipments；會改訂單的狀態都在訂單的狀態紀錄裡
- ⚠️ 狀態通知「不是即時」（綠界文件），通常幾小時內

### 測試

- ℹ️ `EcpayCheckMacValueTest`：MD5 驗證綠界測試環境實際回傳的內容；同一份資料用 SHA-256 不通過
- ℹ️ `CvsNameRuleTest`、前端 `validation.spec.js`：姓名規則，每個例子都在綠界測試環境實際送過
- ℹ️ `ShippingInfoValidatorTest`：超商取貨的收件人格式、OK 超商不能下單、宅配不限制
- ℹ️ `ShipmentIntegrationTest`（假的綠界 HTTP、真的資料庫，通知用 MockMvc 送 form 不帶 JWT）：
  - 建立物流單：送給綠界的欄位與 MD5 簽章正確，訂單已出貨、物流單號 = 寄件代碼＋驗證碼
  - 失敗情況：綠界拒絕（顯示綠界的原因）、連不到、回應簽章被竄改 → 訂單維持備貨中
  - 版本號不符、宅配訂單 → 不會呼叫綠界
  - 狀態通知：寄件（只記錄）→ 到店（已送達）→ 取件（已完成＋已付款）、重複通知、沒收到到店直接取件、未取件退回不加庫存、舊通知不倒退、偽造或用 SHA-256 簽的通知被拒絕
  - 列印寄件單的表單與簽章
- ℹ️ `EcpayLogisticsSandboxTest`：**真的連到綠界測試環境**建立物流單並驗證回應簽章。平常（含 CI）不跑，本機用 `ECPAY_SANDBOX_TEST=true` 打開
- ℹ️ 前端：後台建立物流單、綠界拒絕顯示原因、顯示物流狀態與列印、沒設定不顯示；結帳的姓名格式與超商選項
- ℹ️ 後端 418（1 個綠界測試環境的測試平常略過）、前端 208 個測試全部通過
- ℹ️ **實測**（本機後端＋前端＋綠界物流測試環境，Playwright 手機畫面）：
  - 下 7-11 超商訂單 → 確認 → 後台按「建立物流單並出貨」
  - 真的在綠界建立物流單：寄件代碼 D8858185、驗證碼 9836，訂單已出貨，顧客收到「已出貨」信（物流單號 D88581859836）
  - 「列印寄件單」開新分頁，顯示綠界的「7-ELEVEN 交貨便服務單」
  - 自己算 MD5 簽章送狀態通知：寄件 → 到店（已送達）→ 取件（已完成＋已付款），重複通知不多寫紀錄

本機模擬綠界物流通知（把物流編號換成後台顯示的「綠界物流編號」；HashKey／HashIV 是公開的測試值）：

```bash
python3 - 3799466 2073 包裹配達取件門市 "2026/11/16 09:00:00" <<'PY' > notify.txt
import sys, hashlib, urllib.parse
KEY, IV = 'XBERn1YOvpM9nfZc', 'h1ONHk4P4yqbl5LK'
p = {'MerchantID': '2000933', 'AllPayLogisticsID': sys.argv[1], 'RtnCode': sys.argv[2], 'RtnMsg': sys.argv[3],
     'UpdateStatusDate': sys.argv[4], 'LogisticsType': 'CVS', 'LogisticsSubType': 'UNIMARTC2C'}
s = 'HashKey=' + KEY + '&' + '&'.join(f'{k}={p[k]}' for k in sorted(p, key=str.lower)) + '&HashIV=' + IV
e = urllib.parse.quote_plus(s).lower().replace('%21', '!').replace('%28', '(').replace('%29', ')')
p['CheckMacValue'] = hashlib.md5(e.encode()).hexdigest().upper()
print(urllib.parse.urlencode(p))
PY
curl -X POST localhost:8080/api/logistics/ecpay/notify -H 'content-type: application/x-www-form-urlencoded' --data @notify.txt
```

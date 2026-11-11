# 任務紀錄與注意事項

每個任務完成後在這裡記錄：**和 PLAN 不同的決定**、**刻意沒做的**、**之後要注意的事**。
review PR 時搭配這份看；上線前（D08）再整份檢查一次。

狀態標記：🔲 待決定／待處理　✅ 已處理　ℹ️ 僅供參考

---

## 尚未 review 的 PR

PR #1–#9 已 merge，但你還沒看過。B 系列完成後一起研究。建議依序看，每個 PR 的重點列在下方各任務。

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

---

## 上線前一定要處理（D08 檢查清單）

- 🔲 **運費**：`shipping_methods` 目前是暫定值（宅配 100、超商 60），請家人確認後在後台調整（B07 之後才有後台）
- 🔲 **預設管理員密碼**：上線後登入修改，並從伺服器環境變數移除 `ADMIN_PASSWORD`（B14 才有改密碼功能）
- 🔲 **`JWT_SECRET`**：正式環境要用 `openssl rand -base64 48` 產生新的，不要沿用本機的
- 🔲 **正式環境不要設定 `APP_PROFILE=dev`**，否則會寫入測試商品
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

- 🔲 **圖片網址**：`imageUrl` 目前直接回傳 `image_path`，格式在 B07 決定
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

# 展示站：Vercel（前端）＋ Render（後端）＋ Neon（資料庫）

給面試官看的**免費展示站**。和正式站（D01～D08，自己的主機）是**同一份程式、同一個 main 分支**，差別只有設定。

```
瀏覽器 ──▶ Vercel（前端靜態檔）
              │ /api/*、/uploads/* 轉給後端（frontend/vercel.json）
              ▼
           Render（Spring Boot，Docker，免費方案）──▶ Neon（PostgreSQL）
```

| 檔案 | 用途 |
|---|---|
| `frontend/vercel.json` | 把 `/api`、`/uploads` 轉給 Render；其他網址回 `index.html`（前端路由）；安全 header |
| `render.yaml` | Render 的服務設定與環境變數（Blueprint） |

## 展示站和正式站的差別（先知道限制）

| | 正式站 | 展示站 |
|---|---|---|
| **閒置後** | 一直開著 | Render 閒置 15 分鐘休眠。**休眠後第一次打開要等 2～3 分鐘**，期間畫面顯示「伺服器暫時無法使用」，等一下重新整理就好 |
| **商品圖片** | 永久保存 | Render 每次重新部署或重啟，**上傳的圖片會消失**（資料庫裡的商品還在，只是沒有圖） |
| 通知信、自動完成、逾時取消 | 有（RabbitMQ） | 沒有 |
| 商品快取、登入次數限制 | 有（Redis） | 沒有 |
| 防重複下單 | 前端＋後端 | 只剩前端（送出後停用按鈕） |
| 線上付款、超商電子地圖、物流單 | 綠界正式商店 | 綠界**公開的測試商店**（不會真的扣款或寄件；測試卡號 `4311-9522-2222-2222`、有效期限任一未來日期、安全碼 `222`）。沒有 RabbitMQ，所以**沒付款的線上訂單不會逾時自動取消**，要到後台手動取消；Render 休眠時綠界的通知可能失敗，綠界會重送，訂單可能晚幾分鐘才變成已付款 |
| 商品 | 家人上架 | 13 個示範商品（`APP_PROFILE=dev` 的測試資料） |

> 為什麼等這麼久：免費方案只有 0.1 個 CPU。本機用同樣的限制（512MB、0.1 CPU）實測，Spring Boot 啟動要 150～170 秒；
> Vercel 轉發請求最多等 120 秒，所以喚醒的那一次會失敗，之後就正常。Render 實際的機器可能快一些或慢一些。

免費方案的額度（2026/11 查的，之後可能會變，以官網為準）：

- Render：512MB、0.1 CPU，閒置 15 分鐘休眠，檔案不保留
- Neon：每個專案 1GB、每月 100 CU-hours，閒置 5 分鐘自動暫停（下次連線自動恢復）
- Vercel（Hobby）：個人、非商業用途

---

## 1. Neon：建立資料庫

1. 到 <https://neon.com> 註冊，建立一個 Project：名稱隨意，**Region 選 Singapore**（離台灣近，也和後面的 Render 同一區）
2. 建立後按 **Connect**，會看到一串連線字串，長得像：

```
postgresql://neondb_owner:密碼@ep-xxxx-xxxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

把它拆成四個值記下來（第 2 步要填）：

| 值 | 連線字串裡的位置 | 例子 |
|---|---|---|
| `DB_USERNAME` | `//` 後面、`:` 前面 | `neondb_owner` |
| `DB_PASSWORD` | `:` 後面、`@` 前面 | |
| `DB_HOST` | `@` 後面、`/` 前面 | `ep-xxxx-xxxx.ap-southeast-1.aws.neon.tech` |
| `DB_NAME` | 最後一個 `/` 後面、`?` 前面 | `neondb` |

- ⚠️ `DB_HOST` 用**沒有 `-pooler`** 的那個（Connect 視窗把「Connection pooling」關掉）：後端自己有連線池，而且啟動時要執行資料表的建立（Flyway），需要直接連線
- 不用自己建資料表：後端第一次啟動時會自動建立，並載入示範商品

---

## 2. Render：部署後端

1. 到 <https://render.com> 註冊（用 GitHub 帳號登入最方便），授權它讀取 `mamaShop` 這個 repo
2. **New → Blueprint**，選 `mamaShop`。Render 會讀 repo 根目錄的 `render.yaml`，列出要建立的服務 `mamashop-demo-backend`
3. 它會要你填 `render.yaml` 裡標成「建立時輸入」的值：

| 值 | 填什麼 |
|---|---|
| `DB_HOST`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` | 第 1 步的四個值 |
| `SITE_URL` | 前端的網址。**Vercel 還沒部署，先填 `https://example.com`**，第 4 步再回來改 |
| `CORS_ALLOWED_ORIGINS` | 和 `SITE_URL` 一樣 |
| `ADMIN_EMAIL` | 展示用管理員的 Email，例如 `demo-admin@example.com` |
| `ADMIN_PASSWORD` | 展示用管理員的密碼（至少 8 碼）。**不要用你其他地方的密碼** |

4. 按 Apply。第一次要建置 Docker 映像檔（Maven 下載依賴＋編譯），大約 10 分鐘；之後啟動再等 2～3 分鐘
5. 服務頁面最上面會有網址，像 `https://mamashop-demo-backend.onrender.com`。**記下來**（第 3 步要用）。打開：

```
https://你的後端網址/api/actuator/health
```

看到 `{"status":"UP"}` 就成功了。再試 `https://你的後端網址/api/products`，應該有示範商品。

> Blueprint 建立失敗（例如 Render 說 `render.yaml` 某個欄位不認得——這個檔案是照文件寫的，沒有在 Render 上實際跑過）：
> 改用 **New → Web Service** 手動建立：Runtime 選 Docker、Root Directory 留空、Dockerfile Path 填 `./backend/Dockerfile`、
> Docker Build Context Directory 填 `./backend`、Instance Type 選 Free、Health Check Path 填 `/api/actuator/health`，
> 環境變數照 `render.yaml` 的 `envVars` 一個一個加（`JWT_SECRET` 用 Render 的 Generate 按鈕產生）。

看 log：服務頁面 → Logs。`Started MamashopApplication` 代表啟動完成；被強制結束（Out of memory）的話看最下面的常見問題。

---

## 3. Vercel：部署前端

1. **先改一個檔案**：`frontend/vercel.json` 裡有兩個 `https://mamashop-demo-backend.onrender.com`，換成第 2 步你自己的後端網址（如果一樣就不用改），commit、push 到 main
2. 到 <https://vercel.com> 註冊（用 GitHub 帳號），**Add New → Project**，選 `mamaShop`
3. 設定：
   - **Root Directory**：按 Edit，選 `frontend`（前端在這個資料夾裡）
   - **Framework Preset**：Vite（通常會自動選好。如果是 Other，`vercel.json` 的轉發可能不會生效）
   - 其他用預設值，不用加環境變數
4. 按 Deploy，大約 1 分鐘。完成後會給一個網址，像 `https://mamashop-demo.vercel.app`

---

## 4. 回 Render 更新前端的網址

Render → `mamashop-demo-backend` → **Environment**，把這兩個值改成第 3 步的 Vercel 網址（`https://` 開頭，結尾不要 `/`）：

- `SITE_URL`
- `CORS_ALLOWED_ORIGINS`

存檔後 Render 會自動重新啟動（再等 2～3 分鐘）。

> 沒改的話：打開網站看得到商品（GET 不受影響），但**登入、註冊、下單會失敗**——後端的來源檢查（CORS）只允許 `CORS_ALLOWED_ORIGINS` 列出的網址送出這類請求。

---

## 5. 檢查

用手機打開 Vercel 的網址：

1. 首頁、商品列表有示範商品
2. 註冊一個帳號 → 加入購物車 → 結帳（貨到付款）→ 我的訂單看得到
3. `/admin` 用展示用管理員登入 → 訂單管理 → 確認、出貨（手動填物流單號）→ 完成
4. 後台上傳一張商品圖片，前台看得到（之後 Render 重啟會消失，這是預期的）
5. 直接打開 `https://你的網址/products/1` 再重新整理，不會 404（`vercel.json` 的前端路由設定有生效）

---

## 之後更新

push 到 main 之後，Vercel 和 Render 都會自動重新部署（Render 的後端要等建置＋啟動）。

- 只想讓正式站更新、展示站不動：在 Render／Vercel 的專案設定把 Auto-Deploy 關掉，要更新時手動按
- 資料表有變更（新的 Flyway migration）：後端啟動時自動執行，不用手動處理

---

## 展示用帳號要注意

- 寫在履歷或 README 的展示帳號，**任何人都能登入**。如果公開的是管理員帳號，別人可以改商品、建立其他管理員、甚至把這個帳號的密碼改掉
- 建議：公開一個**一般顧客**的展示帳號；管理員帳號只在面試時提供，或寫「後台帳號請來信索取」
- 展示站的資料被亂改時：到 Neon 後台把資料庫清空（刪掉 Project 重建，或用 SQL Editor 執行 `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`），再到 Render 按 Manual Deploy → 重新啟動，後端會重建資料表與示範商品
- 展示站不要放真實顧客的資料

---

## 常見問題

| 狀況 | 可能的原因 | 檢查 |
|---|---|---|
| 打開網站商品出不來、顯示「伺服器暫時無法使用」 | 後端休眠中，正在喚醒 | 等 2～3 分鐘重新整理；直接打開後端的 `/api/actuator/health` 看 |
| 登入、註冊失敗，但商品看得到 | `CORS_ALLOWED_ORIGINS` 不是 Vercel 的網址 | 第 4 步；網址要完全一樣（`https`、沒有結尾的 `/`） |
| 重新整理 `/products/1` 變成 404 | `vercel.json` 沒有生效 | Root Directory 是不是 `frontend`、Framework Preset 是不是 Vite |
| `/api/...` 回 404 或 Vercel 的錯誤頁 | `vercel.json` 裡的後端網址不對 | 第 3 步的第 1 點 |
| Render 的 log：`Connection refused`／`password authentication failed`／`SSL` | 資料庫的四個值填錯 | 對照 Neon 的連線字串；`DB_HOST` 不要含 `postgresql://`、帳號密碼、`/資料庫名稱` |
| Render 顯示 Out of memory、一直重啟 | 記憶體超過 512MB | `JAVA_TOOL_OPTIONS` 有沒有設（`render.yaml` 的值）；還是不夠就把 `-Xmx256m` 改成 `-Xmx220m` |
| Render 部署一直停在 In progress 然後失敗 | 啟動太久，超過 Render 等待的時間 | 看 Logs 有沒有 `Started MamashopApplication`；有的話再手動部署一次 |
| 商品圖片不見了 | Render 重啟或重新部署 | 預期的行為（見最上面的差別表） |
| 想避免休眠 | — | 用外部監控（例如 D07 的 UptimeRobot）每 10 分鐘打一次後端的 `/api/actuator/health`。先確認 Render 免費方案每月的執行時數夠不夠、有沒有違反使用條款 |

---

## 實際操作紀錄（請自己填）

| 項目 | 內容 |
|---|---|
| 完成日期 | 2026-11-17 |
| 前端網址（Vercel） | <https://mamashop-demo.vercel.app> |
| 後端網址（Render） | `https://mamashop-demo-backend.onrender.com`（和 `vercel.json` 的預設值相同，不用改） |
| 資料庫（Neon） | AWS Asia Pacific (Singapore)，直接連線（沒有 `-pooler`） |
| 公開的展示帳號 | `test001@example.com`（一般顧客，README 有寫密碼） |
| 休眠後第一次打開實際等了多久 | （請自己量一次再填） |
| `render.yaml` 的 Blueprint 有沒有直接成功 | 有，照第 2 步就建立成功，不用手動建立 |

檢查（第 5 步）全部通過：註冊、加入購物車、結帳（貨到付款）、我的訂單、後台登入、確認→出貨→送達→完成、上傳商品圖片、直接打開 `/products/1` 重新整理不會 404。

遇到的問題與解法：

- **一開始沒有線上付款與 7-11 地圖**：最初的 `render.yaml` 刻意沒開綠界。2026-11-19 加上綠界公開測試商店的環境變數後打開（Render 沒有自動帶入新變數的話，到服務的 Environment 手動加上 `render.yaml` 裡 `ECPAY_` 開頭的那幾個）

- **Render 在建立 Blueprint 時找不到 repo**：註冊時授權 GitHub 沒有勾到 `mamaShop`。到 GitHub 的 Settings → Applications → Installed GitHub Apps（<https://github.com/settings/installations>）→ Render → Configure，在 Repository access 加上 `mamaShop` 後回 Render 重新整理
- **展示帳號的 Email 要是 Email 格式**（後端的註冊有檢查），只打 `test001` 不行，所以用 `test001@example.com`

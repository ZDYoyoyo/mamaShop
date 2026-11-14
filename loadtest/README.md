# 壓力測試（R00）

用 [k6](https://k6.io/) 壓測前台的**商品列表**與**商品詳情** API，記錄每秒處理幾個請求（QPS）、p95 回應時間、錯誤率。
這是 R01（Redis 商品快取）**加快取之前的基準**：R01 完成後用同一支腳本、同一台電腦、同樣的步驟再跑一次，比較前後差異。

> ⚠️ **只在本機或測試環境跑，不要壓正式站**（PLAN）。壓測會把伺服器的 CPU 用滿，正式站的顧客會打不開網頁。

| 檔案 | 用途 |
|---|---|
| `products.js` | k6 腳本：40% 商品列表、10% 關鍵字搜尋、50% 商品詳情；只讀不寫 |
| `seed.sql` | 建立測試資料：4 個分類、1000 個商品（900 上架），名稱以 `LT-` 開頭 |
| `cleanup.sql` | 刪除測試資料 |
| `results/` | 每次執行的原始輸出（不進版控） |

---

## 怎麼跑（Windows cmd，在 repo 根目錄）

k6 用 Docker 的官方映像檔執行，不用另外安裝。

### 1. 啟動正式環境設定的整套服務

用 D03 的正式設定（Nginx＋後端＋PostgreSQL），比直接打 `mvnw spring-boot:run` 的後端更接近上線後的樣子。

```bash
copy deploy\.env.example deploy\.env
```

用記事本打開 `deploy\.env`，`DB_PASSWORD`、`JWT_SECRET` 隨便填一串英數字（JWT_SECRET 要 32 字以上，本機測試用），然後：

```bash
docker compose -f deploy/docker-compose.prod.yml up -d --build --wait
```

- `--wait`：等所有服務健康檢查通過才結束（D07）
- 專案名稱是 `mamashop-prod`，和本機開發的資料庫分開，不會動到開發資料

### 2. 建立測試資料

```bash
docker compose -f deploy/docker-compose.prod.yml exec -T db psql -U mamashop -d mamashop < loadtest\seed.sql
```

- `exec -T db psql ...`：在資料庫容器裡執行 psql；`-T` 不要配置終端機，`< 檔案` 才能把 SQL 送進去
- 最後顯示 `900 | 1000` 就成功了。重複執行不會重複新增

### 3. 暖機（結果不看）

```bash
docker run --rm --network mamashop-prod_default -v "%cd%\loadtest:/scripts" grafana/k6:1.4.2 run -q -e VUS=50 -e DURATION=30s /scripts/products.js
```

- `--network mamashop-prod_default`：k6 容器加入整套服務的 Docker 網路，直接用 `http://web` 打 Nginx（不經過主機的 port）
- `-v "%cd%\loadtest:/scripts"`：把 loadtest 資料夾掛進容器
- 為什麼要暖機：Java 剛啟動時，JIT 編譯器還沒把常用的程式碼編譯成機器碼，資料庫的快取也是空的，前幾十秒的數字會特別差。正式測之前先跑 30 秒

### 4. 正式測量

```bash
docker run --rm --network mamashop-prod_default -v "%cd%\loadtest:/scripts" grafana/k6:1.4.2 run -e VUS=50 -e DURATION=60s /scripts/products.js > loadtest\results\vus50.txt
```

`VUS` 改成 10、100 各跑一次，可以看到使用者變多時回應時間怎麼變化。結果在 `loadtest\results\` 裡。

### 5. 收尾

```bash
docker compose -f deploy/docker-compose.prod.yml exec -T db psql -U mamashop -d mamashop < loadtest\cleanup.sql
docker compose -f deploy/docker-compose.prod.yml down -v
```

`down -v` 會刪掉這套測試環境的資料庫（`mamashop-prod_*` 的 volume），**只能在本機測試環境用**，不要在正式主機上執行。

---

## 怎麼看結果

```
http_req_duration..............: avg=35.78ms med=33.79ms p(90)=62.26ms p(95)=70.33ms p(99)=90.29ms max=189.42ms
  { api:detail }...............: avg=32.08ms med=30.84ms ... p(95)=64.79ms
http_req_failed................: 0.00%  0 out of 83349
http_reqs......................: 83349  1386.105086/s
```

| 指標 | 意思 |
|---|---|
| `http_reqs` 的 `/s` | **QPS**：每秒處理幾個請求（吞吐量） |
| `p(95)` | **95% 的請求在這個時間內完成**。比平均值（avg）可靠：平均值會被大部分很快的請求拉低，看不出少數很慢的 |
| `http_req_failed` | 錯誤率（非 2xx／3xx 的比例） |
| `{ api:list }` 等 | 依 API 分開的數字，看得出是哪一支慢 |
| 最上面的 `THRESHOLDS` | 門檻（p95 < 500ms／300ms、錯誤率 < 1%），✓ 通過、✗ 沒通過 |

---

## R00 基準數據（加快取之前）

**環境**：雲端開發機（4 vCPU、16GB），k6 與整套服務在同一台機器；正式環境設定（Nginx → 後端 → PostgreSQL 17），後端記憶體上限 768MB；測試資料 1000 個商品；暖機 30 秒後，每種併發量測 60 秒。

| 併發使用者（VUS） | QPS | p95（全部） | p95 列表 | p95 搜尋 | p95 詳情 | p99（全部） | 錯誤率 |
|---|---|---|---|---|---|---|---|
| 10 | 1,302 | 15.0 ms | 16.6 ms | 18.5 ms | 11.3 ms | 20.6 ms | 0% |
| 50 | 1,386 | 70.3 ms | 73.8 ms | 77.3 ms | 64.8 ms | 90.3 ms | 0% |
| 100 | 1,348 | 158.1 ms | 161.7 ms | 162.5 ms | 153.5 ms | 216.5 ms | 0% |

50 個使用者時各容器的 CPU（`docker stats`，4 核心 = 400%）：

| 容器 | CPU |
|---|---|
| 後端 | 153% |
| 資料庫 | 103% |
| k6（壓測工具本身） | 54% |
| Nginx | 31% |
| **合計** | **約 340%** |

### 解讀

- **QPS 在 10 個使用者時就到頂了（約 1,300～1,390）**，之後使用者再多，吞吐量不會增加，只有回應時間變長。這時系統已經飽和
- **回應時間 ≈ 併發數 ÷ QPS**（利特爾法則，Little's Law）：50 ÷ 1,386 ≈ 36 ms（實測平均 35.8 ms）、100 ÷ 1,348 ≈ 74 ms（實測 73.8 ms）。飽和之後多出來的請求只能排隊
- **瓶頸是 CPU**：整台機器的 CPU 幾乎用滿，其中後端和資料庫用最多。列表每次要查兩次資料庫（這一頁的資料＋總筆數），詳情查一次。R01 的快取讓重複的請求不用查資料庫、也少做一些物件轉換，預期 QPS 會提高、回應時間會降低
- **錯誤率 0%**，100 個使用者時 p95 仍在 160 ms 左右，三個門檻都通過
- **這些數字只能和「同一台機器、同樣步驟」的結果比較**。k6 和服務在同一台機器上互搶 CPU（k6 自己用了 54%）；正式主機（1～2 vCPU）的絕對數字會低很多
- 以實際用量來看：家庭小店的尖峰大概每秒幾個請求，1,300 QPS 綽綽有餘。這份基準的目的是**比較快取的效果**，以及練習用數據說明效能，不是因為現在不夠快

## R01 之後（加快取）

R01 完成後補上，用同樣的表格比較。

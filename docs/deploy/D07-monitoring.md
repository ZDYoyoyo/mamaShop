# D07 基本監控：健康檢查、log 輪替、外部定期檢查

目標：**網站掛掉時，你會在顧客發現之前先收到通知**，而且有足夠的資訊找出原因。

| 項目 | 做在哪裡 | 狀態 |
|---|---|---|
| 健康檢查 API | 後端（Spring Boot Actuator） | ✅ 已完成，部署（D03）後自動生效 |
| Docker 健康檢查 | `deploy/docker-compose.prod.yml` | ✅ 已完成 |
| Docker log 輪替 | `deploy/docker-compose.prod.yml` | ✅ 已完成 |
| 外部定期檢查＋通知 | UptimeRobot 等外部服務 | ⏳ 網站上線、有網域（D02、D04）後照第 3 節設定 |

---

## 1. 健康檢查

### 網址

```
GET https://你的網域/api/actuator/health
```

| 狀況 | 回應 |
|---|---|
| 正常 | `200 {"status":"UP"}` |
| 後端正常但連不到資料庫 | 約 30 秒後 `503 {"status":"DOWN"}`（等資料庫連線的逾時時間） |
| 後端沒有啟動 | Nginx 回 `502` |

- 不用登入；**只回 UP／DOWN，不回任何細節**（資料庫種類、硬碟空間都不公開）
- 其他 Actuator 端點（`env`、`heapdump` 等會洩漏設定和記憶體內容）**沒有開放**：沒登入回 401，管理員也是 404

### Docker 的健康狀態

```bash
docker compose -f deploy/docker-compose.prod.yml ps
```

STATUS 欄位會顯示：

- `(healthy)`：正常
- `(health: starting)`：剛啟動，還在等第一次檢查
- `(unhealthy)`：連續 3 次（每 30 秒一次）檢查失敗

每個服務怎麼檢查：

| 服務 | 檢查方式 |
|---|---|
| db | `pg_isready`（資料庫能不能連線） |
| backend | 呼叫 `/api/actuator/health`（也會檢查資料庫連線） |
| web | 打開首頁 |

> ⚠️ **`unhealthy` 不會自動重新啟動容器**（Docker 的 `restart` 只在程式結束時重啟）。實測：資料庫停掉時後端變 `unhealthy`，資料庫恢復後後端**自己恢復成 `healthy`**，不用重啟。如果一直 `unhealthy`，看下面「出問題時怎麼查」。

另外，web（Nginx）會**等後端 `healthy` 才啟動**：部署或重開機時，比較不會出現「伺服器暫時無法使用」。

---

## 2. Docker log 輪替

Docker 預設把每個容器的輸出（log）存成一個檔案，**而且會一直長大**，幾個月後可能把硬碟塞滿，整個網站停擺。

`deploy/docker-compose.prod.yml` 已經設定：每個容器最多 **3 個 10MB** 的 log 檔，滿了刪最舊的。也就是每個容器最多佔 30MB。

看 log 的指令：

```bash
docker compose -f deploy/docker-compose.prod.yml logs backend --tail 100
docker compose -f deploy/docker-compose.prod.yml logs -f backend
docker compose -f deploy/docker-compose.prod.yml logs backend --since 1h
```

- `--tail 100`：最後 100 行
- `-f`：持續顯示新的 log（Ctrl+C 離開）
- `--since 1h`：最近一小時

> 因為只保留最近 30MB，**出問題時要盡快看 log**，太久以前的會被刪掉。

---

## 3. 外部定期檢查（網站上線後設定）

健康檢查只是「可以被檢查」，還需要有人**定期去檢查、壞了通知你**。主機自己檢查自己沒有用（主機當機時也不會通知），要用外部的服務。

以 **UptimeRobot** 為例（免費方案：每 5 分鐘檢查一次、50 個監控項目、Email 和手機 App 通知；其他服務如 Better Stack 用法類似）：

1. 到 uptimerobot.com 註冊
2. **Add New Monitor**，建立兩個：

| 監控 | Monitor Type | URL | 設定 | 檢查什麼 |
|---|---|---|---|---|
| 網站首頁 | HTTP(s) | `https://你的網域/` | — | Nginx、前端、HTTPS 憑證 |
| 後端 | Keyword | `https://你的網域/api/actuator/health` | Keyword 填 `"UP"`，選「Alert when keyword **not** exists」 | 後端、資料庫 |

3. **Alert Contacts**：填你的 Email；手機裝 UptimeRobot App 可以收推播。建議也加一位家人
4. 測試：在主機執行 `docker compose -f deploy/docker-compose.prod.yml stop backend`，5 分鐘內應該收到通知；`start backend` 後會收到恢復通知

為什麼要兩個：首頁正常不代表後端正常（首頁是 Nginx 的靜態檔，後端掛了首頁還是打得開，但看不到商品、不能下單）。

---

## 4. 出問題時怎麼查

收到通知、或家人說網站怪怪的，依序看：

```bash
cd ~/mamashop
docker compose -f deploy/docker-compose.prod.yml ps
docker compose -f deploy/docker-compose.prod.yml logs backend --tail 100
curl -s http://localhost/api/actuator/health
df -h /
free -h
docker stats --no-stream
```

| 指令 | 看什麼 |
|---|---|
| `ps` | 哪個服務不是 `Up`、不是 `healthy` |
| `logs backend --tail 100` | 有沒有 `ERROR`、`Exception` |
| `curl .../health` | 在主機上直接問後端（排除網路、DNS、HTTPS 的問題） |
| `df -h /` | 硬碟是不是滿了（Use% 接近 100%） |
| `free -h` | 記憶體是不是用完了 |
| `docker stats --no-stream` | 每個容器用了多少 CPU、記憶體（後端上限 768MB） |

常見狀況：

| 狀況 | 處理 |
|---|---|
| backend 不是 `Up`，一直重啟 | `logs backend` 找啟動失敗的原因（常見：`.env` 設定錯、資料庫密碼不對） |
| backend `unhealthy`、log 有 `Connection refused`／`connection attempt failed` | 資料庫有問題：看 `ps` 的 db、`logs db` |
| 硬碟滿了 | `docker system df` 看誰佔空間；`docker image prune -f` 刪掉舊映像檔 |
| 記憶體不足（log 有 `OutOfMemoryError`，或容器被砍掉 exit code 137） | `docker compose ... restart backend` 先恢復；之後考慮升級主機規格 |
| 都看不出原因 | `docker compose ... restart` 全部重啟（資料不會不見），並把 log 存下來：`logs --no-color > ~/incident-$(date +%F).log` |

---

## 實際操作紀錄（請自己填）

| 項目 | 內容 |
|---|---|
| 外部監控服務 | |
| 通知對象 | |
| 測試通知的日期 | |

遇到的問題與解法：

-

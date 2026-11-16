# D05 備份與還原

前提：D03 已完成（網站在主機上運作）。

目標：**主機整台壞掉、資料被誤刪，都能把網站救回來**，最多損失一天的資料。

| 檔案 | 用途 |
|---|---|
| `deploy/scripts/backup.sh` | 每日備份：資料庫＋商品圖片，刪掉過期的，複製到主機以外的地方 |
| `deploy/scripts/verify-backup.sh` | 還原演練：把備份還原到暫時的資料庫，確認備份能用（不動正式資料） |
| `deploy/scripts/restore.sh` | 真的還原：用備份取代目前的資料（⚠️ 會蓋掉現在的資料） |
| `deploy/scripts/backup-lib.sh` | 三個腳本共用的設定，不直接執行 |

## 備份了什麼、沒備份什麼

| 資料 | 有沒有備份 | 原因 |
|---|---|---|
| 資料庫（會員、商品、訂單、付款與物流紀錄） | ✅ | 最重要，不能重來 |
| 商品圖片 | ✅ | 重新拍照上傳很花時間 |
| `deploy/.env`（密碼、金鑰） | ❌ **請自己存到密碼管理器** | 備份檔會複製到別的地方，不適合放機密。換新主機時需要它 |
| HTTPS 憑證 | ❌ | 重新申請就有（D04） |
| RabbitMQ（還沒寄的信、排定的自動完成／逾時取消） | ❌ | 見下面的說明 |
| Redis（商品快取） | ❌ | 只是快取，會自己重建 |

> ⚠️ **還原之後，「已經排定」的自動處理不會發生**：宅配送達 7 天自動完成、線上付款逾時自動取消，是放在 RabbitMQ 裡等時間到的訊息，不在資料庫的備份裡。
> 換新主機還原後，原本「已送達」「待付款」的訂單不會自動處理，要家人到後台手動完成或取消。還原之後才發生的不受影響。

**備份檔裡有顧客的姓名、電話、地址**：腳本把備份資料夾設成只有 `deploy` 自己能讀；複製到主機以外的地方時也要注意存放的位置誰看得到。

以下指令都在**主機**上，用 `deploy` 帳號執行。

---

## 1. 手動備份一次

```bash
bash ~/mamashop/deploy/scripts/backup.sh
```

輸出大概是：

```
2026-11-16 03:30:01 開始備份（/home/deploy/backups/mamashop）
2026-11-16 03:30:02 資料庫：24K，8 張表
2026-11-16 03:30:02 商品圖片：4.0K，1 個檔案
2026-11-16 03:30:02 ⚠️ 沒有設定 BACKUP_RCLONE_REMOTE：備份只存在這台主機上
2026-11-16 03:30:02 備份完成
```

- 網站不用停：`pg_dump` 匯出的是「某一瞬間」一致的資料，備份時顧客可以照常下單
- 腳本每一步在做什麼，寫在檔案裡的註解

看備份檔：

```bash
ls -lh ~/backups/mamashop/
```

- `db-日期-時間.dump`：資料庫；`uploads-日期-時間.tar.gz`：商品圖片
- 權限是 `-rw-------`（只有自己能讀寫）

---

## 2. 還原演練：確認備份真的能用

**沒有還原過的備份不算備份**——檔案在，不代表還原得回來。

```bash
bash ~/mamashop/deploy/scripts/verify-backup.sh
```

- 把最新的備份還原到一個暫時的資料庫（`mamashop_restore_test`），比對完就刪掉。**不會動到正式的資料**，網站不用停

輸出：

```
資料表                      備份     現在
users                               2          2
categories                          1          1
products                            1          1
orders                              2          3
...
備份裡最新的訂單：2026-11-16 16:42
商品圖片：備份 1 個檔案，現在 1 個
還原演練完成：備份可以還原（暫時的資料庫已刪除）
```

怎麼看：
- 「備份」和「現在」差不多：正常。備份之後才新增的資料，「現在」會比較多
- 「備份」是 0、或比「現在」少很多：備份有問題，不要等到出事才發現
- 「備份裡最新的訂單」是很久以前：排程可能沒有在跑（看第 3 節的 log）
- 出現 `pg_restore: error`：備份檔壞了

**建議每個月跑一次**，並在下面的紀錄表寫上日期。

---

## 3. 每天自動備份

```bash
crontab -e
```

在最後加一行（整行，不要換行）：

```
30 3 * * * /bin/bash /home/deploy/mamashop/deploy/scripts/backup.sh >> /home/deploy/backup.log 2>&1
```

- `30 3 * * *`：每天 03:30（五個欄位是 分、時、日、月、星期）。選半夜訂單最少的時候
- `>> /home/deploy/backup.log 2>&1`：輸出和錯誤訊息都附加到 log 檔
- 和 D04 的憑證續期（04:17）錯開

確認排程：

```bash
crontab -l
```

隔天看有沒有執行：

```bash
tail -20 ~/backup.log
ls -lh ~/backups/mamashop/ | tail -5
```

- log 最後一行是「備份完成」、資料夾裡有今天日期的檔案才算成功
- 任何一步失敗腳本都會停下來，**不會刪舊的備份**（所以連續失敗時舊備份會一直留著），log 裡看得到錯誤訊息

預設保留 14 天。要改的話在 `deploy/.env` 加一行，例如 `BACKUP_KEEP_DAYS=30`。

用了多少空間：

```bash
du -sh ~/backups/mamashop/
df -h /
```

- `du -sh`：這個資料夾總共多大；`df -h /`：硬碟還剩多少。圖片多了之後留意一下（每天一份完整的圖片備份）

---

## 4. 複製一份到主機以外的地方

備份和網站在**同一台主機**的話：主機壞掉、硬碟壞掉、帳號被停、被入侵刪檔，備份會跟著一起不見。**一定要有一份在別的地方。**

腳本用 [rclone](https://rclone.org/)（一個「把檔案同步到各種雲端空間」的工具，支援 Google Drive、Backblaze B2、Cloudflare R2、另一台主機等等）。

安裝：

```bash
sudo apt-get install -y rclone
```

設定一個遠端（互動式，照畫面選擇你要用的服務；名稱取 `offsite`）：

```bash
rclone config
```

- 選 `n`（新的 remote）→ name 輸入 `offsite` → 選服務 → 照指示登入或填金鑰
- 各種服務的設定步驟：<https://rclone.org/docs/>（左邊選服務）
- 設定存在 `~/.config/rclone/rclone.conf`，裡面有存取雲端的憑證，權限已經是只有自己能讀

測試連得上（列出遠端的資料夾）：

```bash
rclone lsd offsite:
```

在 `deploy/.env` 加上（`offsite` 是剛才取的名稱，冒號後面是遠端的資料夾）：

```
BACKUP_RCLONE_REMOTE=offsite:mamashop-backup
```

再跑一次備份，最後應該出現「已複製到 offsite:mamashop-backup」：

```bash
bash ~/mamashop/deploy/scripts/backup.sh
rclone ls offsite:mamashop-backup
```

- 遠端預設保留 30 天（`BACKUP_REMOTE_KEEP_DAYS`），比主機上的 14 天久
- 腳本只會「上傳」和「刪除超過保留天數的」，不會因為主機上的備份不見就把遠端的也刪掉

> 🔒 **備份裡有顧客個資**。雲端空間請用只有你能存取的帳號、開兩步驟驗證。
> 想更保險可以用 rclone 的加密功能（`crypt` 類型的 remote：上傳前先加密，雲端業者也看不到內容）；密碼要另外保存，遺失就解不開。

> 不想用雲端：也可以從家裡的電腦定期把備份抓回來，例如 Windows 的 cmd：
> `scp -r deploy@主機IP:backups/mamashop C:\mamashop-backup`
> 缺點是要記得做。

---

## 5. 還原單一資料（誤刪了某筆資料）

不需要整個還原。把備份還原到暫時的資料庫，查出需要的資料再手動補回去：

```bash
cd ~/mamashop
docker compose -f deploy/docker-compose.prod.yml exec -T db createdb -U mamashop mamashop_lookup
docker compose -f deploy/docker-compose.prod.yml exec -T db pg_restore -U mamashop -d mamashop_lookup --no-owner < ~/backups/mamashop/db-20261116-033000.dump
docker compose -f deploy/docker-compose.prod.yml exec db psql -U mamashop -d mamashop_lookup
```

- 第一行：建立一個空的資料庫 `mamashop_lookup`
- 第二行：把備份還原進去（`<` 把主機上的檔案傳給容器裡的 `pg_restore`）
- 第三行：進去下 SQL 查資料（`\q` 離開）

用完刪掉：

```bash
docker compose -f deploy/docker-compose.prod.yml exec -T db dropdb -U mamashop mamashop_lookup
```

---

## 6. 整個還原（主機壞了換新的、或要回到某一天）

⚠️ `restore.sh` 會**用備份取代目前的資料**，備份之後才產生的訂單、會員都會消失。

### 換一台新主機

1. 照 D01 設定主機、D03 第 1～2 步安裝 Docker 並下載程式碼
2. 把密碼管理器裡的 `deploy/.env` 內容放回 `~/mamashop/deploy/.env`（`chmod 600`）。**`TLS_MODE` 先改成 `http`**（新主機還沒有憑證）
3. 照 D03 第 4 步建置並啟動（這時是一個空的網站）
4. 把備份檔放到新主機（從雲端抓回來：`rclone copy offsite:mamashop-backup ~/backups/mamashop`）
5. 還原：

```bash
bash ~/mamashop/deploy/scripts/restore.sh ~/backups/mamashop/db-20261116-033000.dump ~/backups/mamashop/uploads-20261116-033000.tar.gz
```

6. DNS 改指到新主機的 IP，照 D04 重新申請憑證、切回 HTTPS
7. 到後台看「已送達」「待付款」的訂單：原本排定的自動完成／自動取消不會發生（見最上面的說明），手動處理

### 腳本做了什麼

1. 要你輸入資料庫名稱確認（打錯就取消，不做任何事）
2. 把「現在的資料」先另存一份（`before-restore-時間.dump`）：萬一還原錯檔案，還有後悔的機會
3. 停掉後端（還原時不能有人下單）
4. 還原資料庫。整個過程包在一個交易裡：中途失敗就全部復原，不會停在刪了一半的狀態
5. 清掉商品快取（裡面是還原前的商品資料）
6. 啟動後端，等它健康檢查通過。備份如果是舊版程式的，後端啟動時會自動把資料表升級到新版（Flyway）
7. 還原商品圖片

還原後打開網站，確認商品、圖片、訂單、登入都正常。

---

## 常見問題

| 狀況 | 可能的原因 | 檢查 |
|---|---|---|
| `找不到 .../deploy/.env` | 不在主機上，或 `.env` 還沒建立 | D03 第 3 步 |
| `service "db" is not running` | 服務沒啟動 | `docker compose -f deploy/docker-compose.prod.yml ps` |
| cron 沒有執行 | 排程那一行有錯、路徑不對 | `crontab -l`；`grep CRON /var/log/syslog \| tail` 看 cron 有沒有啟動它 |
| 備份成功但沒有「已複製到…」 | `deploy/.env` 沒有 `BACKUP_RCLONE_REMOTE` | 第 4 節 |
| `rclone: command not found`（只在 cron 裡發生） | cron 的 PATH 比較短 | `which rclone` 看路徑；通常在 `/usr/bin`，cron 找得到。裝在別處的話在 crontab 最上面加 `PATH=/usr/local/bin:/usr/bin:/bin` |
| 硬碟滿了 | 備份太多、圖片太大 | `du -sh ~/backups/mamashop/`；調小 `BACKUP_KEEP_DAYS` |
| 還原後前台商品不對 | 快取 | 腳本已經會清；手動清：`docker compose -f deploy/docker-compose.prod.yml exec redis redis-cli FLUSHALL` |
| 還原後後端起不來 | 備份比程式「新」（資料表版本比程式認得的高） | `logs backend --tail 50`；把程式更新到備份當時或更新的版本 |

---

## 實際操作紀錄（請自己填）

| 項目 | 內容 |
|---|---|
| 第一次備份的日期 | |
| 設定 cron 的日期 | |
| 主機以外的備份放在哪裡 | |
| `deploy/.env` 已存到密碼管理器 | |

還原演練紀錄（建議每月一次）：

| 日期 | 用哪一份備份 | 結果 |
|---|---|---|
| | | |

遇到的問題與解法：

-

# D04 HTTPS：Let's Encrypt 憑證與自動續期

前提：D03 已完成（網站用 `http://主機IP` 打得開）、D02 已完成（網域的 DNS 已經指向主機）。

完成後的樣子：

```
使用者 ──443（HTTPS）──▶ web（Nginx）── 網站（和 D03 一樣）
使用者 ──80（HTTP）────▶ web（Nginx）── 301 轉址到 https://網域
                                      └─ /.well-known/acme-challenge/  Let's Encrypt 驗證網域用（唯一不轉址的路徑）

certbot（需要時才啟動的容器）── 申請／續期憑證 ──▶ letsencrypt volume ──▶ web 唯讀掛載
cron 每天執行 deploy/scripts/renew-cert.sh：快到期才續期，然後叫 Nginx 重新讀取憑證
```

| 檔案 | 用途 |
|---|---|
| `deploy/nginx/https.conf.template` | HTTPS 的 Nginx 設定（`TLS_MODE=https` 時使用） |
| `deploy/nginx/default.conf` | 只有 HTTP 的設定（預設） |
| `deploy/nginx/site.conf` | 網站本體，兩種模式共用 |
| `deploy/nginx/acme-and-health.conf` | 網域驗證與健康檢查的路徑，兩種模式的 80 port 共用 |
| `deploy/docker-compose.prod.yml` | `certbot` 服務、`letsencrypt`／`certbot-www` 兩個 volume、443 port |
| `deploy/scripts/renew-cert.sh` | 續期腳本 |

**為什麼要 HTTPS**：沒有的話，顧客的密碼、登入憑證、地址電話在網路上是明文，同一個 Wi-Fi 的人都看得到；瀏覽器也會顯示「不安全」。綠界的付款與物流通知也需要外面連得到的網址（P01～P03）。

**Let's Encrypt 怎麼確認網域是你的（HTTP-01 驗證）**：certbot 在主機上放一個檔案，Let's Encrypt 的伺服器從外面用 `http://你的網域/.well-known/acme-challenge/檔名` 來讀。讀得到，就代表「控制這個網域指向的主機的人」是你，於是簽發憑證。所以 **DNS 要先指到這台主機、80 port 要打得開**。

以下指令都在**主機**上，用 `deploy` 帳號執行。把 `shop.example.com` 換成你的網域。

---

## 1. 確認 DNS 與防火牆

在**自己的電腦**（Windows cmd）：

```bash
nslookup shop.example.com
```

- 回應的 Address 要是主機的 IP。DNS 剛設定完可能要等幾分鐘到幾小時才生效；還沒生效就申請會失敗

在主機上：

```bash
sudo ufw status
```

- 要有 `80/tcp` 和 `443/tcp` 都是 `ALLOW`（D01 已經開了）。雲端廠商自己的防火牆也要開 443

用手機（不要連主機同一個網路）打開 `http://shop.example.com`，看得到網站才繼續。

---

## 2. 更新程式、重新建置 web

```bash
cd ~/mamashop
git pull
docker compose -f deploy/docker-compose.prod.yml build web
docker compose -f deploy/docker-compose.prod.yml up -d
```

- 這一版的 web 多了「網域驗證」的路徑和 443 port。此時 `deploy/.env` 還沒有 `TLS_MODE`，所以仍然是 HTTP 模式，網站照常運作

確認驗證路徑通了（應該回 `404`，而不是網站的首頁內容）：

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://shop.example.com/.well-known/acme-challenge/test
```

---

## 3. 先用「測試環境」申請一次（不算次數）

Let's Encrypt 正式環境有次數限制（同一個網域每週 5 張重複的憑證、連續失敗每小時 5 次）。先用 `--dry-run` 走一遍流程，確定會成功：

```bash
docker compose -f deploy/docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot -d shop.example.com --email 你的信箱 --agree-tos --no-eff-email --dry-run
```

- `run --rm certbot`：啟動一個暫時的 certbot 容器，跑完就刪掉
- `certonly`：只申請憑證，不要自動改網頁伺服器的設定（Nginx 的設定我們自己管）
- `--webroot -w /var/www/certbot`：驗證方式——把驗證檔放進這個資料夾（和 web 共用的 volume），由正在運作的 Nginx 提供。**不用停網站**
- `-d shop.example.com`：要申請的網域。有 `www.` 也要用的話再加一個 `-d www.shop.example.com`（DNS 也要設定）
- `--email`：憑證快到期、或有安全問題時，Let's Encrypt 會寄信到這裡
- `--agree-tos`：同意服務條款；`--no-eff-email`：不訂閱 EFF 的電子報
- `--dry-run`：用測試環境演練，不會真的發憑證

看到 `The dry run was successful.` 才繼續。失敗的話看最下面的「常見問題」。

---

## 4. 正式申請

同一個指令，拿掉 `--dry-run`：

```bash
docker compose -f deploy/docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot -d shop.example.com --email 你的信箱 --agree-tos --no-eff-email
```

看到 `Successfully received certificate.` 就成功了。憑證存在 `letsencrypt` 這個 Docker volume 裡：

```bash
docker compose -f deploy/docker-compose.prod.yml run --rm certbot certificates
```

- 會列出網域（Domains）與到期日（Expiry Date，90 天後）

---

## 5. 切換成 HTTPS 模式

```bash
nano deploy/.env
```

改三個值：

```
TLS_MODE=https
DOMAIN=shop.example.com
SITE_URL=https://shop.example.com
```

- `TLS_MODE=https`：Nginx 改用 HTTPS 的設定
- `DOMAIN`：只寫網域，不要 `https://`、不要結尾的 `/`
- `SITE_URL`：**一定要一起改成 https**。通知信裡的連結、綠界付款後的返回網址、後端允許的來源（CORS）都用這個值；沒改的話登入等 API 會被擋（CORS）

套用：

```bash
docker compose -f deploy/docker-compose.prod.yml up -d
docker compose -f deploy/docker-compose.prod.yml ps
```

- `up -d`：Compose 發現環境變數變了，會重新建立 web 和 backend
- `ps`：web 要是 `Up (healthy)`，而且有 `0.0.0.0:443->443/tcp`

> ⚠️ web 一直 `Restarting`：看 `docker compose -f deploy/docker-compose.prod.yml logs web --tail 20`。
> 出現 `cannot load certificate`：憑證不存在，通常是 `DOMAIN` 和申請時的網域不一樣（大小寫、有沒有 `www.`），或第 4 步沒成功。
> 急著恢復網站：把 `TLS_MODE` 改回 `http` 再 `up -d`。

---

## 6. 檢查

在主機或自己的電腦：

```bash
curl -I http://shop.example.com/
curl -I https://shop.example.com/
```

- 第一個：`HTTP/1.1 301 Moved Permanently`，`Location: https://shop.example.com/`
- 第二個：`HTTP/2 200`，有 `strict-transport-security: max-age=86400`

用手機打開 `http://shop.example.com`：自動變成 `https://`、網址列有鎖頭。登入、把商品加入購物車、下一筆測試訂單，確認信裡的連結是 `https://`。

想知道設定的安全等級：到 <https://www.ssllabs.com/ssltest/> 輸入網域（大約等 2 分鐘），應該是 A。

---

## 7. 自動續期

憑證 90 天到期。先手動跑一次續期腳本，確認它能執行：

```bash
bash ~/mamashop/deploy/scripts/renew-cert.sh
```

- 剛申請完不需要續期，所以只會重新載入 Nginx，最後印出憑證的到期日
- 腳本做的事寫在檔案開頭的註解裡

演練一次「真的續期」（測試環境，不算次數）：

```bash
docker compose -f deploy/docker-compose.prod.yml run --rm certbot renew --dry-run
```

- 看到 `Congratulations, all simulated renewals succeeded` 代表之後自動續期會成功

設定每天自動執行：

```bash
crontab -e
```

- `crontab -e`：編輯「目前這個使用者（deploy）」的排程表。第一次會問要用哪個編輯器，選 nano

在最後加一行（整行，不要換行）：

```
17 4 * * * /bin/bash /home/deploy/mamashop/deploy/scripts/renew-cert.sh >> /home/deploy/renew-cert.log 2>&1
```

- `17 4 * * *`：每天 04:17（五個欄位是 分、時、日、月、星期；`*` 代表每一個）。選半夜、不是整點，避開大家都在整點連 Let's Encrypt
- `>> /home/deploy/renew-cert.log`：把輸出附加到 log 檔；`2>&1`：錯誤訊息也一起記
- 為什麼每天跑：腳本裡的 `certbot renew` 只有「剩不到 30 天」才會真的續期。每天檢查，就算某幾天失敗（網路、Let's Encrypt 維護），還有很多次機會

存檔後確認：

```bash
crontab -l
```

隔天看有沒有執行：

```bash
tail -20 ~/renew-cert.log
```

> 外部監控（D07）可以加一項「憑證到期提醒」：UptimeRobot 等服務會在憑證剩 N 天時通知。這是續期失敗時的最後一道保險。

---

## 8. 穩定之後：把 HSTS 拉長

HSTS 是告訴瀏覽器「這個網站之後一律用 HTTPS」，連使用者手動打 `http://` 都會被瀏覽器自己改成 `https://`。目前設定 1 天（`max-age=86400`）：**瀏覽器記住之後，這段時間內如果 HTTPS 壞了（例如憑證過期），網站就打不開**，它不肯退回 HTTP。所以剛開始用短的。

穩定運作一兩週、確認自動續期有在跑之後，把 `deploy/nginx/default.conf` 和 `deploy/nginx/https.conf.template` 裡的 `max-age=86400` 改成 `max-age=31536000`（1 年），commit、push，再照 D03 第 6 節更新主機。

---

## 常見問題

| 狀況 | 可能的原因 | 檢查 |
|---|---|---|
| 申請時 `Timeout during connect` | 80 port 從外面連不到 | `sudo ufw status`、雲端廠商的防火牆、`docker compose ... ps` 看 web 有沒有在跑 |
| 申請時 `DNS problem: NXDOMAIN` 或 IP 不對 | DNS 還沒生效，或指到別的 IP | `nslookup 網域`；等一下再試 |
| 申請時 `Invalid response ... 404` | 驗證檔讀不到 | 第 2 步有沒有做（要用新版的 web）；`-w /var/www/certbot` 有沒有打錯 |
| `too many certificates` / `too many failed authorizations` | 超過次數限制 | 等一小時（失敗次數）或一週（重複憑證）。之後先用 `--dry-run` |
| web `Restarting`，log 有 `cannot load certificate` | 憑證檔不存在 | `certbot certificates` 看網域名稱；`deploy/.env` 的 `DOMAIN` 要完全一樣 |
| 改成 HTTPS 後登入失敗、畫面說無法連線 | `SITE_URL` 還是 `http://` | 改成 `https://網域` 後 `up -d` |
| 瀏覽器說憑證無效 | 用 IP 或別的網域連（憑證只對申請的網域有效） | 用網域連 |
| 用 `https://主機IP` 連不上 | 正常：用 IP 連 443 會直接被拒絕 | 用網域連 |

> ⚠️ **不要 `down -v`**（D03 提過）：除了資料庫和商品圖片，憑證也在 volume 裡，刪掉要重新申請。

---

## 實際操作紀錄（請自己填）

| 項目 | 內容 |
|---|---|
| 完成日期 | |
| 網域 | |
| 憑證到期日（第一次） | |
| SSL Labs 評等 | |
| HSTS 改成 1 年的日期 | |

遇到的問題與解法：

-

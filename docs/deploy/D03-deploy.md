# D03 正式環境部署：Docker Compose（Nginx＋後端＋PostgreSQL）

前提：D01 已完成（主機只能用金鑰登入、ufw 只開 22／80／443）。D02（網域）還沒好也可以先做，用主機 IP 連線。

完成後的樣子：

```
使用者 ──80──▶ web（Nginx）──┬── /            前端網頁（Vue 建置好的靜態檔）
                              ├── /uploads/    商品圖片（直接讀 volume）
                              └── /api/ ──▶ backend（Spring Boot）──▶ db（PostgreSQL）
                                               ↑ 這兩個沒有對外的 port，只有 Docker 內部網路連得到
```

| 檔案 | 用途 |
|---|---|
| `deploy/docker-compose.prod.yml` | 三個服務的設定 |
| `deploy/nginx/Dockerfile` | 建置前端＋Nginx 映像檔 |
| `deploy/nginx/default.conf` | 路由、快取、上傳大小、轉發給後端 |
| `deploy/nginx/security-headers.conf` | CSP 等安全 header |
| `deploy/.env.example` | 設定範本；在主機上複製成 `deploy/.env`（不進版控） |
| `.dockerignore`（根目錄） | 建置前端時只把 `frontend/`、`deploy/nginx/` 送進 Docker |

以下指令都在**主機**上，用 `deploy` 帳號執行。

---

## 1. 安裝 Docker

Ubuntu 內建的 `docker.io` 版本比較舊，用 Docker 官方的套件來源：

```bash
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

- 前四行：下載 Docker 的 GPG 公鑰。apt 用它驗證下載的套件真的是 Docker 官方發布的，沒有被竄改
- `echo "deb ..." | sudo tee ...`：新增 Docker 的套件來源。`$(dpkg --print-architecture)` 是 CPU 架構（例如 amd64），`$VERSION_CODENAME` 是 Ubuntu 版本代號（24.04 是 noble）
- 最後一行：安裝 Docker 本體與 Compose（`docker compose` 指令）

讓 deploy 不用每次都打 `sudo docker`：

```bash
sudo usermod -aG docker deploy
exit
```

重新 SSH 登入後才會生效。確認：

```bash
docker run --rm hello-world
```

> ⚠️ `docker` 群組等同 root：能用 docker 的人可以掛載整個主機的檔案系統。只把自己的帳號加進去。

---

## 2. 把程式碼放到主機

如果 repo 是**私人的**，主機需要權限才能下載。用 **Deploy key**（只能讀這一個 repo 的金鑰，比放自己的 GitHub 帳號金鑰安全）：

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C "mamashop-server"
cat ~/.ssh/github_deploy.pub
```

- `-f ~/.ssh/github_deploy`：存成另一個檔名，和登入主機用的金鑰分開
- `-N ""`：不設密碼（主機要能自動拉程式碼）。這把金鑰只能**讀**這個 repo，外洩的影響有限

到 GitHub 的 repo → Settings → Deploy keys → Add deploy key，貼上公鑰，**不要勾** Allow write access。

告訴 SSH 連 GitHub 時用這把金鑰：

```bash
cat >> ~/.ssh/config <<'EOF'
Host github.com
    IdentityFile ~/.ssh/github_deploy
EOF
chmod 600 ~/.ssh/config
```

下載程式碼：

```bash
git clone git@github.com:ZDYoyoyo/mamaShop.git ~/mamashop
cd ~/mamashop
```

（公開的 repo 直接 `git clone https://github.com/ZDYoyoyo/mamaShop.git ~/mamashop`，不用 deploy key。）

---

## 3. 建立正式環境的設定檔

```bash
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
```

- `chmod 600`：只有 deploy 自己能讀寫。這個檔案有資料庫密碼和 JWT 密鑰

產生密碼與密鑰（每個指令會印出一串隨機字元，複製到 `deploy/.env` 對應的位置）：

```bash
openssl rand -hex 24
openssl rand -base64 48
openssl rand -base64 18
```

- 第一個給 `DB_PASSWORD`，第二個給 `JWT_SECRET`，第三個給 `ADMIN_PASSWORD`
- `openssl rand`：用密碼學安全的亂數產生器。`-hex` 只有 0-9a-f（放進資料庫連線字串不會有特殊字元問題）

編輯：

```bash
nano deploy/.env
```

至少要填：`SITE_URL`（先填 `http://主機IP`）、`DB_PASSWORD`、`JWT_SECRET`、`ADMIN_EMAIL`、`ADMIN_PASSWORD`。存檔：Ctrl+O、Enter；離開：Ctrl+X。

> 這些值**只存在主機上**。把 `ADMIN_PASSWORD` 記在密碼管理器，第一次登入後台要用。

---

## 4. 建置並啟動

```bash
docker compose -f deploy/docker-compose.prod.yml build backend
docker compose -f deploy/docker-compose.prod.yml build web
docker compose -f deploy/docker-compose.prod.yml up -d
```

- `-f deploy/docker-compose.prod.yml`：用正式環境的設定（不是根目錄給本機開發用的 `docker-compose.yml`）。Compose 會讀同一個資料夾的 `deploy/.env`
- 分兩次 `build`：後端（Maven）和前端（npm）同時建置很吃記憶體，2GB 的主機可能不夠。第一次要下載很多依賴，大約 5～10 分鐘
- `up -d`：在背景（detached）啟動所有服務。順序由 `depends_on` 決定：資料庫 healthcheck 通過 → 後端 → Nginx

看狀態：

```bash
docker compose -f deploy/docker-compose.prod.yml ps
```

三個都是 `Up (healthy)`（剛啟動時會是 `health: starting`，等一分鐘；健康檢查見 D07），**只有 web 有 `0.0.0.0:80->80/tcp`**。

看後端啟動 log：

```bash
docker compose -f deploy/docker-compose.prod.yml logs backend --tail 50
```

看到 `Started MamashopApplication` 和 `已建立預設管理員` 就成功了。

> 每次都打 `-f deploy/docker-compose.prod.yml` 很長，可以設定別名：`echo "alias dc='docker compose -f ~/mamashop/deploy/docker-compose.prod.yml'" >> ~/.bashrc && source ~/.bashrc`，之後用 `dc ps`、`dc logs backend`。

---

## 5. 檢查

在主機上：

```bash
curl -I http://localhost/
curl http://localhost/api/categories
sudo bash ~/check-server.sh
```

- 第一個：`HTTP/1.1 200 OK`，有 `Content-Security-Policy` 等 header
- 第二個：`{"success":true,"data":[]}`（正式環境沒有測試資料，所以是空的）
- 也可以看後端健康檢查：`curl http://localhost/api/actuator/health` → `{"status":"UP"}`
- 第三個（D01 的檢查腳本）：「對外監聽的只有：22 80」。如果出現 5432 或 8080，代表設定被改過，資料庫或後端對外開放了

用手機打開 `http://主機IP`：

1. 首頁出現（還沒有商品）
2. `http://主機IP/admin` → 用 `ADMIN_EMAIL`／`ADMIN_PASSWORD` 登入
3. 後台建立分類、商品（含圖片）、上架
4. 回前台看得到商品和圖片；註冊一個測試帳號下單；後台確認訂單

---

## 6. 更新程式（之後每次改版）

```bash
cd ~/mamashop
git pull
docker compose -f deploy/docker-compose.prod.yml build backend
docker compose -f deploy/docker-compose.prod.yml build web
docker compose -f deploy/docker-compose.prod.yml up -d
docker image prune -f
```

- `git pull`：下載最新的程式碼
- `build`：只有改到的部分會重新建置（Docker 快取）
- `up -d`：只重建映像檔有變的容器。後端重啟大約 10～20 秒，這段時間網頁會顯示「伺服器暫時無法使用」（F08），之後自動恢復
- `docker image prune -f`：刪掉舊版本的映像檔，不然硬碟會越用越少
- 資料庫的資料和商品圖片在 volume 裡，**重建容器不會消失**。資料表的變更由 Flyway 在後端啟動時自動執行

---

## 常用指令

| 要做什麼 | 指令（前面都要加 `docker compose -f deploy/docker-compose.prod.yml`） |
|---|---|
| 看狀態 | `ps` |
| 看 log（持續追蹤，Ctrl+C 離開） | `logs -f backend` |
| 重新啟動後端 | `restart backend` |
| 進資料庫下 SQL | `exec db psql -U mamashop -d mamashop` |
| 全部停止（資料保留） | `down` |

> ⚠️ **不要執行 `down -v`**：`-v` 會刪除 volume，也就是**資料庫和所有商品圖片**。

## 常見問題

| 狀況 | 可能的原因 | 檢查 |
|---|---|---|
| `up` 時出現「請先建立 deploy/.env」 | 沒有 `deploy/.env`，或少了必填的值 | `ls -l deploy/.env`；對照 `.env.example` |
| 網頁打不開 | web 沒啟動、ufw 沒開 80、廠商防火牆沒開 80 | `ps`、`sudo ufw status`、`curl -I http://localhost/` |
| 網頁出現但「伺服器暫時無法使用」 | 後端沒啟動或啟動失敗（Nginx 回 502） | `logs backend --tail 100` |
| 後端 log：`password authentication failed` | 第一次啟動後又改了 `DB_PASSWORD`。PostgreSQL 只在**第一次建立資料庫時**設定密碼 | 改回原本的密碼；或進資料庫 `ALTER USER mamashop PASSWORD '新密碼';` |
| 建置到一半被砍掉（`Killed`、exit code 137） | 記憶體不足 | 確認有 swap（D01 第 8 步）；一次只建置一個服務 |
| 上傳圖片失敗 | 圖片超過 10MB | 後台會先壓縮（A02），通常不會發生 |

---

## 實際操作紀錄（請自己填）

| 項目 | 內容 |
|---|---|
| 完成日期 | |
| 第一次建置花了多久 | |
| `check-server.sh` 結果 | |

遇到的問題與解法：

-

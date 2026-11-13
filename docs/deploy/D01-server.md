# D01 主機：租 VPS、SSH 金鑰登入、防火牆

目標：一台 Ubuntu 24.04 主機，

- 只能用 **SSH 金鑰**登入（關閉密碼登入、禁止 root 直接登入）
- 防火牆只開 **22（SSH）、80（HTTP）、443（HTTPS）**
- 自動安裝安全性更新、有 swap

做完後用 `deploy/scripts/check-server.sh` 檢查，全部 PASS 才算完成。

> 以下的 `<主機IP>` 換成你的主機 IP。標示「**Windows cmd**」的在你的電腦上執行，標示「**主機**」的在 SSH 連進去之後執行。
> 遇到的每個問題請記在最後的「實際操作紀錄」，面試時就是真實的排查經驗。

---

## 0. 選主機

| 項目 | 建議 | 理由 |
|---|---|---|
| 作業系統 | **Ubuntu 24.04 LTS** | 長期支援到 2029 年；本文的指令都以它為準 |
| 記憶體 | **2GB**（最少 1GB＋swap） | Spring Boot（JVM）約 300～500MB、PostgreSQL 約 100MB、Nginx 很小；1GB 也跑得動，但更新、建置時容易不夠 |
| CPU／硬碟 | 1 vCPU、25GB 以上 SSD | 小專案足夠；硬碟主要給資料庫、商品圖片、備份 |
| 地區 | 台灣、東京或新加坡 | 離顧客近，網頁比較快 |
| 費用 | 約每月 US$6～12 | Vultr、Linode（Akamai）、DigitalOcean 等都有這個價位；GCP、AWS 也可以，但設定比較多 |

建立主機時，大部分廠商可以直接貼上 **SSH 公鑰**（下一步產生），請直接貼上，第一次就能用金鑰登入。

---

## 1. 在你的電腦產生 SSH 金鑰（Windows cmd）

Windows 10／11 內建 OpenSSH，cmd 就能用。

```bash
ssh-keygen -t ed25519 -C "mamashop"
```

- `ssh-keygen`：產生一對金鑰。**私鑰**留在你的電腦（絕對不能給別人），**公鑰**放到主機上
- `-t ed25519`：金鑰類型。比傳統的 RSA 短、快、安全
- `-C "mamashop"`：註解，方便以後認出這把金鑰是做什麼的
- 問存放位置時直接按 Enter（存到 `C:\Users\你的帳號\.ssh\id_ed25519`）
- 問 passphrase 時**建議設定**：電腦被偷時，別人拿到私鑰也不能直接用

產生兩個檔案：`id_ed25519`（私鑰）、`id_ed25519.pub`（公鑰）。顯示公鑰，複製整行貼到主機廠商的設定頁：

```bash
type %USERPROFILE%\.ssh\id_ed25519.pub
```

---

## 2. 第一次登入（Windows cmd）

```bash
ssh root@<主機IP>
```

- 第一次連線會問「Are you sure you want to continue connecting」，輸入 `yes`：記住這台主機的指紋，之後如果主機被冒充，SSH 會警告
- 有些廠商（例如 AWS）預設帳號不是 `root` 而是 `ubuntu`，請看廠商說明

---

## 3. 更新系統（主機）

```bash
apt update && apt upgrade -y
```

- `apt update`：下載最新的套件清單（只是清單，還沒安裝）
- `apt upgrade -y`：把已安裝的套件更新到最新版；`-y` 自動回答 yes
- 如果更新了核心，最後會提示需要重新開機：`reboot`，等一分鐘再連回來

---

## 4. 建立一般帳號 deploy（主機）

平常不用 root：root 打錯一個指令就可能毀掉整台主機，用一般帳號、需要時才 `sudo`。

```bash
adduser deploy
usermod -aG sudo deploy
rsync --archive --chown=deploy:deploy ~/.ssh /home/deploy
```

- `adduser deploy`：建立帳號，會要你設定密碼（**之後 `sudo` 時要輸入**，請記好；SSH 登入不會用到它）
- `usermod -aG sudo deploy`：把 deploy 加進 `sudo` 群組，可以用 `sudo` 執行管理指令。`-a` 是「附加」，少了它會把其他群組清掉
- `rsync ... ~/.ssh /home/deploy`：把 root 的 `.ssh`（裡面有你的公鑰 `authorized_keys`）複製給 deploy，並把擁有者改成 deploy。這樣 deploy 就能用同一把金鑰登入

**另開一個 cmd 視窗**（舊的不要關），確認 deploy 登得進去：

```bash
ssh deploy@<主機IP>
sudo whoami
```

`sudo whoami` 輸入 deploy 的密碼後顯示 `root`，代表 sudo 正常。**確認成功才繼續下一步**，否則下一步關掉 root 登入後就進不來了。

---

## 5. 只允許金鑰登入、禁止 root 登入（主機）

把專案裡的 `deploy/server/sshd-hardening.conf` 複製到主機。在你的電腦（專案資料夾）執行（**Windows cmd**）：

```bash
scp deploy\server\sshd-hardening.conf deploy\scripts\check-server.sh deploy@<主機IP>:~
```

- `scp`：用 SSH 複製檔案，`~` 是 deploy 的家目錄（`/home/deploy`）

回到主機（用 deploy 登入的那個視窗）：

```bash
sudo cp ~/sshd-hardening.conf /etc/ssh/sshd_config.d/01-mamashop-hardening.conf
sudo sshd -t
sudo systemctl restart ssh
```

- 放到 `/etc/ssh/sshd_config.d/`：主設定檔 `/etc/ssh/sshd_config` 第一行會載入這個資料夾的所有 `.conf`。不改主設定檔，之後系統更新時比較不會衝突
- ⚠️ **檔名一定要 `01-` 開頭**：sshd 對同一個設定「先讀到的值為準」，資料夾裡的檔案依檔名順序讀。雲端主機常有 `50-cloud-init.conf` 寫著 `PasswordAuthentication yes`；如果你的檔案叫 `99-xxx.conf`，就會被它蓋過去，**密碼登入還是開著**（在 Ubuntu 24.04 容器實測過）
- `sshd -t`：檢查設定檔語法，**沒有輸出就是正確**。有錯就先修正，不要重新啟動
- `systemctl restart ssh`：重新啟動 SSH 服務，讓設定生效。**目前的連線不會斷**

確認實際生效的設定（`sshd -T` 印出所有設定檔合併後的結果）：

```bash
sudo sshd -T | grep -E "^(passwordauthentication|permitrootlogin|pubkeyauthentication) "
```

應該是 `passwordauthentication no`、`permitrootlogin no`、`pubkeyauthentication yes`。

再**另開一個 cmd 視窗**測試（舊的還是不要關）：

```bash
ssh deploy@<主機IP>
ssh root@<主機IP>
ssh -o PubkeyAuthentication=no deploy@<主機IP>
```

| 指令 | 應該看到 |
|---|---|
| `ssh deploy@...` | 登入成功 |
| `ssh root@...` | `Permission denied (publickey)` |
| `ssh -o PubkeyAuthentication=no deploy@...`（強制用密碼） | `Permission denied (publickey)`，**不會問密碼** |

---

## 6. 防火牆：只開 22、80、443（主機）

Ubuntu 用 `ufw`（Uncomplicated Firewall），是 iptables 的簡化介面。

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

- `default deny incoming`：預設拒絕所有連進來的連線，只開放下面明確允許的
- `default allow outgoing`：主機往外連（下載更新、Docker 映像檔）不限制
- `allow OpenSSH`：開 22 port。⚠️ **一定要在 `enable` 之前**，否則啟用防火牆的瞬間 SSH 就被擋掉
- `allow 80/tcp`、`443/tcp`：網站的 HTTP、HTTPS（D03、D04 會用到）
- `enable`：啟用，開機也會自動啟用。會警告「可能中斷 SSH」，因為已經開了 22，輸入 `y`

### ⚠️ Docker 會繞過 ufw

Docker 對外公開的 port（docker-compose 的 `ports: "5432:5432"`）是**直接改 iptables**，ufw 擋不住。也就是說，就算 ufw 只開 22、80、443，寫成 `"5432:5432"` 的資料庫還是對全世界開放。

所以 D03 的正式環境：

- 資料庫、後端**完全不寫 `ports`**（容器之間用 Docker 內部網路連線），只有 Nginx 公開 80、443
- 如果真的要公開給主機自己用，寫成 `"127.0.0.1:5432:5432"`（只綁本機，本機開發的 `docker-compose.yml` 就是這樣）

`check-server.sh` 會檢查「實際在對所有網卡監聽的 port」，抓得到這種錯誤。

### 雲端廠商的防火牆

很多廠商在主機外面還有一層防火牆（Security Group、Cloud Firewall）。如果有，也設定成只允許 22、80、443。兩層都擋，其中一層設錯也還有另一層。

---

## 7. 自動安全性更新（主機）

Ubuntu 預設已經開啟 `unattended-upgrades`（每天自動安裝**安全性**更新，一般更新不會自動裝）。確認：

```bash
cat /etc/apt/apt.conf.d/20auto-upgrades
```

看到 `APT::Periodic::Unattended-Upgrade "1";` 就是開啟的。沒有的話：

```bash
sudo dpkg-reconfigure -plow unattended-upgrades
```

選 Yes。

---

## 8. swap（主機，記憶體 2GB 以下建議做）

swap 是用硬碟當作備用記憶體。記憶體快用完時，系統把暫時用不到的資料搬到硬碟，而不是直接把程式砍掉（OOM killer）。硬碟比記憶體慢很多，所以 swap 是「保險」，不是拿來擴充記憶體。

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

- `fallocate -l 2G /swapfile`：建立一個 2GB 的檔案
- `chmod 600`：只有 root 能讀寫（swap 裡可能有記憶體中的密碼等資料）
- `mkswap`：把檔案格式化成 swap
- `swapon`：開始使用
- `tee -a /etc/fstab`：寫進開機設定，重開機後自動啟用。用 `sudo tee` 而不是 `sudo echo ... >>`，因為 `>>` 是 shell 處理的，不會有 sudo 的權限
- `free -h`：看到 `Swap: 2.0Gi` 就成功

---

## 9. 時區（主機）

```bash
sudo timedatectl set-timezone Asia/Taipei
```

讓系統 log 的時間和備份排程（D05）用台灣時間，比較好讀。程式本身不受影響（資料庫存的是 UTC 時間，畫面顯示時才轉成台灣時間）。

---

## 10. 檢查（主機）

```bash
sudo bash ~/check-server.sh
```

每一項 PASS 才算完成（swap 是 WARN，不影響）。範例：

```
== SSH ==
PASS 關閉密碼登入（passwordauthentication no）
PASS 關閉鍵盤互動登入（另一種密碼登入）（kbdinteractiveauthentication no）
PASS 禁止 root 直接登入（permitrootlogin no）
PASS 允許金鑰登入（pubkeyauthentication yes）
PASS deploy 有 SSH 金鑰（/home/deploy/.ssh/authorized_keys）
PASS deploy 在 sudo 群組
== 防火牆（ufw）==
PASS ufw 已啟用
PASS 預設拒絕所有連入
PASS 只開放 22、80、443
== 實際在對外監聽的服務 ==
PASS 對外監聽的只有：22
== 系統 ==
PASS 自動安裝安全性更新（unattended-upgrades）
PASS 有 swap（2047 MB）
INFO 時區：Asia/Taipei

全部通過 👍
```

---

## 11. 讓連線方便一點（Windows，選做）

每次打 `ssh deploy@<主機IP>` 很麻煩，可以設定別名：

```bash
notepad %USERPROFILE%\.ssh\config
```

貼上（存檔時不要變成 `config.txt`）：

```
Host mamashop
    HostName <主機IP>
    User deploy
    IdentityFile ~/.ssh/id_ed25519
```

之後只要：

```bash
ssh mamashop
```

---

## 被鎖在外面怎麼辦

SSH 設錯、防火牆擋掉 22 port 都可能讓你連不進去。**不要慌，主機和資料都還在**：

1. 到主機廠商的網頁後台，找「Console」「VNC」「Serial console」：瀏覽器直接開一個主機的終端機，不需要 SSH
2. 用 root 或 deploy 的**密碼**登入（Console 不受 SSH 設定影響，所以第 4 步設定的密碼很重要）
3. 修正：例如 `sudo ufw allow OpenSSH`，或刪掉 `/etc/ssh/sshd_config.d/01-mamashop-hardening.conf` 再 `sudo systemctl restart ssh`

## 常見問題

| 狀況 | 可能的原因 | 檢查 |
|---|---|---|
| `Permission denied (publickey)` | 帳號打錯（root／deploy／ubuntu） | 確認 `ssh 帳號@IP` 的帳號 |
| 同上 | 主機上沒有你的公鑰 | `cat ~/.ssh/authorized_keys` 有沒有你那行 |
| 同上 | `.ssh` 權限太寬，sshd 拒絕使用 | `ls -ld ~/.ssh` 要是 `drwx------`（700）、`authorized_keys` 要是 `-rw-------`（600）。修正：`chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys` |
| 同上 | 用錯私鑰 | `ssh -i %USERPROFILE%\.ssh\id_ed25519 deploy@IP`；加 `-v` 看詳細過程 |
| `Connection timed out` | 防火牆擋掉（ufw 或廠商防火牆） | 用廠商 Console 登入，`sudo ufw status` |
| `WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED` | 主機重灌過（指紋變了），或真的被冒充 | 確定是自己重灌的：`ssh-keygen -R <主機IP>` 刪掉舊指紋 |
| sudo 要密碼但忘了 | 第 4 步設定的 deploy 密碼 | 用廠商 Console 以 root 登入，`passwd deploy` 重設 |

---

## 實際操作紀錄（請自己填）

| 項目 | 內容 |
|---|---|
| 廠商、方案、地區 | |
| 主機 IP | （不要寫進公開的 repo） |
| 完成日期 | |
| `check-server.sh` 結果 | |

遇到的問題與解法：

-

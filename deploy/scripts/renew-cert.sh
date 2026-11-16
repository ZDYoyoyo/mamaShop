#!/usr/bin/env bash
# HTTPS 憑證續期（D04）。Let's Encrypt 的憑證只有 90 天，要定期續期；由 cron 每天執行一次這個腳本。
#
# 用法（在主機上，用 deploy 帳號；不需要 sudo，deploy 已經在 docker 群組）：
#   bash ~/mamashop/deploy/scripts/renew-cert.sh
#
# 做兩件事：
#   1. certbot renew：檢查憑證，「剩不到 30 天」才會真的續期，其他時候什麼都不做（所以每天跑也沒關係）
#   2. 叫 Nginx 重新讀取設定（reload）：Nginx 啟動時就把憑證讀進記憶體，不 reload 的話還是用舊的那張。
#      reload 不會中斷正在進行的連線
#
# 設定排程與檢查方式見 docs/deploy/D04-https.md

# -e：任何指令失敗就停止；-u：用到沒定義的變數就報錯；-o pipefail：管線中任何一段失敗都算失敗
set -euo pipefail

# 這個腳本所在的資料夾的上一層＝deploy/（不管從哪個目錄執行都找得到 compose 檔）
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose -f "$DEPLOY_DIR/docker-compose.prod.yml")

echo "===== $(date '+%Y-%m-%d %H:%M:%S') 檢查憑證 ====="

# run --rm：啟動一個暫時的 certbot 容器，跑完就刪掉
# renew：檢查 letsencrypt volume 裡所有的憑證，快到期的才續期（用當初申請時的方式：webroot）
# --quiet：沒事就不印東西，有錯才印
# cron 執行時沒有終端機：-T 告訴 docker 不要配置 TTY，否則會報「the input device is not a TTY」
"${COMPOSE[@]}" run --rm -T certbot renew --quiet

# 不管這次有沒有續期都 reload：很便宜，而且不用判斷「這次到底有沒有換憑證」
# nginx -t 先檢查設定，確定沒問題才 reload（設定有錯時 reload 會失敗，但舊的設定會繼續運作）
"${COMPOSE[@]}" exec -T web nginx -t
"${COMPOSE[@]}" exec -T web nginx -s reload

echo "完成。憑證到期日："
# certificates：列出每張憑證的網域與到期日
"${COMPOSE[@]}" run --rm -T certbot certificates 2>/dev/null | grep -E "Certificate Name|Expiry Date" || true

# 備份、還原腳本共用的設定（D05）。不是直接執行的：由 backup.sh、verify-backup.sh、restore.sh 用 source 讀進來。

# -e：任何指令失敗就停止；-u：用到沒定義的變數就報錯；-o pipefail：管線（a | b）中任何一段失敗都算失敗
# （備份最怕「其實失敗了，卻留下一個看起來正常的檔案」）
set -euo pipefail
# 之後建立的檔案只有自己能讀寫（600）、資料夾只有自己能進（700）：備份裡有顧客的姓名、電話、地址
umask 077

# 這個檔案所在資料夾的上一層＝deploy/（不管從哪個目錄執行都找得到）
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose -f "$DEPLOY_DIR/docker-compose.prod.yml")

if [ ! -f "$DEPLOY_DIR/.env" ]; then
  echo "找不到 $DEPLOY_DIR/.env" >&2
  exit 1
fi

# 從 deploy/.env 讀一個值（沒有這一行就用第二個參數當預設值）。
# 不直接 source .env：裡面的值可能有 shell 的特殊字元（例如密碼裡的 $ 或空白）
env_value() {
  local value
  value="$(grep -E "^$1=" "$DEPLOY_DIR/.env" | tail -n 1 | cut -d= -f2- || true)"
  echo "${value:-${2:-}}"
}

DB_NAME="$(env_value DB_NAME mamashop)"
DB_USERNAME="$(env_value DB_USERNAME mamashop)"
# 備份放哪裡、保留幾天：可以在 deploy/.env 設定（見 .env.example），沒設定就用預設值
BACKUP_DIR="$(env_value BACKUP_DIR "$HOME/backups/mamashop")"
BACKUP_KEEP_DAYS="$(env_value BACKUP_KEEP_DAYS 14)"
BACKUP_RCLONE_REMOTE="$(env_value BACKUP_RCLONE_REMOTE "")"
BACKUP_REMOTE_KEEP_DAYS="$(env_value BACKUP_REMOTE_KEEP_DAYS 30)"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }

# 在資料庫容器裡執行指令。-T：不配置終端機（cron 執行、或要用管線傳資料時必須加）
db_exec() { "${COMPOSE[@]}" exec -T db "$@"; }

# 最新的一份備份（檔名有時間，照名稱排序最後一個就是最新的）
# 用 find 而不是 ls 萬用字元：沒有符合的檔案時 find 只是什麼都不印，不會讓整個腳本因為錯誤而靜靜結束
latest_backup() {
  { find "$BACKUP_DIR" -maxdepth 1 -type f -name "$1" 2>/dev/null || true; } | sort | tail -n 1
}

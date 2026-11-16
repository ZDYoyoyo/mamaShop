#!/usr/bin/env bash
# 每日備份（D05）：資料庫＋商品圖片。由 cron 每天執行；也可以隨時手動執行。
#
# 用法（在主機上，用 deploy 帳號，不需要 sudo）：
#   bash ~/mamashop/deploy/scripts/backup.sh
#
# 產生兩個檔案（預設在 ~/backups/mamashop/）：
#   db-20261116-033000.dump         資料庫（pg_dump 的自訂格式，已壓縮）
#   uploads-20261116-033000.tar.gz  商品圖片
# 然後刪掉超過保留天數的舊檔；有設定 BACKUP_RCLONE_REMOTE 的話，再複製一份到主機以外的地方。
#
# 沒有備份的東西（原因見 docs/deploy/D05-backup.md）：deploy/.env、HTTPS 憑證、RabbitMQ、Redis
#
# 設定排程、還原方式見 docs/deploy/D05-backup.md

source "$(dirname "${BASH_SOURCE[0]}")/backup-lib.sh"

stamp="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
db_file="$BACKUP_DIR/db-$stamp.dump"
uploads_file="$BACKUP_DIR/uploads-$stamp.tar.gz"

# 中途失敗時刪掉寫到一半的檔案：留著的話，之後會被誤認成一份完整的備份
cleanup() { rm -f "$db_file.partial" "$uploads_file.partial"; }
trap cleanup EXIT

log "開始備份（$BACKUP_DIR）"

# ===== 1. 資料庫 =====
# pg_dump：匯出「某一瞬間」一致的資料（用一個交易讀完），網站不用停、顧客可以照常下單
#   -Fc：自訂格式（custom）。已經壓縮，而且還原時可以只還原某幾張表、或先列出內容檢查
#   先寫到 .partial，成功才改名：檔名是 .dump 的一定是完整的
db_exec pg_dump -U "$DB_USERNAME" -d "$DB_NAME" -Fc > "$db_file.partial"

# 檢查這個檔案真的能被 pg_restore 讀懂（--list 只列出內容、不還原）。空檔案或壞掉的檔案會在這裡失敗
tables="$(db_exec pg_restore --list < "$db_file.partial" | grep -c ' TABLE DATA ' || true)"
if [ "$tables" -lt 1 ]; then
  log "失敗：資料庫備份裡沒有任何資料表" >&2
  exit 1
fi
mv "$db_file.partial" "$db_file"
log "資料庫：$(du -h "$db_file" | cut -f1)，$tables 張表"

# ===== 2. 商品圖片 =====
# 圖片在 Docker volume 裡，從 web 容器（唯讀掛載在 /srv/uploads）打包出來。
#   tar -c：建立壓縮檔；-z：gzip；-f -：輸出到標準輸出（再由主機這邊存成檔案）；-C /srv：先切到 /srv 再打包 uploads/
"${COMPOSE[@]}" exec -T web tar -czf - -C /srv uploads > "$uploads_file.partial"
# -t：列出壓縮檔內容（順便確認檔案沒有壞）；數一下有幾個檔案（不算資料夾）
images="$(tar -tzf "$uploads_file.partial" | grep -vc '/$' || true)"
mv "$uploads_file.partial" "$uploads_file"
log "商品圖片：$(du -h "$uploads_file" | cut -f1)，$images 個檔案"

# ===== 3. 刪掉太舊的備份 =====
# 走到這裡代表今天的備份成功了（前面任何一步失敗腳本就會停止），才刪舊的：
# 備份連續失敗的時候，舊的備份會一直留著，不會被刪光。
#   -mtime +N：最後修改時間超過 N 天；-print：印出檔名（記在 log 裡）；-delete：刪除
# before-restore-*.dump：restore.sh 還原前自動另存的那一份，一樣過期就刪
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'db-*.dump' -o -name 'uploads-*.tar.gz' -o -name 'before-restore-*.dump' \) \
  -mtime +"$BACKUP_KEEP_DAYS" -print -delete | sed 's/^/刪除舊備份：/'

# ===== 4. 複製到主機以外的地方 =====
# 備份和網站在同一台主機：主機壞掉、被刪、帳號被停，備份跟著一起不見。一定要有一份在別的地方。
if [ -n "$BACKUP_RCLONE_REMOTE" ]; then
  # rclone copy：把本機有、遠端沒有的檔案傳上去。不會刪遠端的檔案（本機的備份被誤刪時，遠端的還在）
  rclone copy "$BACKUP_DIR" "$BACKUP_RCLONE_REMOTE" --include 'db-*.dump' --include 'uploads-*.tar.gz'
  # 遠端另外保留比較久；--min-age：只刪超過這個時間的
  rclone delete "$BACKUP_RCLONE_REMOTE" --min-age "${BACKUP_REMOTE_KEEP_DAYS}d" \
    --include 'db-*.dump' --include 'uploads-*.tar.gz'
  log "已複製到 $BACKUP_RCLONE_REMOTE"
else
  log "⚠️ 沒有設定 BACKUP_RCLONE_REMOTE：備份只存在這台主機上（見 docs/deploy/D05-backup.md 第 4 節）"
fi

log "備份完成"

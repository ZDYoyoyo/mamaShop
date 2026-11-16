#!/usr/bin/env bash
# 還原演練（D05）：把備份還原到一個「暫時的資料庫」，確認備份真的能用。
# 不會動到正式的資料庫，網站不用停，隨時可以執行。建議每個月做一次。
#
# 用法：
#   bash ~/mamashop/deploy/scripts/verify-backup.sh              # 檢查最新的一份
#   bash ~/mamashop/deploy/scripts/verify-backup.sh 備份檔.dump   # 檢查指定的檔案
#
# 沒有還原過的備份不算備份：檔案在，不代表內容是對的、還原得回來。

source "$(dirname "${BASH_SOURCE[0]}")/backup-lib.sh"

db_file="${1:-$(latest_backup 'db-*.dump')}"
if [ -z "$db_file" ] || [ ! -f "$db_file" ]; then
  echo "找不到備份檔。先執行 backup.sh，或指定檔案：$0 路徑/db-xxxx.dump" >&2
  exit 1
fi
TEST_DB="${DB_NAME}_restore_test"

# 不管成功失敗，結束時都刪掉暫時的資料庫
cleanup() { db_exec dropdb -U "$DB_USERNAME" --if-exists "$TEST_DB" >/dev/null 2>&1 || true; }
trap cleanup EXIT

log "還原演練：$db_file → 暫時的資料庫 $TEST_DB"
cleanup
# createdb：建立一個空的資料庫
db_exec createdb -U "$DB_USERNAME" "$TEST_DB"
# pg_restore：把備份還原進去。--no-owner：不管原本的擁有者是誰，都用現在這個帳號；
# --exit-on-error：遇到第一個錯誤就停（預設是繼續做完、最後才說有幾個錯）
db_exec pg_restore -U "$DB_USERNAME" -d "$TEST_DB" --no-owner --exit-on-error < "$db_file"

# 每張重要的表有幾筆：備份裡的（還原出來的） vs. 現在正式資料庫的。
# 備份之後才新增的資料，正式的會比較多，這是正常的；備份的是 0、或少很多，才要查原因
count() { db_exec psql -U "$DB_USERNAME" -d "$1" -At -c "SELECT count(*) FROM $2" 2>/dev/null || echo "-"; }
printf '\n%-26s %10s %10s\n' "資料表" "備份" "現在"
for table in users categories products orders order_items order_status_histories payments shipments; do
  printf '%-26s %10s %10s\n' "$table" "$(count "$TEST_DB" "$table")" "$(count "$DB_NAME" "$table")"
done
# 資料表結構的版本（Flyway 的紀錄）：還原後後端啟動時會從這個版本接著升級
printf '%-26s %10s %10s\n' "Flyway 版本" \
  "$(db_exec psql -U "$DB_USERNAME" -d "$TEST_DB" -At -c "SELECT max(installed_rank) FROM flyway_schema_history")" \
  "$(db_exec psql -U "$DB_USERNAME" -d "$DB_NAME" -At -c "SELECT max(installed_rank) FROM flyway_schema_history")"
# 備份裡最新的一筆訂單：確認備份不是很久以前的
echo
echo "備份裡最新的訂單：$(db_exec psql -U "$DB_USERNAME" -d "$TEST_DB" -At -c "SELECT coalesce(to_char(max(created_at) AT TIME ZONE 'Asia/Taipei', 'YYYY-MM-DD HH24:MI'), '（沒有訂單）') FROM orders")"

# 同一個時間的圖片備份：確認解得開，並和現在的檔案數比較
uploads_file="${db_file/db-/uploads-}"
uploads_file="${uploads_file%.dump}.tar.gz"
if [ -f "$uploads_file" ]; then
  backed_up="$(tar -tzf "$uploads_file" | grep -vc '/$' || true)"
  current="$("${COMPOSE[@]}" exec -T web sh -c 'find /srv/uploads -type f | wc -l' | tr -d '[:space:]')"
  echo "商品圖片：備份 $backed_up 個檔案，現在 $current 個"
else
  echo "⚠️ 找不到對應的圖片備份：$uploads_file"
fi

echo
log "還原演練完成：備份可以還原（暫時的資料庫已刪除）"

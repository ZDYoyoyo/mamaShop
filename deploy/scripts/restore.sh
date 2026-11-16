#!/usr/bin/env bash
# 還原（D05）：用備份「取代」目前的資料庫與商品圖片。⚠️ 目前的資料會被蓋掉。
# 用在：主機壞了換一台新的、資料被誤刪、要回到某一天的狀態。
# 只是想確認備份能不能用，請用 verify-backup.sh（不會動到正式資料）。
#
# 用法：
#   bash ~/mamashop/deploy/scripts/restore.sh 路徑/db-20261116-033000.dump [路徑/uploads-20261116-033000.tar.gz]
#
# 前提：docker compose 的服務已經啟動過（至少 db 在執行）。完整步驟見 docs/deploy/D05-backup.md 第 6 節

source "$(dirname "${BASH_SOURCE[0]}")/backup-lib.sh"

db_file="${1:-}"
uploads_file="${2:-}"
if [ -z "$db_file" ] || [ ! -f "$db_file" ]; then
  echo "用法：$0 資料庫備份.dump [圖片備份.tar.gz]" >&2
  exit 1
fi
if [ -n "$uploads_file" ] && [ ! -f "$uploads_file" ]; then
  echo "找不到圖片備份：$uploads_file" >&2
  exit 1
fi

echo "即將用下面的備份「取代」目前的資料："
echo "  資料庫：$db_file → $DB_NAME"
[ -n "$uploads_file" ] && echo "  商品圖片：$uploads_file"
echo "備份之後才產生的訂單、會員、商品都會消失。"
# 要求打出資料庫名稱才繼續：避免手滑按到 Enter。RESTORE_CONFIRM 是給自動化測試用的
if [ "${RESTORE_CONFIRM:-}" != "$DB_NAME" ]; then
  read -r -p "確定的話請輸入資料庫名稱（$DB_NAME）：" answer
  if [ "$answer" != "$DB_NAME" ]; then
    echo "已取消，沒有做任何變更。"
    exit 1
  fi
fi

# 還原前先把「現在的資料」再備份一次：萬一還原錯了檔案，還有後悔的機會
safety="$BACKUP_DIR/before-restore-$(date +%Y%m%d-%H%M%S).dump"
mkdir -p "$BACKUP_DIR"
db_exec pg_dump -U "$DB_USERNAME" -d "$DB_NAME" -Fc > "$safety"
log "還原前的資料已另存：$safety"

# 停掉後端：還原的過程中不能有人下單、也不能有程式連著資料庫改資料
log "停止後端"
"${COMPOSE[@]}" stop backend

log "還原資料庫"
# --clean --if-exists：先刪掉現有的資料表再重建（資料庫是空的也不會報錯）
# --single-transaction：整個還原包在一個交易裡，中途失敗就全部復原，不會停在「刪了一半」的狀態
# --no-owner：不管備份時的擁有者，都用現在這個帳號
db_exec pg_restore -U "$DB_USERNAME" -d "$DB_NAME" --clean --if-exists --no-owner --single-transaction < "$db_file"

# 清掉 Redis 的商品快取：裡面是「還原前」的商品資料，不清的話前台最多 10 分鐘看到舊的
# （實測：還原到一台空的主機後，商品列表還是空的，因為快取了還原前「沒有商品」的結果）。
# Redis 裡只有快取和結帳用的一次性識別碼，全部清掉沒有關係
log "清除商品快取"
"${COMPOSE[@]}" exec -T redis redis-cli FLUSHALL >/dev/null

log "啟動後端（啟動時 Flyway 會檢查資料表版本，比程式舊的話自動升級）"
"${COMPOSE[@]}" up -d backend
# 等後端的健康檢查通過（最多 2 分鐘）
for _ in $(seq 1 40); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$("${COMPOSE[@]}" ps -q backend)" 2>/dev/null || true)"
  [ "$status" = healthy ] && break
  sleep 3
done
if [ "${status:-}" != healthy ]; then
  log "⚠️ 後端沒有變成 healthy，請看：docker compose -f deploy/docker-compose.prod.yml logs backend --tail 50" >&2
  exit 1
fi

if [ -n "$uploads_file" ]; then
  log "還原商品圖片"
  # 圖片的 volume 只有後端可以寫（掛在 /app/uploads），所以從後端容器解壓縮。
  # tar -x：解開；-C /app：解到 /app（壓縮檔裡的路徑是 uploads/檔名）。
  # 同名的檔案會被備份的蓋掉；備份裡沒有的檔案不會被刪（多出來的圖片沒有商品在用，不影響）
  "${COMPOSE[@]}" exec -T backend tar -xzf - -C /app < "$uploads_file"
fi

log "還原完成。請打開網站確認商品、圖片、訂單都正常"

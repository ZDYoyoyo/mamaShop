#!/usr/bin/env bash
#
# 檢查主機的基本安全設定（D01）。在主機上執行：
#   sudo bash check-server.sh
#
# 每一項顯示 PASS／FAIL／WARN；有任何 FAIL 時結束代碼是 1。
# 只讀取設定，不會修改任何東西。

set -u

fails=0
pass() { printf '\033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '\033[31mFAIL\033[0m %s\n' "$1"; fails=$((fails + 1)); }
warn() { printf '\033[33mWARN\033[0m %s\n' "$1"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "請用 sudo 執行：sudo bash $0"
  exit 2
fi

echo "== SSH =="
# sshd -T：印出 sshd「實際生效」的設定（所有設定檔合併後的結果），比直接看設定檔可靠
if ! sshd_config=$(sshd -T 2>/dev/null); then
  fail "無法讀取 sshd 設定（sshd -T 失敗，先執行 sudo sshd -t 看錯誤）"
else
  check_sshd() { # 設定名稱（小寫）、期望值、說明
    actual=$(printf '%s\n' "$sshd_config" | awk -v key="$1" '$1 == key { print $2; exit }')
    if [ "$actual" = "$2" ]; then pass "$3（$1 $actual）"; else fail "$3（$1 目前是 ${actual:-未設定}，應該是 $2）"; fi
  }
  check_sshd passwordauthentication no "關閉密碼登入"
  check_sshd kbdinteractiveauthentication no "關閉鍵盤互動登入（另一種密碼登入）"
  check_sshd permitrootlogin no "禁止 root 直接登入"
  check_sshd pubkeyauthentication yes "允許金鑰登入"
fi

# 執行 sudo 的那個帳號：要有金鑰才登得進來
login_user=${SUDO_USER:-}
if [ -z "$login_user" ] || [ "$login_user" = "root" ]; then
  warn "請用一般帳號登入後再 sudo 執行，才能檢查該帳號的 SSH 金鑰"
else
  keys="/home/$login_user/.ssh/authorized_keys"
  if [ -s "$keys" ]; then pass "$login_user 有 SSH 金鑰（$keys）"; else fail "$login_user 沒有 SSH 金鑰（$keys 不存在或是空的）"; fi
  if id -nG "$login_user" | tr ' ' '\n' | grep -qx sudo; then pass "$login_user 在 sudo 群組"; else fail "$login_user 不在 sudo 群組"; fi
fi

echo "== 防火牆（ufw）=="
if ! command -v ufw >/dev/null 2>&1; then
  fail "沒有安裝 ufw（sudo apt install ufw）"
else
  status=$(ufw status verbose 2>/dev/null)
  if printf '%s\n' "$status" | grep -q '^Status: active'; then pass "ufw 已啟用"; else fail "ufw 沒有啟用（sudo ufw enable）"; fi
  if printf '%s\n' "$status" | grep -q 'deny (incoming)'; then pass "預設拒絕所有連入"; else fail "預設沒有拒絕連入（sudo ufw default deny incoming）"; fi
  # 允許連入的 port（去掉 IPv6 重複的那行）
  allowed=$(printf '%s\n' "$status" | awk '/ALLOW IN/ { split($1, a, "/"); print a[1] }' | sed 's/OpenSSH/22/' | sort -un | tr '\n' ' ')
  expected="22 80 443 "
  if [ "$allowed" = "$expected" ]; then pass "只開放 22、80、443"; else fail "開放的 port 是：${allowed:-（沒有）}，應該只有 22 80 443"; fi
fi

echo "== 實際在對外監聽的服務 =="
# ⚠️ Docker 對外公開的 port（docker-compose 的 ports: "5432:5432"）會直接改 iptables，
#    繞過 ufw！所以除了看 ufw，也要看實際有哪些程式在監聽所有網卡（0.0.0.0 或 [::]）
public=$(ss -tlnH 2>/dev/null | awk '{ print $4 }' | grep -E '^(0\.0\.0\.0|\*|\[::\]):' | sed -E 's/.*:([0-9]+)$/\1/' | sort -un | tr '\n' ' ')
unexpected=$(for port in $public; do case $port in 22 | 80 | 443) ;; *) printf '%s ' "$port" ;; esac; done)
if [ -z "$unexpected" ]; then pass "對外監聽的只有：${public:-（沒有）}"; else fail "這些 port 對所有網卡開放：$unexpected（如果是 Docker 開的，ufw 擋不住，請改成 127.0.0.1:port 或不要公開）"; fi

echo "== 系統 =="
if [ -f /etc/apt/apt.conf.d/20auto-upgrades ] && grep -q 'Unattended-Upgrade "1"' /etc/apt/apt.conf.d/20auto-upgrades; then
  pass "自動安裝安全性更新（unattended-upgrades）"
else
  fail "沒有開啟自動安全性更新（sudo dpkg-reconfigure -plow unattended-upgrades）"
fi
swap_kb=$(awk '/^SwapTotal:/ { print $2 }' /proc/meminfo)
if [ "${swap_kb:-0}" -gt 0 ]; then pass "有 swap（$((swap_kb / 1024)) MB）"; else warn "沒有 swap：記憶體 2GB 以下的主機建議加 2GB swap，避免記憶體不足時程式被系統砍掉"; fi
echo "INFO 時區：$(timedatectl show -p Timezone --value 2>/dev/null || cat /etc/timezone 2>/dev/null)"

echo
if [ "$fails" -eq 0 ]; then echo "全部通過 👍"; else echo "有 $fails 項沒通過，請依說明修正後再執行一次"; fi
[ "$fails" -eq 0 ]

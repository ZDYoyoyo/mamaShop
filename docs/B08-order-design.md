# B08 建立訂單：設計說明

這份文件說明 `POST /api/orders` 的設計，重點是**併發**、**交易**、**回滾**。
對應程式：`backend/src/main/java/com/mamashop/order/service/OrderService.java`

---

## 1. API

```http
POST /api/orders
Authorization: Bearer {token}

{
  "items": [ { "productId": 1, "quantity": 2 }, { "productId": 5, "quantity": 1 } ],
  "shippingMethod": "HOME_DELIVERY",          // 或 CVS_PICKUP
  "recipientName": "王小明",
  "recipientPhone": "0912345678",
  "shippingAddress": "台北市…",                // 宅配必填
  "cvsBrand": "SEVEN", "cvsStoreCode": "…", "cvsStoreName": "…",   // 超商取貨必填
  "note": "…"
}
```

成功回 `201`，內容是訂單明細（`orderNo`、狀態、金額、明細）。

**請求裡沒有任何價格或金額**：價格一律以伺服器當下的資料為準。前端送來的金額可以被竄改（開瀏覽器開發工具就能改），不能相信。

會員 id 從 JWT 取得（`@AuthenticationPrincipal`），不從請求讀，所以無法替別人下單。

---

## 2. 流程（全部在同一個交易內）

```
@Transactional  ─────────────────────────────────────────────── BEGIN
 1. 合併重複商品，依商品 id 由小到大排序
 2. 查運送方式（停用 → 409），取得當下運費
 3. 逐一扣庫存：UPDATE products SET stock = stock - ?
                WHERE id = ? AND status = 'ON_SALE' AND stock >= ?
       └─ 任何一個更新 0 筆 → 丟 BusinessException ──────────── ROLLBACK
 4. 讀商品（這些列已被本交易鎖住），把名稱、單價快照到明細
 5. 算金額：小計 = 單價 × 數量；商品金額 = Σ小計；總額 = 商品金額 + 運費
 6. 產生訂單編號，INSERT orders、order_items（cascade）
 7. INSERT order_status_histories（NULL → PENDING_CONFIRM，操作者＝顧客）
 方法正常結束 ─────────────────────────────────────────────────── COMMIT
```

---

## 3. 為什麼不會超賣：條件更新

### 錯誤寫法：先讀、再判斷、再寫回

```java
Product p = productRepository.findById(id);   // 讀到 stock = 10
if (p.getStock() < qty) throw ...;            // 夠
p.setStock(p.getStock() - qty);                // 寫回 9
```

兩個交易同時執行時：

| 時間 | 交易 A | 交易 B |
|---|---|---|
| t1 | 讀到 10 | |
| t2 | | 讀到 10 |
| t3 | 寫回 9，COMMIT | |
| t4 | | 寫回 9，COMMIT ← 蓋掉 A 的結果 |

兩筆訂單都成功，庫存卻只少 1（lost update）。

**實測**：把程式暫時改成這種寫法，跑併發測試（庫存 10、50 人同時搶），**50 筆全部成功**，超賣 5 倍。

### 正確寫法：一條 UPDATE 完成「檢查＋扣除」

```sql
UPDATE products SET stock = stock - :qty
 WHERE id = :id AND status = 'ON_SALE' AND stock >= :qty
```

- PostgreSQL 執行 UPDATE 時會**鎖住這一列**（row lock），直到交易結束
- 第二個交易要更新同一列時會**等待**；第一個交易結束後，它會用**最新的值**重新判斷 `stock >= :qty`
- 所以「檢查」和「扣除」是不可分割的一個動作
- 回傳更新筆數：1 成功、0 失敗（庫存不足、下架或不存在）

**實測**：庫存 10、50 人同時搶 → 剛好 10 筆成功、40 筆收到「庫存不足」、最後庫存 0。

### 其他做法比較

| 做法 | 說明 | 為什麼沒選 |
|---|---|---|
| 條件更新（本專案） | 一條 UPDATE，資料庫保證原子性 | ✅ 簡單、正確、不需重試 |
| 悲觀鎖 `SELECT ... FOR UPDATE` | 先鎖住再讀、再更新 | 也正確，但要兩條 SQL，效果和條件更新相同 |
| 樂觀鎖 `@Version` | 更新時比對版本號，不符就失敗 | 搶購時大部分人都會版本衝突，需要重試邏輯，體驗差。適合「很少衝突」的情境（例如後台多人改同一張訂單，B03 已用在 `orders`） |
| Redis 預扣（R03） | 先在 Redis 原子扣減，再非同步寫入資料庫 | 適合超高流量搶購，但多了資料一致性問題。家人的網站不需要 |
| 只靠 `CHECK (stock >= 0)` | 扣成負數時資料庫報錯 | 能防超賣，但錯誤訊息不友善。本專案把它當**最後一道防線** |

---

## 4. 為什麼要依商品 id 排序：避免死結

假設訂單 A 買「商品1、商品2」，訂單 B 買「商品2、商品1」，同時執行：

| 時間 | 交易 A | 交易 B |
|---|---|---|
| t1 | 鎖住商品1 | 鎖住商品2 |
| t2 | 等商品2（被 B 鎖住）… | 等商品1（被 A 鎖住）… |

互相等待，形成**死結**。PostgreSQL 偵測到後會讓其中一筆直接失敗（`deadlock detected`）。

解法：**所有交易都用同樣的順序上鎖**（依商品 id 由小到大），就不可能形成循環等待。程式用 `TreeMap` 自動排序。

**實測**：40 筆訂單一半「X、Y」、一半「Y、X」同時下單：
- 有排序：40 筆全部成功
- 暫時拿掉排序：3 次測試都出現大量 `deadlock detected`，測試失敗

---

## 5. 交易與回滾

### `@Transactional` 做了什麼

Spring 在 `OrderService` 外面包一層代理（proxy）：

```
Controller → [代理：BEGIN] → createOrder() → [代理：COMMIT 或 ROLLBACK]
```

- 方法正常結束 → COMMIT
- 丟出 **RuntimeException**（例如 `BusinessException`）→ ROLLBACK
- 丟出 **checked exception**（例如 `IOException`）→ 預設**不會** rollback！這也是 `BusinessException` 繼承 `RuntimeException` 的原因

### 庫存不足時發生什麼事

訂單買「帆布包 ×2、零錢包 ×5」，零錢包只剩 1 件：

1. UPDATE 帆布包 → 成功（庫存 10 → 8，**尚未 commit**，其他交易看不到）
2. UPDATE 零錢包 → 0 筆 → 丟 `BusinessException(INSUFFICIENT_STOCK)`
3. Spring 執行 ROLLBACK → 帆布包回到 10，訂單完全沒有寫入
4. `GlobalExceptionHandler` 回 409：「「碎花零錢包」庫存不足，目前剩 1 件」

**測試**：`CreateOrderIntegrationTest.insufficientStock_rollsBackTheWholeOrder` 驗證帆布包庫存仍是 10、訂單數為 0。

### 常見陷阱（和傳統 Spring MVC 一樣）

- **同一個類別內呼叫**：`this.createOrder()` 不會經過代理，`@Transactional` 無效
- **方法必須是 public**
- **在方法內 catch 掉例外又不丟出**：Spring 不知道出錯，會照樣 COMMIT

### 交易要盡量短

扣庫存的 UPDATE 鎖住商品列，直到交易結束。交易越長，其他人等越久。所以交易內**不做任何外部呼叫**（寄信、呼叫金流 API）。之後的訂單通知信（M01）要在 commit **之後**透過佇列處理。

---

## 6. 快照：價格、名稱、運費

`order_items.product_name`、`unit_price` 和 `orders.shipping_fee` 存的是**下單當下**的值。之後商品改名、改價、運費調整，都不影響已成立的訂單。

讀價格的時機：**扣完庫存之後**。這時商品列已被本交易鎖住，別人改不了價格或狀態，所以讀到的價格和扣庫存是同一個時間點。如果先讀價格再扣庫存，中間可能有人剛好改價。

---

## 7. 其他細節

| 項目 | 做法 | 理由 |
|---|---|---|
| 重複商品 | 合併成一筆（2 + 3 → 5） | `order_items` 有 `UNIQUE (order_id, product_id)` |
| 單一商品數量 | 合併後最多 99 | 避免惡意一次買光 |
| 訂單編號 | `M` + 台灣日期 `yyMMdd` + 10 位隨機數 | 流水號可以被猜、會透露訂單量；隨機數用 `SecureRandom` |
| 金額計算 | `BigDecimal`，單價 × 數量 | 不用 `double`（`0.1 + 0.2 != 0.3`）；資料庫有 CHECK 確保總額 = 商品金額 + 運費 |
| 金額上限 | 超過 99,999,999.99 → 400 | 對應 `NUMERIC(10,2)`，避免寫入時才出錯 |
| 收件資訊 | 自訂驗證 `@ValidShippingInfo`，錯誤掛在對應欄位 | 宅配要地址、超商要門市；資料庫也有同樣的 CHECK |
| 不相關欄位 | 宅配不存門市、超商不存地址 | 前端可能兩邊都送，只保留有意義的 |
| 會員 | `getReferenceById` | 只需要外鍵，不用多查一次資料庫 |

---

## 8. 還沒處理的（記錄在 NOTES.md）

- **重複送出**：連點兩下「送出訂單」會建立兩筆訂單。目前靠前端按下後停用按鈕（F05），R02 會用 Redis 一次性 token 從後端擋
- **訂單編號重複**：機率極低，真的發生時整筆回滾、回 500，顧客重送即可
- **庫存回補**：取消訂單歸還庫存在 B09／B10

---

## 9. 面試可能被問的問題

1. **怎麼防止超賣？** → 條件更新，說明 row lock 與 WHERE 重新判斷；提到實測錯誤寫法超賣 5 倍
2. **為什麼不用樂觀鎖？** → 搶購時衝突率高，大部分請求要重試；樂觀鎖適合低衝突情境（本專案用在後台訂單管理）
3. **為什麼不用 `synchronized`？** → 只在單一 JVM 有效，開兩台後端就沒用；而且鎖的範圍是整個方法，所有商品的訂單都要排隊
4. **多個商品時怎麼保證全部成功或全部失敗？** → 同一個交易，任何一個失敗就 RuntimeException → rollback
5. **死結怎麼發生、怎麼避免？** → 交叉上鎖；依固定順序（商品 id）上鎖
6. **為什麼要價格快照？** → 訂單是交易紀錄，商品改價不能影響已成立的訂單
7. **流量再大 100 倍怎麼辦？** → 熱門商品的同一列鎖會成為瓶頸 → Redis 預扣（R03）、佇列削峰

-- 壓力測試用的測試資料（R00）
--
-- 4 個分類、1000 個商品（900 個上架、100 個下架）。名稱一律以 LT- 開頭，cleanup.sql 用這個前綴刪除。
-- 商品名稱輪流用 5 種（帆布托特包、棉麻斜背包…），搜尋「帆布」時大約五分之一的商品會符合。
--
-- ⚠️ 只在本機或測試環境執行，不要在正式站執行（PLAN：不要壓正式站）
--
-- 執行（repo 根目錄，正式環境設定在本機跑的那套）：
--   docker compose -f deploy/docker-compose.prod.yml exec -T db psql -U mamashop -d mamashop < loadtest/seed.sql
-- 重複執行不會重複新增（已經有 LT- 商品就跳過）。

BEGIN;

INSERT INTO categories (name, sort_order)
SELECT 'LT-分類' || g, 100 + g
FROM generate_series(1, 4) AS g
ON CONFLICT (name) DO NOTHING;

INSERT INTO products (category_id, name, description, price, stock, status)
SELECT c.id,
       'LT-' || (ARRAY['帆布托特包', '棉麻斜背包', '碎花零錢包', '日本棉麻布', '防潑水後背包'])[1 + g % 5] || ' ' || g,
       -- 描述長度接近真實商品（詳情 API 會回傳描述）
       repeat('壓力測試用的商品描述，模擬一般商品介紹的長度。', 10),
       100 + (g % 50) * 20,
       50 + g % 100,
       CASE WHEN g % 10 = 0 THEN 'OFF_SALE' ELSE 'ON_SALE' END
FROM generate_series(1, 1000) AS g
JOIN (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM categories WHERE name LIKE 'LT-%') AS c
  ON c.rn = 1 + g % 4
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name LIKE 'LT-%');

COMMIT;

-- 讓 PostgreSQL 更新統計資訊，查詢計畫才會反映新的資料量
ANALYZE categories;
ANALYZE products;

SELECT count(*) FILTER (WHERE status = 'ON_SALE') AS 上架商品, count(*) AS 全部測試商品
FROM products WHERE name LIKE 'LT-%';

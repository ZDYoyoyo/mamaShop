-- 刪除 seed.sql 建立的壓力測試資料（名稱以 LT- 開頭的商品與分類）
--
--   docker compose -f deploy/docker-compose.prod.yml exec -T db psql -U mamashop -d mamashop < loadtest/cleanup.sql
--
-- 壓測腳本只讀不寫（不會下單），所以測試商品不會被訂單參照，可以直接刪除。

BEGIN;
DELETE FROM products WHERE name LIKE 'LT-%';
DELETE FROM categories WHERE name LIKE 'LT-%';
COMMIT;

SELECT count(*) AS 剩下的測試商品 FROM products WHERE name LIKE 'LT-%';

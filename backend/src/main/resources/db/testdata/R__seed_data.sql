-- =====================================================================
-- 本機開發用測試資料（只在 dev profile 載入，正式環境不會執行）
--
-- 這是 Flyway 的 Repeatable migration（R__ 開頭）：
--   * 每次 versioned migration（V1、V2…）跑完後執行
--   * 檔案內容有變動才會重跑，所以每一段都要能重複執行（idempotent）
-- 會員資料（含管理員）在 B06 處理，因為密碼雜湊要由程式產生。
-- =====================================================================

INSERT INTO categories (name, sort_order) VALUES
    ('手提包', 1),
    ('肩背包', 2),
    ('零錢包', 3),
    ('布料',   4)
ON CONFLICT (name) DO NOTHING;

-- 商品沒有天然唯一鍵，用「同分類下同名稱不存在才新增」確保可重複執行
INSERT INTO products (category_id, name, description, price, stock, status)
SELECT c.id, v.name, v.description, v.price, v.stock, v.status
FROM (VALUES
    ('手提包', '帆布托特包（米白）', '厚磅帆布，內附小口袋。尺寸：寬 38 × 高 32 × 底 12 cm',            890.00, 10, 'ON_SALE'),
    ('手提包', '帆布托特包（深藍）', '厚磅帆布，內附小口袋。尺寸：寬 38 × 高 32 × 底 12 cm',            890.00,  3, 'ON_SALE'),
    ('手提包', '拼布手提包',         '每個花色都是手工拼接，圖片僅供參考。尺寸：寬 30 × 高 25 cm',      1280.00,  1, 'ON_SALE'),
    ('肩背包', '棉麻斜背包（燕麥色）', '可調式背帶，最長 130 cm。尺寸：寬 24 × 高 18 × 底 6 cm',        750.00,  8, 'ON_SALE'),
    ('肩背包', '防潑水肩背包（黑）', '防潑水尼龍布，適合通勤。尺寸：寬 33 × 高 28 × 底 10 cm',         1150.00,  0, 'ON_SALE'),
    ('零錢包', '碎花零錢包',         '金屬口金開合。尺寸：寬 12 × 高 9 cm',                             320.00, 20, 'ON_SALE'),
    ('零錢包', '皮革拉鍊零錢包',     '植鞣牛皮，使用越久顏色越深。尺寸：寬 11 × 高 8 cm',               480.00,  5, 'OFF_SALE'),
    ('布料',   '日本棉麻布（碎花）', '寬幅 110 cm，以 50 cm 為一單位販售，購買 2 單位即為 1 米。',      180.00, 50, 'ON_SALE'),
    ('布料',   '厚磅帆布（原色）',   '10 號帆布，寬幅 150 cm，以 50 cm 為一單位販售。',                 150.00, 40, 'ON_SALE')
) AS v(category_name, name, description, price, stock, status)
JOIN categories c ON c.name = v.category_name
WHERE NOT EXISTS (
    SELECT 1 FROM products p WHERE p.category_id = c.id AND p.name = v.name
);

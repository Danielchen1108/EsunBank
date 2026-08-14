-- =============================================================================
-- EsunBank 社群媒體系統 — DML（測試種子資料）
-- =============================================================================
-- 需求：「資料庫的 DDL 和 DML 請存放在專案下的 \DB 資料夾內提供」
--
-- 執行前提：先執行 01_DDL.sql 與 02_DDL_stored_procedures.sql
-- =============================================================================

USE esunbank_social;


-- -----------------------------------------------------------------------------
-- 使用者
-- -----------------------------------------------------------------------------
-- 密碼皆為 BCrypt(cost=10) 雜湊值，明碼統一為：Test1234
-- 需求規格要求「密碼請加鹽(salt)並經雜湊(Hash)後儲存，避免明碼外洩」，
-- 故種子資料同樣不得含明碼。
--
-- 這些雜湊值以 BCrypt cost=10 實際產生並驗證通過，長度均為 60 字元，
-- 印證 password 欄位 VARCHAR(72) 的設計。
--
-- 注意：雜湊前綴為 $2b$。Spring Security 的 BCryptPasswordEncoder 預設產生 $2a$，
-- 但其 matches() 可驗證 $2a$ / $2b$ / $2y$ 三種前綴。若環境有疑慮，
-- 可用 BCryptPasswordEncoder().encode("Test1234") 重新產生後替換。
-- -----------------------------------------------------------------------------
INSERT INTO `user` (phone, user_name, email, password, cover_image, biography) VALUES
('0912345678', '陳大文', 'alice@example.com',
 '$2b$10$kfcSVqsS8zMDTNz16oG5qe/WuB1OGz2/2/pn9if.nyeIbmfXSljd2',
 NULL, '喜歡寫程式，也喜歡喝咖啡 ☕'),

('0922333444', '林小明', 'bob@example.com',
 '$2b$10$IjTrZhe/DV36MaYjXWagRu.VG.o/Ug5P9lFAPdM3F80iYQr10G/we',
 '/images/cover/bob.jpg', '後端工程師'),

('0933555666', '王美麗', 'carol@example.com',
 '$2b$10$0E7dswbYIaspZqKtuVMV0e3tpWP0cjrogy2bcjnmKwLYAOk0cqmZu',
 NULL, '');


-- -----------------------------------------------------------------------------
-- 發文
-- -----------------------------------------------------------------------------
-- 內容含中文與 emoji，用於驗證 utf8mb4 字元集。
--
-- created_at 在此刻意指定，這與 不衝突：
--   規範的是「正式流程中發佈時間由 DB 層產生」，也就是 sp_post_create
--   不接受呼叫端傳入時間——該 SP 至今仍沒有時間參數，這條規則沒有被放寬。
--   種子資料是驗證用的佈置，直接寫表；若全部沿用 CURRENT_TIMESTAMP，
--   四篇會落在同一秒，排序與相對時間顯示在畫面上就完全看不出效果。
--
-- 時間刻意讓相對時間的四種級距各出現一次：
--   12 分鐘前 → 「12 分鐘前」
--    5 小時前 → 「5 小時前」
--    2 天前   → 「2 天前」
--    9 天前   → 超過 7 天，改顯示絕對日期
-- 載入後畫面由上而下即為 emoji → 照片 → SP 注入 → 第一篇。
-- -----------------------------------------------------------------------------
INSERT INTO `post` (user_id, content, image, created_at) VALUES
(1, '大家好，這是我的第一篇發文！🎉', NULL, NOW() - INTERVAL 9 DAY),
(1, '今天研究了 MySQL 的 Stored Procedure，發現用 SP 存取資料庫不代表就免疫 SQL Injection——如果 SP 內部用 PREPARE 加 CONCAT 組動態 SQL，一樣會被注入。', NULL, NOW() - INTERVAL 2 DAY),
(2, '分享一張今天的照片 📷', '/images/post/bob-001.jpg', NOW() - INTERVAL 5 HOUR),
(3, '測試 emoji 與中文混排：🚀🔥✨ 台北天氣真好', NULL, NOW() - INTERVAL 12 MINUTE);


-- -----------------------------------------------------------------------------
-- 留言
-- -----------------------------------------------------------------------------
-- 每則留言的時間都必須晚於所屬發文，否則畫面上會出現
-- 「留言比它所在的發文還早」的矛盾。對照上一段的發文時間：
--   post 1（9 天前）→ 8 天前、7 天前
--   post 2（2 天前）→ 1 天前
--   post 3（5 小時前）→ 90 分鐘前
-- 留言在單一發文內維持由舊到新（sp_comment_list_visible 的排序），與發文列表相反：
-- 動態最新在上，但一則對話要從頭往下讀。
INSERT INTO `comment` (user_id, post_id, content, created_at) VALUES
(2, 1, '歡迎加入！👋',                              NOW() - INTERVAL 8 DAY),
(3, 1, '第一篇發文紀念',                            NOW() - INTERVAL 7 DAY),
(1, 3, '照片拍得不錯',                              NOW() - INTERVAL 90 MINUTE),
(3, 2, '這個點很重要，很多人以為用了 SP 就安全了',    NOW() - INTERVAL 1 DAY);


-- -----------------------------------------------------------------------------
-- 軟刪除驗證用資料
-- -----------------------------------------------------------------------------
-- 建立一筆帶留言的發文，再以 sp_post_delete 軟刪除，
-- 用於驗證：
--   1. 交易內 post 與 comment 同時被標記（Transaction 要求）
--   2. 軟刪除後該發文不出現在 sp_post_list 結果中
-- -----------------------------------------------------------------------------
INSERT INTO `post` (user_id, content, image) VALUES
(2, '這篇發文將被軟刪除，用於驗證連動標記與讀取過濾', NULL);

SET @deleted_post_id = LAST_INSERT_ID();

INSERT INTO `comment` (user_id, post_id, content) VALUES
(1, @deleted_post_id, '這則留言應隨發文一併被標記為已刪除'),
(3, @deleted_post_id, '這則也是');

CALL sp_post_delete(@deleted_post_id);


-- -----------------------------------------------------------------------------
-- 驗證查詢（執行後人工確認）
-- -----------------------------------------------------------------------------

-- 預期：4 筆（軟刪除那篇不應出現），且由新到舊：
--   emoji 混排 → 分享照片 → SP 注入 → 第一篇發文
CALL sp_post_list();

-- 預期：ORDER BY 由索引提供，Extra 不含 Using filesort
EXPLAIN SELECT p.post_id, p.user_id, u.user_name, p.content, p.image, p.created_at
  FROM `post` p
  JOIN `user` u ON u.user_id = p.user_id
 WHERE p.is_deleted = FALSE
 ORDER BY p.created_at DESC, p.post_id DESC;

-- 預期：該發文 is_deleted = 1，且其 2 則留言 is_deleted 皆為 1
SELECT p.post_id, p.is_deleted AS post_deleted,
       c.comment_id, c.is_deleted AS comment_deleted
  FROM `post` p
  LEFT JOIN `comment` c ON c.post_id = p.post_id
 WHERE p.post_id = @deleted_post_id;

-- 預期：3 筆使用者，password 皆為 60 字元的 BCrypt 雜湊，無明碼
SELECT user_id, phone, user_name, CHAR_LENGTH(password) AS pwd_len,
       LEFT(password, 4) AS pwd_prefix
  FROM `user`;

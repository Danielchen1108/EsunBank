-- =============================================================================
-- EsunBank 社群媒體系統 — DML（測試種子資料）
-- =============================================================================
-- 題目 §6：「資料庫的 DDL 和 DML 請存放在專案下的 \DB 資料夾內提供」
--
-- 執行前提：先執行 01_DDL.sql 與 02_DDL_stored_procedures.sql
-- =============================================================================

USE esunbank_social;


-- -----------------------------------------------------------------------------
-- 使用者
-- -----------------------------------------------------------------------------
-- 密碼皆為 BCrypt(cost=10) 雜湊值，明碼統一為：Test1234
-- 題目第 2 頁要求「密碼請加鹽(salt)並經雜湊(Hash)後儲存，避免明碼外洩」，
-- 故種子資料同樣不得含明碼。
--
-- 這些雜湊值以 BCrypt cost=10 實際產生並驗證通過，長度均為 60 字元，
-- 印證 password 欄位 VARCHAR(72) 的設計（AC-12）。
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
-- created_at 不指定，由 DB 層預設值 CURRENT_TIMESTAMP 產生（AC-14）。
-- 內容含中文與 emoji，用於驗證 utf8mb4 字元集（AC-11）。
-- -----------------------------------------------------------------------------
INSERT INTO `post` (user_id, content, image) VALUES
(1, '大家好，這是我的第一篇發文！🎉', NULL),
(1, '今天研究了 MySQL 的 Stored Procedure，發現用 SP 存取資料庫不代表就免疫 SQL Injection——如果 SP 內部用 PREPARE 加 CONCAT 組動態 SQL，一樣會被注入。', NULL),
(2, '分享一張今天的照片 📷', '/images/post/bob-001.jpg'),
(3, '測試 emoji 與中文混排：🚀🔥✨ 台北天氣真好', NULL);


-- -----------------------------------------------------------------------------
-- 留言
-- -----------------------------------------------------------------------------
INSERT INTO `comment` (user_id, post_id, content) VALUES
(2, 1, '歡迎加入！👋'),
(3, 1, '第一篇發文紀念'),
(1, 3, '照片拍得不錯'),
(3, 2, '這個點很重要，很多人以為用了 SP 就安全了');


-- -----------------------------------------------------------------------------
-- 軟刪除驗證用資料（ADR-004）
-- -----------------------------------------------------------------------------
-- 建立一筆帶留言的發文，再以 sp_post_delete 軟刪除，
-- 用於驗證：
--   1. 交易內 post 與 comment 同時被標記（題目 §6 Transaction 要求）
--   2. 軟刪除後該發文不出現在 sp_post_list 結果中（AC-18）
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

-- 預期：4 筆（軟刪除那篇不應出現）
CALL sp_post_list();

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

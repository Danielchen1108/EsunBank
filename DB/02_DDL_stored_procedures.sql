-- =============================================================================
-- EsunBank 社群媒體系統 — Stored Procedures
-- =============================================================================
-- 需求：「透過 Stored Procedure 存取資料庫」— 應用程式資料層不直接下 SQL。
-- 需求：「需防止 SQL Injection」
--
--   重要：使用 Stored Procedure 本身不等於免疫 SQL Injection。
--   若 SP 內以 PREPARE + CONCAT 組動態 SQL，一樣會被注入。
--   本檔所有 SP 皆為靜態語句，參數僅出現在 WHERE / VALUES 的綁定位置。
--   應用層須以 JDBC CallableStatement 綁定參數呼叫，不得字串拼接。
--
-- 執行順序：01_DDL.sql -> 02_DDL_stored_procedures.sql -> 03_DML.sql
-- =============================================================================

USE esunbank_social;

DROP PROCEDURE IF EXISTS sp_user_register;
DROP PROCEDURE IF EXISTS sp_user_find_by_phone;
DROP PROCEDURE IF EXISTS sp_post_create;
DROP PROCEDURE IF EXISTS sp_post_list;
DROP PROCEDURE IF EXISTS sp_post_find_by_id;
DROP PROCEDURE IF EXISTS sp_post_update;
DROP PROCEDURE IF EXISTS sp_post_delete;
DROP PROCEDURE IF EXISTS sp_comment_create;
DROP PROCEDURE IF EXISTS sp_comment_list_visible;

DELIMITER $$

-- -----------------------------------------------------------------------------
-- sp_user_register — 註冊使用者
-- -----------------------------------------------------------------------------
-- p_password 必須是應用層已用 BCryptPasswordEncoder 加鹽雜湊後的值，
-- 絕不接受明碼（需求規格：密碼請加鹽並經雜湊後儲存）。
-- 手機號碼重複時，由 uk_user_phone 唯一約束擋下，應用層轉譯為使用者可讀的錯誤。
-- -----------------------------------------------------------------------------
CREATE PROCEDURE sp_user_register(
    IN p_phone     CHAR(10),
    IN p_user_name VARCHAR(50),
    IN p_email     VARCHAR(255),
    IN p_password  VARCHAR(72),
    IN p_biography VARCHAR(500)
)
BEGIN
    INSERT INTO `user` (phone, user_name, email, password, biography)
    VALUES (p_phone, p_user_name, p_email, p_password, IFNULL(p_biography, ''));

    SELECT LAST_INSERT_ID() AS user_id;
END$$


-- -----------------------------------------------------------------------------
-- sp_user_find_by_phone — 依手機號碼取出使用者（登入）
-- -----------------------------------------------------------------------------
-- 回傳含 password 雜湊值，供應用層以 BCryptPasswordEncoder.matches() 比對。
-- 密碼為單向雜湊，無法還原明碼，故只能比對。
-- -----------------------------------------------------------------------------
CREATE PROCEDURE sp_user_find_by_phone(
    IN p_phone CHAR(10)
)
BEGIN
    SELECT user_id,
           phone,
           user_name,
           email,
           password,
           cover_image,
           biography
      FROM `user`
     WHERE phone = p_phone;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_create — 新增發文
-- -----------------------------------------------------------------------------
CREATE PROCEDURE sp_post_create(
    IN p_user_id BIGINT UNSIGNED,
    IN p_content VARCHAR(2000),
    IN p_image   VARCHAR(255)
)
BEGIN
    INSERT INTO `post` (user_id, content, image)
    VALUES (p_user_id, p_content, p_image);

    SELECT LAST_INSERT_ID() AS post_id;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_list — 列出所有發文
-- -----------------------------------------------------------------------------
-- 必帶 is_deleted = FALSE：軟刪除設計下遺漏此過濾，已刪除的發文會重新出現。
--
-- 排序由新到舊：
--   需求未定義發文的排序規則，補為「最新在最上面」——社群動態的通例。
--   排序寫在這裡而不是前端：留言的排序已經在
--   sp_comment_list_visible 裡，兩者若分屬不同層，規則就散在兩種語言中，遲早不同步。
--
-- 帶 post_id DESC 當決勝欄位：
--   created_at 為 DATETIME（秒精度），同一秒內的多篇發文只靠時間排序順序不確定。
--   post_id 是 AUTO_INCREMENT，與寫入順序一致，DESC 即「後寫入的在前」。
--   （與 sp_comment_list_visible 用 comment_id 收尾是同一個理由。）
--
-- 索引：idx_post_deleted_created (is_deleted, created_at, post_id) 正是為這條查詢而建，
--       排序直接由索引提供，不產生 filesort。欄位順序的推導見 01_DDL.sql。
-- -----------------------------------------------------------------------------
CREATE PROCEDURE sp_post_list()
BEGIN
    SELECT p.post_id,
           p.user_id,
           u.user_name,
           p.content,
           p.image,
           p.created_at
      FROM `post` p
      JOIN `user` u ON u.user_id = p.user_id
     WHERE p.is_deleted = FALSE
     ORDER BY p.created_at DESC, p.post_id DESC;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_find_by_id — 取單筆發文（編輯前檢查）
-- -----------------------------------------------------------------------------
CREATE PROCEDURE sp_post_find_by_id(
    IN p_post_id BIGINT UNSIGNED
)
BEGIN
    SELECT p.post_id,
           p.user_id,
           u.user_name,
           p.content,
           p.image,
           p.created_at
      FROM `post` p
      JOIN `user` u ON u.user_id = p.user_id
     WHERE p.post_id    = p_post_id
       AND p.is_deleted = FALSE;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_update — 編輯發文
-- -----------------------------------------------------------------------------
-- AND is_deleted = FALSE：防止編輯到已軟刪除的發文。
-- 回傳受影響列數，應用層據此判斷目標是否存在。
-- -----------------------------------------------------------------------------
CREATE PROCEDURE sp_post_update(
    IN p_post_id BIGINT UNSIGNED,
    IN p_content VARCHAR(2000),
    IN p_image   VARCHAR(255)
)
BEGIN
    UPDATE `post`
       SET content = p_content,
           image   = p_image
     WHERE post_id    = p_post_id
       AND is_deleted = FALSE;

    SELECT ROW_COUNT() AS affected_rows;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_delete — 軟刪除發文 + 連動軟刪除其留言
-- -----------------------------------------------------------------------------
--   需求：「需同時異動多個資料表時，請實作 Transaction，避免資料錯亂」
--
--   這是全案唯一的跨表寫入情境。
--   兩次 UPDATE 分屬 post 與 comment 兩表，必須原子完成——否則會出現
--   「發文已標記刪除但留言仍為未刪除」的資料錯亂，即需求所述情境。
--
--   設計要點：若只 UPDATE post 單表，刪除即退化為單表操作，
--   全案將不存在任何跨表寫入，需求的 Transaction 要求無處落地。
--   連動標記 comment 是保住該要求的關鍵。
-- -----------------------------------------------------------------------------
CREATE PROCEDURE sp_post_delete(
    IN p_post_id BIGINT UNSIGNED
)
BEGIN
    DECLARE v_affected INT DEFAULT 0;

    -- 任何 SQL 例外都回滾整筆交易，再把錯誤拋回應用層
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

        UPDATE `post`
           SET is_deleted = TRUE
         WHERE post_id    = p_post_id
           AND is_deleted = FALSE;

        SET v_affected = ROW_COUNT();

        UPDATE `comment`
           SET is_deleted = TRUE
         WHERE post_id    = p_post_id
           AND is_deleted = FALSE;

    COMMIT;

    SELECT v_affected AS affected_rows;
END$$


-- -----------------------------------------------------------------------------
-- sp_comment_create — 新增留言
-- -----------------------------------------------------------------------------
-- 先確認目標發文存在且未被軟刪除，否則留言會掛在已刪除的發文上。
--
--   已知限制：存在性檢查與 INSERT 沒有包在同一個交易內。
--   兩者之間若有人刪除了該發文，仍會寫入一則「未刪除的留言掛在已刪除的發文下」
--   的孤兒資料。讀取端的 sp_comment_list_visible 一併過濾 post.is_deleted，
--   使它不會顯示於畫面——但那是遮蔽，不是修復，資料庫裡的不一致仍會產生。
--   要真正修掉，需把檢查與寫入包進交易並對該列加鎖。
-- -----------------------------------------------------------------------------
CREATE PROCEDURE sp_comment_create(
    IN p_user_id BIGINT UNSIGNED,
    IN p_post_id BIGINT UNSIGNED,
    IN p_content VARCHAR(500)
)
BEGIN
    DECLARE v_post_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO v_post_exists
      FROM `post`
     WHERE post_id    = p_post_id
       AND is_deleted = FALSE;

    IF v_post_exists = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Post not found or has been deleted';
    END IF;

    INSERT INTO `comment` (user_id, post_id, content)
    VALUES (p_user_id, p_post_id, p_content);

    SELECT LAST_INSERT_ID() AS comment_id;
END$$


-- -----------------------------------------------------------------------------
-- sp_comment_list_visible — 列出所有可見留言（發文列表用）
-- -----------------------------------------------------------------------------
-- 一次取回全部可見留言，由業務層依 post_id 分組掛回各則發文。
--
--   為什麼不把留言 JOIN 進 sp_post_list：
--   那會讓回傳變成「發文欄位隨留言重複」的扁平列，映射複雜，
--   且已通過驗證的 sp_post_list 行為得跟著改動。
--   拆成兩支各司其職的 SP，總共兩次往返，也不會有 N+1。
--
--   為什麼同時過濾 comment 與 post 的 is_deleted：
--   只看 c.is_deleted 不夠。sp_comment_create 的存在性檢查與寫入未包在同一交易內，
--   競態下會產生「未刪除的留言掛在已刪除的發文下」的孤兒資料。JOIN post 一併過濾，使本 SP 的正確性不依賴呼叫端是否記得排除。
--
--   排序帶 comment_id 當決勝欄位：
--   created_at 是 DATETIME（秒精度），同一秒內的多則留言只靠時間排序順序不確定。
--
--   方向與 sp_post_list 相反是刻意的：發文由新到舊（動態的通例是最新在最上面），
--   單一發文內的留言由舊到新（一段對話要從頭往下讀）。
-- -----------------------------------------------------------------------------
CREATE PROCEDURE sp_comment_list_visible()
BEGIN
    SELECT c.comment_id,
           c.post_id,
           c.user_id,
           u.user_name,
           c.content,
           c.created_at
      FROM `comment` c
      JOIN `post` p ON p.post_id = c.post_id
      JOIN `user` u ON u.user_id = c.user_id
     WHERE c.is_deleted = FALSE
       AND p.is_deleted = FALSE
     ORDER BY c.post_id, c.created_at, c.comment_id;
END$$

DELIMITER ;

-- =============================================================================
-- EsunBank 社群媒體系統 — Stored Procedures
-- =============================================================================
-- 題目 §6：「透過 Stored Procedure 存取資料庫」— 應用程式資料層不直接下 SQL。
-- 題目 §6：「需防止 SQL Injection」
--
--   重要：使用 Stored Procedure 本身不等於免疫 SQL Injection。
--   若 SP 內以 PREPARE + CONCAT 組動態 SQL，一樣會被注入。
--   本檔所有 SP 皆為靜態語句，參數僅出現在 WHERE / VALUES 的綁定位置。
--   應用層須以 JDBC CallableStatement 綁定參數呼叫，不得字串拼接。
--
-- 對應設計文件：pipeline-artifacts/P02_TechDesign/F001-DB.md
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

DELIMITER $$

-- -----------------------------------------------------------------------------
-- sp_user_register — 註冊使用者（F002）
-- -----------------------------------------------------------------------------
-- p_password 必須是應用層已用 BCryptPasswordEncoder 加鹽雜湊後的值，
-- 絕不接受明碼（題目第 2 頁：密碼請加鹽並經雜湊後儲存）。
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
-- sp_user_find_by_phone — 依手機號碼取出使用者（F003 登入）
-- -----------------------------------------------------------------------------
-- 回傳含 password 雜湊值，供應用層以 BCryptPasswordEncoder.matches() 比對。
-- 密碼為單向雜湊，無法還原明碼，故只能比對（F003 AC-2）。
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
-- sp_post_create — 新增發文（F004）
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
-- sp_post_list — 列出所有發文（F004）
-- -----------------------------------------------------------------------------
-- 必帶 is_deleted = FALSE：軟刪除設計下遺漏此過濾，已刪除的發文會重新出現
-- （F001 AC-18）。
--
-- 未加 ORDER BY：題目未定義排序規則（NG-2 / F004 OQ-2），待 P03 API 規格決定後補上，
-- 屆時須一併評估是否為 post.created_at 建立索引。
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
     WHERE p.is_deleted = FALSE;
END$$


-- -----------------------------------------------------------------------------
-- sp_post_find_by_id — 取單筆發文（F004 編輯前檢查）
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
-- sp_post_update — 編輯發文（F004）
-- -----------------------------------------------------------------------------
-- AND is_deleted = FALSE：防止編輯到已軟刪除的發文（ADR-004 負面後果防範）。
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
-- sp_post_delete — 軟刪除發文 + 連動軟刪除其留言（F004）
-- -----------------------------------------------------------------------------
--   題目 §6：「需同時異動多個資料表時，請實作 Transaction，避免資料錯亂」
--
--   這是全案唯一的跨表寫入情境（F001 OQ-5）。
--   兩次 UPDATE 分屬 post 與 comment 兩表，必須原子完成——否則會出現
--   「發文已標記刪除但留言仍為未刪除」的資料錯亂，即題目所述情境。
--
--   設計要點：若只 UPDATE post 單表，刪除即退化為單表操作，
--   全案將不存在任何跨表寫入，題目 §6 的 Transaction 要求無處落地。
--   連動標記 comment 是保住該要求的關鍵（ADR-004）。
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
-- sp_comment_create — 新增留言（F005）
-- -----------------------------------------------------------------------------
-- 先確認目標發文存在且未被軟刪除，否則留言會掛在已刪除的發文上
-- （ADR-004 負面後果防範）。
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

DELIMITER ;

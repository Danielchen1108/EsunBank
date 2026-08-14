-- =============================================================================
-- EsunBank 社群媒體系統 — DDL
-- =============================================================================
-- 對應設計文件：pipeline-artifacts/P02_TechDesign/F001-DB.md
-- 資料庫：MySQL 8.0+（ADR-002）
-- 字元集：utf8mb4（F001 AC-11，社群內容需支援中文與 emoji）
--
-- 執行順序：01_DDL.sql -> 02_DDL_stored_procedures.sql -> 03_DML.sql
-- =============================================================================

DROP DATABASE IF EXISTS esunbank_social;

CREATE DATABASE esunbank_social
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE esunbank_social;


-- -----------------------------------------------------------------------------
-- user — 使用者
-- -----------------------------------------------------------------------------
-- 題目第 2 頁 User 表。phone 為題目未列但 §1「以手機號碼進行註冊與登入」
-- 所必需，依第 2 頁「請包含，但不限制僅能有以下」新增。
-- -----------------------------------------------------------------------------
CREATE TABLE `user` (
    user_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '使用者 ID',
    phone       CHAR(10)        NOT NULL                COMMENT '手機號碼，註冊與登入帳號。CHAR(10) 於 DB 層強制 10 碼（AC-10）',
    user_name   VARCHAR(50)     NOT NULL                COMMENT '使用者名稱',
    email       VARCHAR(255)    NOT NULL                COMMENT '使用者電子郵件',
    password    VARCHAR(72)     NOT NULL                COMMENT '密碼：BCrypt 加鹽雜湊後儲存。BCrypt 輸出固定 60 字元，取 72 留緩衝（AC-12）',
    cover_image VARCHAR(255)        NULL DEFAULT NULL   COMMENT '封面照片路徑。題目標示為非必要欄位',
    biography   VARCHAR(500)    NOT NULL DEFAULT ''     COMMENT '自我介紹。題目未標非必要故為 NOT NULL，給空字串預設避免註冊時強制填寫',

    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_phone (phone),

    -- CHAR(10) 只擋「超長」，不擋「過短」：'0912' 會被原樣存入而不報錯。
    -- 必須額外加 CHECK 才能真正在 DB 層強制恰好 10 碼（AC-10）。
    -- 僅驗長度，不驗開頭數字與國別碼（SCOPE-BOUNDARY.md owner 裁決）。
    CONSTRAINT chk_user_phone_length CHECK (CHAR_LENGTH(phone) = 10)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '使用者。無 is_deleted：題目功能清單無「刪除使用者」';


-- -----------------------------------------------------------------------------
-- post — 發文
-- -----------------------------------------------------------------------------
CREATE TABLE `post` (
    post_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT               COMMENT '發文 ID',
    user_id    BIGINT UNSIGNED NOT NULL                              COMMENT '發文者，FK to user',
    content    VARCHAR(2000)   NOT NULL                              COMMENT '發佈文章內容。長度上限依 ADR-005',
    image      VARCHAR(255)        NULL DEFAULT NULL                 COMMENT '圖片路徑。題目標示為非必要欄位',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '發佈時間，由 DB 層產生（AC-14）',
    is_deleted BOOLEAN         NOT NULL DEFAULT FALSE                COMMENT '軟刪除旗標（ADR-004）。題目未列，依「不限制僅能有以下」新增',

    PRIMARY KEY (post_id),

    -- 「列出所有發文」必帶 is_deleted = FALSE 過濾，為最高頻查詢（AC-15）
    KEY idx_post_deleted (is_deleted, post_id),

    CONSTRAINT fk_post_user
        FOREIGN KEY (user_id) REFERENCES `user` (user_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '發文。採軟刪除，資料列永不移除，故 FK 的 ON DELETE 不會觸發；RESTRICT 用於防止繞過 SP 直接刪除';


-- -----------------------------------------------------------------------------
-- comment — 留言
-- -----------------------------------------------------------------------------
CREATE TABLE `comment` (
    comment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT               COMMENT '留言 ID',
    user_id    BIGINT UNSIGNED NOT NULL                              COMMENT '留言者，FK to user',
    post_id    BIGINT UNSIGNED NOT NULL                              COMMENT '所屬發文，FK to post',
    content    VARCHAR(500)    NOT NULL                              COMMENT '留言內容。長度上限依 ADR-005',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '留言時間，由 DB 層產生（AC-14）',
    is_deleted BOOLEAN         NOT NULL DEFAULT FALSE                COMMENT '軟刪除旗標（ADR-004）。刪除發文時由 sp_post_delete 連動標記',

    PRIMARY KEY (comment_id),

    -- 兩條讀寫路徑共用這一個索引，欄位順序由「誰是等值條件」決定：
    --
    --   sp_post_delete       WHERE post_id = ? AND is_deleted = FALSE   兩者皆等值
    --   sp_comment_list_visible  WHERE is_deleted = FALSE               只有它是等值，
    --                            ORDER BY post_id, created_at, comment_id
    --
    -- 因此 is_deleted 必須放前導：列表查詢沒有 post_id 條件，
    -- 若把 post_id 放第一欄，前導欄位無約束，MySQL 會退回全表掃描加 filesort
    -- （實測 EXPLAIN 為 key: NULL / Using filesort）。
    -- is_deleted 在前時，後面的 post_id、created_at 天然就是排序順序，
    -- ORDER BY 直接由索引提供（實測 Extra: NULL，無 filesort）。
    --
    -- 刪除路徑不受影響：它對兩欄都是等值條件，順序不影響可用性。
    KEY idx_comment_deleted_post (is_deleted, post_id, created_at),

    CONSTRAINT fk_comment_user
        FOREIGN KEY (user_id) REFERENCES `user` (user_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_comment_post
        FOREIGN KEY (post_id) REFERENCES `post` (post_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '留言。採軟刪除';

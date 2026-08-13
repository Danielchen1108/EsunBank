package com.esunbank.social.data.repository;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.esunbank.social.common.exception.CommentTargetPostNotFoundException;

/**
 * 留言資料存取（資料層）。
 *
 * <p>依題目 §6「透過 Stored Procedure 存取資料庫」，本類別不撰寫任何 SQL 陳述式，
 * 僅以 {@link CallableStatement} 呼叫 {@code DB/02_DDL_stored_procedures.sql}
 * 中定義的 Stored Procedure。
 *
 * <p><b>防 SQL Injection（題目 §6）：</b>所有參數以 {@code setLong} / {@code setString}
 * 綁定，不進行字串拼接。搭配 SP 內部的靜態語句（不使用 {@code PREPARE} + {@code CONCAT}），
 * 兩端共同構成防護——僅使用 SP 而 SP 內拼接動態 SQL 並不免疫注入。
 *
 * <p><b>範圍：</b>僅有新增。題目 §4 原文只要求「使用者可以針對發文新增留言」，
 * 列出／編輯／刪除留言依 {@code SCOPE-BOUNDARY.md} 判定為 Out of Scope。
 */
@Repository
public class CommentRepository {

    /**
     * {@code sp_comment_create} 在目標發文不存在或已軟刪除時
     * 以 {@code SIGNAL SQLSTATE '45000'} 中斷。
     *
     * <p>45000 是 SQL 標準保留給使用者自訂例外的 SQLSTATE，Spring 沒有對應的
     * {@code DataAccessException} 子類別可接（不像唯一鍵衝突有 {@code DuplicateKeyException}），
     * 因此轉譯必須發生在拿得到原始 {@link SQLException} 的資料層，
     * 而非如 F002 那樣在業務層接 Spring 的抽象例外。
     *
     * <p>本類別只呼叫 {@code sp_comment_create}，而該 SP 全文僅有一處 {@code SIGNAL}，
     * 故此 SQLSTATE 在這裡語意唯一，無須再比對訊息字串。
     */
    private static final String SQLSTATE_POST_NOT_FOUND_SIGNAL = "45000";

    private final JdbcTemplate jdbcTemplate;

    public CommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 呼叫 {@code sp_comment_create} 新增留言。
     *
     * <p>目標發文是否存在且未被軟刪除，由 SP 內部檢查（{@code F001-DB.md}
     * §「軟刪除的讀取規則」）。放在 SP 而非應用層的理由：檢查與寫入在同一次
     * 資料庫往返內完成，兩者之間沒有其他連線可以搶先軟刪除該發文。
     *
     * @return 新增的 comment_id
     * @throws CommentTargetPostNotFoundException 目標發文不存在或已被軟刪除
     */
    public Long create(Long userId, Long postId, String content) {

        try {
            return jdbcTemplate.execute(
                    (java.sql.Connection connection) -> {
                        CallableStatement statement =
                                connection.prepareCall("{call sp_comment_create(?, ?, ?)}");
                        statement.setLong(1, userId);
                        statement.setLong(2, postId);
                        statement.setString(3, content);
                        return statement;
                    },
                    (CallableStatement statement) -> {
                        statement.execute();
                        try (ResultSet resultSet = statement.getResultSet()) {
                            if (resultSet != null && resultSet.next()) {
                                return resultSet.getLong("comment_id");
                            }
                        }
                        throw new IllegalStateException("sp_comment_create 未回傳 comment_id");
                    });

        } catch (DataAccessException e) {
            if (isPostNotFoundSignal(e)) {
                // 轉譯為領域例外，避免 SQLSTATE 這類資料層細節外洩至上層
                throw new CommentTargetPostNotFoundException(postId);
            }
            throw e;
        }
    }

    private boolean isPostNotFoundSignal(DataAccessException e) {
        return e.getMostSpecificCause() instanceof SQLException sqlException
                && SQLSTATE_POST_NOT_FOUND_SIGNAL.equals(sqlException.getSQLState());
    }
}

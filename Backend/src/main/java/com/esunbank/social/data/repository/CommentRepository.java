package com.esunbank.social.data.repository;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.esunbank.social.common.exception.CommentTargetPostNotFoundException;

/**
 * 留言資料存取（資料層）。
 *
 * <p>資料庫存取一律透過 Stored Procedure，本類別不撰寫任何 SQL 陳述式，
 * 僅以 {@link CallableStatement} 呼叫 {@code DB/02_DDL_stored_procedures.sql}
 * 中定義的 Stored Procedure。
 *
 * <p><b>防 SQL Injection：</b>所有參數以 {@code setLong} / {@code setString}
 * 綁定，不進行字串拼接。搭配 SP 內部的靜態語句（不使用 {@code PREPARE} + {@code CONCAT}），
 * 兩端共同構成防護——僅使用 SP 而 SP 內拼接動態 SQL 並不免疫注入。
 *
 * <p><b>範圍：</b>新增，以及供發文列表帶出留言的整批讀取（{@link #listVisible()}，
 * 屬 ——後來追加，早已寫明「若 決定列表帶出留言，
 * 讀取邏輯歸發文那一側。編輯與刪除留言不做。
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
     * 而非如 那樣在業務層接 Spring 的抽象例外。
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
     * <p>目標發文是否存在且未被軟刪除，由 SP 內部檢查。放在 SP 而非應用層的理由：省去一次資料庫往返，
     * 並讓「檢查 + 寫入」這組邏輯集中在一處，不會散落到呼叫端各自重做。
     *
     * <p><b>已知限制：</b>「同一次往返」不等於「同一個交易」。
     * {@code sp_comment_create} 沒有 {@code START TRANSACTION}，存在性檢查也沒有加鎖，
     * 在 autocommit 下檢查與寫入是兩筆各自提交的交易，兩者之間仍有競態窗口。
     * 若該窗口內目標發文被其他連線軟刪除，會產生一則未刪除的留言掛在已刪除的發文下。
     * 此限制已由 後來決定暫不修。
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

    /**
     * 呼叫 {@code sp_comment_list_visible} 一次取回所有可見留言。
     *
     * <p>不接 postId 參數：發文列表需要的是「全部發文各自的留言」，逐篇查會變成 N+1。
     * 整批取回後由業務層依 {@code postId} 分組，整個列表固定兩次資料庫往返。
     *
     * <p><b>可見 = 留言與其所屬發文皆未軟刪除。</b>SP 內 JOIN {@code post} 並同時過濾兩張表的
     * {@code is_deleted}。只過濾 {@code comment.is_deleted} 不夠——的競態
     * 會產生「未刪除的留言掛在已刪除的發文下」的孤兒資料，過去沒有讀取管道所以看不見，
     * 一旦加上讀取就會浮現。過濾寫在 SP 內，本層與業務層都不需要記得補這個條件。
     *
     * <p>結果已由 SP 依 {@code post_id, created_at, comment_id} 排序，本層原樣保留順序——
     * 留言是對話，順序本身就是資訊（對照 {@link PostRepository#findAll()} 刻意不排序）。
     */
    public List<CommentRow> listVisible() {
        return jdbcTemplate.execute(
                (java.sql.Connection connection) ->
                        connection.prepareCall("{call sp_comment_list_visible()}"),
                (CallableStatement statement) -> {
                    statement.execute();
                    List<CommentRow> comments = new ArrayList<>();
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (resultSet != null) {
                            while (resultSet.next()) {
                                comments.add(mapRow(resultSet));
                            }
                        }
                    }
                    return comments;
                });
    }

    /** 資料列 → 資料層型別（資料層職責，見 {@code data/package-info.java}）。 */
    private CommentRow mapRow(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");

        return new CommentRow(
                resultSet.getLong("comment_id"),
                resultSet.getLong("post_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("user_name"),
                resultSet.getString("content"),
                createdAt == null ? null : createdAt.toLocalDateTime());
    }

    private boolean isPostNotFoundSignal(DataAccessException e) {
        return e.getMostSpecificCause() instanceof SQLException sqlException
                && SQLSTATE_POST_NOT_FOUND_SIGNAL.equals(sqlException.getSQLState());
    }

    /**
     * 留言的資料列表示（資料層）。
     *
     * <p>與 {@link PostRepository.PostRow}、{@code UserRepository.UserCredentials} 同一做法：
     * 資料層定義自己的資料列型別，不回傳業務層的領域模型——
     * {@code data/package-info.java} 宣告資料層不得依賴業務層。
     *
     * <p><b>含 {@code postId}：</b>它是業務層分組時的鍵，必須從資料層帶上來。
     * 領域模型 {@code Comment} 則不含此欄——留言掛在哪則發文下，由巢狀結構本身表達。
     *
     * <p>不含 {@code is_deleted}：{@code sp_comment_list_visible} 已保證只回傳可見留言。
     */
    public record CommentRow(
            Long commentId,
            Long postId,
            Long userId,
            String userName,
            String content,
            java.time.LocalDateTime createdAt) {
    }
}

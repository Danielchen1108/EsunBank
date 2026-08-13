package com.esunbank.social.data.repository;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.esunbank.social.business.service.Post;

/**
 * 發文資料存取（資料層）。
 *
 * <p>依題目 §6「透過 Stored Procedure 存取資料庫」，本類別不撰寫任何 SQL 陳述式，
 * 僅以 {@link CallableStatement} 呼叫 {@code DB/02_DDL_stored_procedures.sql}
 * 中定義的 Stored Procedure。
 *
 * <p><b>防 SQL Injection（題目 §6）：</b>所有參數以 {@code setLong} / {@code setString} 綁定，
 * 不進行字串拼接。搭配 SP 內部的靜態語句（不使用 {@code PREPARE} + {@code CONCAT}），
 * 兩端共同構成防護——僅使用 SP 而 SP 內拼接動態 SQL 並不免疫注入。
 *
 * <p><b>軟刪除的讀取過濾（ADR-004）：</b>{@code sp_post_list} 與 {@code sp_post_find_by_id}
 * 都在 SP 內帶 {@code is_deleted = FALSE}。本層不得另外提供「查全部（含已刪除）」的方法——
 * 一旦存在，讀取端就有機會漏掉過濾條件，已刪除的發文便會重新出現。
 */
@Repository
public class PostRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 呼叫 {@code sp_post_create} 新增發文。
     *
     * @param userId 發文者，取自登入憑證而非請求內容（題目 §2）
     * @param image  圖片路徑，可為 null（題目第 2 頁標示 Image 為非必要欄位）
     * @return 新增的 post_id
     */
    public Long create(Long userId, String content, String image) {
        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> {
                    CallableStatement statement =
                            connection.prepareCall("{call sp_post_create(?, ?, ?)}");
                    statement.setLong(1, userId);
                    statement.setString(2, content);
                    statement.setString(3, image);
                    return statement;
                },
                (CallableStatement statement) -> {
                    statement.execute();
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (resultSet != null && resultSet.next()) {
                            return resultSet.getLong("post_id");
                        }
                    }
                    throw new IllegalStateException("sp_post_create 未回傳 post_id");
                });
    }

    /**
     * 呼叫 {@code sp_post_list} 列出所有未刪除的發文。
     *
     * <p>SP 內已帶 {@code is_deleted = FALSE}（ADR-004）。
     *
     * <p>結果順序未定義：{@code sp_post_list} 刻意不寫 ORDER BY——題目未定義排序規則
     * （`SCOPE-BOUNDARY.md` 列為 Out of Scope）。本層不自行補排序，避免實作出題目沒要求的行為。
     */
    public List<Post> findAll() {
        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> connection.prepareCall("{call sp_post_list()}"),
                (CallableStatement statement) -> {
                    statement.execute();
                    List<Post> posts = new ArrayList<>();
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (resultSet != null) {
                            while (resultSet.next()) {
                                posts.add(mapPost(resultSet));
                            }
                        }
                    }
                    return posts;
                });
    }

    /**
     * 呼叫 {@code sp_post_find_by_id} 取單筆未刪除的發文。
     *
     * <p>回 {@link Optional#empty()} 有兩種可能：發文不存在，或已被軟刪除。
     * 對呼叫端而言兩者等價——已刪除的發文不該再被看見或編輯（ADR-004）。
     */
    public Optional<Post> findById(Long postId) {
        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> {
                    CallableStatement statement =
                            connection.prepareCall("{call sp_post_find_by_id(?)}");
                    statement.setLong(1, postId);
                    return statement;
                },
                (CallableStatement statement) -> {
                    statement.execute();
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (resultSet != null && resultSet.next()) {
                            return Optional.of(mapPost(resultSet));
                        }
                    }
                    return Optional.<Post>empty();
                });
    }

    /**
     * 呼叫 {@code sp_post_update} 編輯發文。
     *
     * <p><b>回傳值不可作為「發文是否存在」的判斷依據。</b>MySQL 預設不帶
     * {@code CLIENT_FOUND_ROWS}，{@code ROW_COUNT()} 回報的是**實際變更**的列數：
     * 送出與原文完全相同的內容時會回 0，但發文確實存在。存在性請以
     * {@link #findById(Long)} 判斷（業務層即如此處理）。
     *
     * @return 實際變更的列數
     */
    public int update(Long postId, String content, String image) {
        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> {
                    CallableStatement statement =
                            connection.prepareCall("{call sp_post_update(?, ?, ?)}");
                    statement.setLong(1, postId);
                    statement.setString(2, content);
                    statement.setString(3, image);
                    return statement;
                },
                (CallableStatement statement) -> {
                    statement.execute();
                    return readAffectedRows(statement, "sp_post_update");
                });
    }

    /**
     * 呼叫 {@code sp_post_delete} 軟刪除發文，並連動軟刪除其留言。
     *
     * <p><b>題目 §6「需同時異動多個資料表時，請實作 Transaction」的落地點。</b>
     * SP 內以顯式 {@code START TRANSACTION} / {@code COMMIT} 包覆 {@code post} 與
     * {@code comment} 兩次 UPDATE，任一失敗即 {@code ROLLBACK} 並將錯誤拋回本層，
     * 避免出現「發文已標記刪除但留言仍為未刪除」的資料錯亂（ADR-004）。
     *
     * <p>交易寫在 SP 內而非本層或業務層的 {@code @Transactional}：SP 自帶
     * {@code START TRANSACTION}，外層若再開交易，MySQL 會在進入 SP 時隱式提交外層交易，
     * 反而使邊界不清。
     *
     * @return 被標記為已刪除的發文列數；0 表示發文不存在或先前已被刪除
     */
    public int softDelete(Long postId) {
        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> {
                    CallableStatement statement =
                            connection.prepareCall("{call sp_post_delete(?)}");
                    statement.setLong(1, postId);
                    return statement;
                },
                (CallableStatement statement) -> {
                    statement.execute();
                    return readAffectedRows(statement, "sp_post_delete");
                });
    }

    /** SP 以結果集回傳 {@code affected_rows}（MySQL 的 CALL 無法用 OUT 參數同時回傳結果集）。 */
    private int readAffectedRows(CallableStatement statement, String procedureName)
            throws SQLException {

        try (ResultSet resultSet = statement.getResultSet()) {
            if (resultSet != null && resultSet.next()) {
                return resultSet.getInt("affected_rows");
            }
        }
        throw new IllegalStateException(procedureName + " 未回傳 affected_rows");
    }

    /** 資料列 → 領域模型（資料層職責，見 {@code data/package-info.java}）。 */
    private Post mapPost(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");

        return new Post(
                resultSet.getLong("post_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("user_name"),
                resultSet.getString("content"),
                resultSet.getString("image"),
                createdAt == null ? null : createdAt.toLocalDateTime());
    }
}

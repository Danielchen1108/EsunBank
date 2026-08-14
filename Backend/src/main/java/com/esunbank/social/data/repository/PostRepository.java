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


/**
 * 發文資料存取（資料層）。
 *
 * <p>資料庫存取一律透過 Stored Procedure，本類別不撰寫任何 SQL 陳述式，
 * 僅以 {@link CallableStatement} 呼叫 {@code DB/02_DDL_stored_procedures.sql}
 * 中定義的 Stored Procedure。
 *
 * <p><b>防 SQL Injection：</b>所有參數以 {@code setLong} / {@code setString} 綁定，
 * 不進行字串拼接。搭配 SP 內部的靜態語句（不使用 {@code PREPARE} + {@code CONCAT}），
 * 兩端共同構成防護——僅使用 SP 而 SP 內拼接動態 SQL 並不免疫注入。
 *
 * <p><b>軟刪除的讀取過濾：</b>{@code sp_post_list} 與 {@code sp_post_find_by_id}
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
     * @param userId 發文者，取自登入憑證而非請求內容
     * @return 新增的 post_id
     */
    public Long create(Long userId, String content) {
        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> {
                    CallableStatement statement =
                            connection.prepareCall("{call sp_post_create(?, ?, ?)}");
                    statement.setLong(1, userId);
                    statement.setString(2, content);
                    // image：欄位保留於 schema（需求規格列有 Image，標示為非必要欄位），
                    // 但需求功能清單無上傳功能，故 API 不開放填寫。
                    // SP 的參數維持不變（DB 腳本不因應用層決定而改），此處固定綁 NULL。
                    statement.setNull(3, java.sql.Types.VARCHAR);
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
     * <p>SP 內已帶 {@code is_deleted = FALSE}。
     *
     * <p>結果順序未定義：{@code sp_post_list} 刻意不寫 ORDER BY——需求未定義排序規則。
     * 本層不自行補排序，避免實作出需求沒要求的行為。
     */
    public List<PostRow> findAll() {
        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> connection.prepareCall("{call sp_post_list()}"),
                (CallableStatement statement) -> {
                    statement.execute();
                    List<PostRow> posts = new ArrayList<>();
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (resultSet != null) {
                            while (resultSet.next()) {
                                posts.add(mapRow(resultSet));
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
     * 對呼叫端而言兩者等價——已刪除的發文不該再被看見或編輯。
     */
    public Optional<PostRow> findById(Long postId) {
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
                            return Optional.of(mapRow(resultSet));
                        }
                    }
                    return Optional.<PostRow>empty();
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
     * <p><b>image 固定傳 null：</b>理由同 {@link #create(Long, String)}。
     * {@code sp_post_update} 為整體取代語意（{@code SET image = p_image}），
     * 故編輯發文會一併把 image 清為 NULL——在沒有上傳功能的前提下，
     * 該欄唯一的來源是 {@code DB/03_DML.sql} 的種子資料。
     *
     * @return 實際變更的列數
     */
    public int update(Long postId, String content) {
        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> {
                    CallableStatement statement =
                            connection.prepareCall("{call sp_post_update(?, ?, ?)}");
                    statement.setLong(1, postId);
                    statement.setString(2, content);
                    statement.setNull(3, java.sql.Types.VARCHAR);
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
     * <p><b>需求「需同時異動多個資料表時，請實作 Transaction」的落地點。</b>
     * SP 內以顯式 {@code START TRANSACTION} / {@code COMMIT} 包覆 {@code post} 與
     * {@code comment} 兩次 UPDATE，任一失敗即 {@code ROLLBACK} 並將錯誤拋回本層，
     * 避免出現「發文已標記刪除但留言仍為未刪除」的資料錯亂。
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

    /**
     * 資料列 → 領域模型（資料層職責，見 {@code data/package-info.java}）。
     *
     * <p>SP 的結果集仍含 {@code image} 欄，此處刻意不映射——欄位保留於 schema
     * （需求規格），但無上傳功能故 API 不開放，
     * 帶到上層只會是一個永遠為 null 的欄位。
     */
    private PostRow mapRow(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");

        return new PostRow(
                resultSet.getLong("post_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("user_name"),
                resultSet.getString("content"),
                createdAt == null ? null : createdAt.toLocalDateTime());
    }

    /**
     * 發文的資料列表示（資料層）。
     *
     * <p><b>為什麼不直接回傳業務層的領域模型：</b>依 {@code data/package-info.java}
     * 宣告的依賴方向，資料層不得依賴業務層。原本 {@code PostRepository} 直接回傳
     * {@code business.service.Post}，形成資料層 → 業務層的反向依賴，與該宣告矛盾。
     *
     * <p>改由本層定義自己的資料列型別，再交給業務層映射為領域模型——
     * 與 {@code UserRepository.UserCredentials} 一致的做法。
     *
     * <p>不含 {@code is_deleted}：讀取用的 SP 已保證只回傳未刪除的資料。
     * 不含 {@code image}：欄位保留於 schema，但 API 不開放。
     */
    public record PostRow(
            Long postId,
            Long userId,
            String userName,
            String content,
            java.time.LocalDateTime createdAt) {
    }
}

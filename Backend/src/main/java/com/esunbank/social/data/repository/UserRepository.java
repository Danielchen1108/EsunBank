package com.esunbank.social.data.repository;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 使用者資料存取（資料層）。
 *
 * <p>依需求 §6「透過 Stored Procedure 存取資料庫」，本類別不撰寫任何 SQL 陳述式，
 * 僅以 {@link CallableStatement} 呼叫 {@code DB/02_DDL_stored_procedures.sql}
 * 中定義的 Stored Procedure。
 *
 * <p><b>防 SQL Injection（需求 §6）：</b>所有參數以 {@code setString} 綁定，
 * 不進行字串拼接。搭配 SP 內部的靜態語句（不使用 {@code PREPARE} + {@code CONCAT}），
 * 兩端共同構成防護——僅使用 SP 而 SP 內拼接動態 SQL 並不免疫注入。
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 呼叫 {@code sp_user_register} 新增使用者。
     *
     * @param passwordHash 已雜湊的密碼。本層不接受明碼——雜湊為業務層職責
     * @return 新增的 user_id
     * @throws org.springframework.dao.DuplicateKeyException 手機號碼已存在時，
     *         由 {@code uk_user_phone} 唯一約束觸發
     */
    public Long register(String phone, String userName, String email,
                         String passwordHash, String biography) {

        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> {
                    CallableStatement statement =
                            connection.prepareCall("{call sp_user_register(?, ?, ?, ?, ?)}");
                    statement.setString(1, phone);
                    statement.setString(2, userName);
                    statement.setString(3, email);
                    statement.setString(4, passwordHash);
                    statement.setString(5, biography);
                    return statement;
                },
                (CallableStatement statement) -> {
                    statement.execute();
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (resultSet != null && resultSet.next()) {
                            return resultSet.getLong("user_id");
                        }
                    }
                    throw new IllegalStateException("sp_user_register 未回傳 user_id");
                });
    }

    /**
     * 呼叫 {@code sp_user_find_by_phone} 依手機號碼取出登入所需資料（F003）。
     *
     * <p><b>回傳雜湊值而非在資料庫比對密碼：</b>BCrypt 的 salt 內嵌於雜湊值中，
     * 資料庫無從重算，比對只能在應用層以 {@code PasswordEncoder.matches()} 完成
     * （F003 AC-2）。
     *
     * <p>SP 另回傳 {@code user_name}、{@code email}、{@code cover_image}、
     * {@code biography}，此處刻意不取——登入只需要判斷「是誰」與「密碼對不對」，
     * 多讀的 PII 只會增加外洩面。
     *
     * @return 該手機號碼對應的帳號；查無此號碼時為空
     */
    public Optional<UserCredentials> findByPhone(String phone) {

        return jdbcTemplate.execute(
                (java.sql.Connection connection) -> {
                    CallableStatement statement =
                            connection.prepareCall("{call sp_user_find_by_phone(?)}");
                    statement.setString(1, phone);
                    return statement;
                },
                (CallableStatement statement) -> {
                    statement.execute();
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (resultSet != null && resultSet.next()) {
                            return Optional.of(new UserCredentials(
                                    resultSet.getLong("user_id"),
                                    resultSet.getString("phone"),
                                    resultSet.getString("user_name"),
                                    resultSet.getString("password")));
                        }
                    }
                    return Optional.empty();
                });
    }

    /**
     * 登入比對所需的帳號資料。
     *
     * <p>定義於資料層內部而非獨立檔案：這是本 repository 的查詢結果型別，
     * 與其一同閱讀較清楚。**不可上移至業務層**——資料層不得反向依賴業務層
     * （見 {@code data/package-info.java} 的依賴方向限制）。
     *
     * <p>{@code userName} 並非比對所需，而是登入成功後要回給前端顯示「目前登入者」用。
     * {@code sp_user_find_by_phone} 本來就會選出這一欄，多帶回來不需額外查詢。
     *
     * @param passwordHash BCrypt 雜湊值。單向不可還原，只能以
     *                     {@code PasswordEncoder.matches()} 比對
     */
    public record UserCredentials(Long userId, String phone, String userName, String passwordHash) {
    }
}

package com.esunbank.social.data.repository;

import java.sql.CallableStatement;
import java.sql.ResultSet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 使用者資料存取（資料層）。
 *
 * <p>依題目 §6「透過 Stored Procedure 存取資料庫」，本類別不撰寫任何 SQL 陳述式，
 * 僅以 {@link CallableStatement} 呼叫 {@code DB/02_DDL_stored_procedures.sql}
 * 中定義的 Stored Procedure。
 *
 * <p><b>防 SQL Injection（題目 §6）：</b>所有參數以 {@code setString} 綁定，
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
}

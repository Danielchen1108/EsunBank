package com.esunbank.social.data.repository;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

/**
 * 資料庫連線檢查（資料層）。
 *
 * <p>唯一直接持有 {@link DataSource} 的地方。連線本身是資料層的關注點，
 * 讓展示層或業務層取得 {@code DataSource} 等於把持久化細節洩漏出去，
 * 違反 {@code data/package-info.java} 宣告的分層職責（題目 §5）。
 *
 * <p>本類別不呼叫 Stored Procedure——它檢查的是連線是否可用，
 * 而非存取任何業務資料，故不適用「透過 SP 存取資料庫」的規定（題目 §6）。
 */
@Repository
public class HealthRepository {

    /** 連線驗證的等待秒數。取小值：健康檢查應快速回答，而非卡住等待。 */
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public HealthRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 資料庫是否可連線。
     *
     * <p>不拋出例外——健康檢查的用途就是回報狀態，連不上是它要回答的答案之一，
     * 不是需要往上拋的錯誤。
     *
     * @return 可連線時為 {@code null}，否則為失敗原因（例外類別名稱）
     */
    public String checkConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS) ? null : "連線無效";
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }
}

/**
 * 資料層（Data Layer）。
 *
 * <p>對應題目 §5「後端依照需求設計展示層、業務層、資料層以及共用層」。
 *
 * <p>職責：
 * <ul>
 *   <li>透過 Stored Procedure 存取資料庫（題目 §6）</li>
 *   <li>資料列與領域模型之間的映射</li>
 * </ul>
 *
 * <p><b>重要限制：</b>本層不得直接撰寫 SQL 字串。所有資料庫存取一律呼叫
 * {@code DB/02_DDL_stored_procedures.sql} 中定義的 Stored Procedure，
 * 並以 {@code CallableStatement} 綁定參數，不得字串拼接（題目 §6 防 SQL Injection）。
 *
 * <p>設計說明：因題目要求透過 Stored Procedure 存取，本專案使用 Spring JDBC
 * （{@code JdbcTemplate} + {@code CallableStatement}）而非 JPA。
 *
 * <p>選 {@code JdbcTemplate} 而非 {@code SimpleJdbcCall}：後者會在首次呼叫時查詢
 * 資料庫的中繼資料來推導參數，多一次往返且行為不夠顯眼；直接寫 {@code CallableStatement}
 * 能讓「哪個參數綁到第幾個位置」在程式碼中一眼可見，這正是防 SQL Injection 的關鍵所在。
 */
package com.esunbank.social.data;

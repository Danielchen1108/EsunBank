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
 * （{@code JdbcClient} / {@code SimpleJdbcCall}）而非 JPA。
 */
package com.esunbank.social.data;

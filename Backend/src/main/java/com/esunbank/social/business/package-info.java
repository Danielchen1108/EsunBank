/**
 * 業務層（Business Layer）。
 *
 * <p>的分層設計：展示層、業務層、資料層、共用層。
 *
 * <p>職責：
 * <ul>
 *   <li>業務規則與流程編排</li>
 *   <li>交易邊界的宣告（需同時異動多個資料表時實作 Transaction）</li>
 * </ul>
 *
 * <p>限制：不得依賴 HTTP 相關型別（{@code HttpServletRequest}、{@code ResponseEntity} 等）。
 * 僅能呼叫 {@code data} 層。
 */
package com.esunbank.social.business;

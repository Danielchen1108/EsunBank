/**
 * 展示層（Presentation Layer）。
 *
 * <p>的分層設計：展示層、業務層、資料層、共用層。
 *
 * <p>職責：
 * <ul>
 *   <li>接收 HTTP 請求，回傳 RESTful 回應</li>
 *   <li>請求參數驗證（Bean Validation）</li>
 *   <li>DTO 與領域模型之間的轉換</li>
 * </ul>
 *
 * <p>限制：不得包含業務規則，不得直接存取資料層。僅能呼叫 {@code business} 層。
 */
package com.esunbank.social.presentation;

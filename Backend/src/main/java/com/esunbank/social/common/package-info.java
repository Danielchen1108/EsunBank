/**
 * 共用層（Common Layer）。
 *
 * <p>對應題目 §5「後端依照需求設計展示層、業務層、資料層以及共用層」。
 *
 * <p>職責：跨層共用的基礎設施。
 * <ul>
 *   <li>{@code config} — Spring 設定（Security、資料來源等）</li>
 *   <li>{@code security} — 身分驗證機制（題目 §2；見 ADR-003 Spring Security + JWT）</li>
 *   <li>{@code exception} — 全域例外處理與統一錯誤回應</li>
 * </ul>
 *
 * <p>限制：不得依賴 {@code presentation}、{@code business}、{@code data} 三層。
 * 依賴方向為單向：其餘三層皆可依賴共用層。
 */
package com.esunbank.social.common;

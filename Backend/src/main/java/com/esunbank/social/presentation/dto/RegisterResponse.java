package com.esunbank.social.presentation.dto;

/**
 * 註冊成功回應。
 *
 * <p>僅回傳 userId。不回傳完整使用者資料，避免不必要地暴露 PII；
 * 也不自動簽發登入憑證——題目未要求註冊後自動登入
 * （見 {@code F002-REQ.md} Non-goals）。
 */
public record RegisterResponse(Long userId) {
}

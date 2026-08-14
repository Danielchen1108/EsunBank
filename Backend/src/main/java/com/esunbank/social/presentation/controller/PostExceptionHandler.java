package com.esunbank.social.presentation.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.esunbank.social.business.service.PostNotFoundException;

/**
 * 發文端點的例外處理（展示層）。
 *
 * <p>僅套用於 {@link PostController}：與 正同時開發，各自的例外由各自處理，
 * 限定範圍可避免共用的例外處理器成為多方修改的衝突點。
 * 參數驗證等通用錯誤仍由共用層的 {@code GlobalExceptionHandler} 處理。
 *
 * <p>回應不包含堆疊追蹤或資料庫訊息——避免洩漏內部結構。
 */
@RestControllerAdvice(assignableTypes = PostController.class)
public class PostExceptionHandler {

    /**
     * 找不到目標發文。
     *
     * <p>回 404 Not Found。已被軟刪除的發文也走這條路徑——
     * 對呼叫端而言「已刪除」與「不存在」等價，回應也不應洩漏該筆曾經存在。
     */
    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePostNotFound(PostNotFoundException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "找不到發文或該發文已被刪除");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}

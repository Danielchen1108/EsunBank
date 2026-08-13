package com.esunbank.social.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全域例外處理（共用層）。
 *
 * <p>集中把例外轉為統一格式的錯誤回應，使各控制器不必重複處理。
 *
 * <p>回應不包含堆疊追蹤或資料庫訊息——避免洩漏內部結構。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 請求參數驗證失敗（如手機號碼非 10 碼）。
     *
     * <p>逐欄回報，使前端能標示出錯的欄位。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "請求參數有誤");
        body.put("errors", errors);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 手機號碼已被註冊。
     *
     * <p>回 409 Conflict 而非 400——請求格式正確，衝突來自資源目前的狀態。
     */
    @ExceptionHandler(DuplicatePhoneException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePhone(DuplicatePhoneException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "此手機號碼已被註冊");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}

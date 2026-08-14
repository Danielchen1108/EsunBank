package com.esunbank.social.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 留言相關例外處理（共用層）。
 *
 * <p>獨立於 {@link GlobalExceptionHandler} 的理由：留言功能與登入驗證、發文
 * 為並行開發，各自新增獨立的 advice 可避免多人同時改動同一個檔案。
 * Spring 允許多個 {@code @RestControllerAdvice} 並存，各自宣告要接的例外型別。
 *
 * <p>共通的驗證失敗（400）仍由 {@link GlobalExceptionHandler} 處理，
 * 錯誤回應格式因而一致。
 */
@RestControllerAdvice
public class CommentExceptionHandler {

    /**
     * 留言的目標發文不存在或已被軟刪除。
     *
     * <p><b>回 404 Not Found 的理由</b>（需求未定義，{@code F005-REQ.md} OQ-1）：
     * {@code postId} 位於 URI 路徑 {@code /api/posts/{postId}/comments} 中，
     * 指的是父資源本身。父資源不存在時，REST 慣例即為 404。
     *
     * <p>不選其他狀態碼的理由：
     * <ul>
     *   <li>非 400——請求語法與欄位皆正確，問題在於路徑指向的資源</li>
     *   <li>非 409——這不是狀態衝突，目標根本不可定址</li>
     * </ul>
     *
     * <p>訊息不區分「從未存在」與「已軟刪除」，見
     * {@link CommentTargetPostNotFoundException} 的說明。
     */
    @ExceptionHandler(CommentTargetPostNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTargetPostNotFound(
            CommentTargetPostNotFoundException e) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "發文不存在或已被刪除");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}

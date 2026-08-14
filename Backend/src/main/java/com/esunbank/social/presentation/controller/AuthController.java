package com.esunbank.social.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.esunbank.social.business.service.LoginCommand;
import com.esunbank.social.business.service.LoginResult;
import com.esunbank.social.business.service.RegisterCommand;
import com.esunbank.social.business.service.UserService;
import com.esunbank.social.presentation.dto.LoginRequest;
import com.esunbank.social.presentation.dto.LoginResponse;
import com.esunbank.social.presentation.dto.RegisterRequest;
import com.esunbank.social.presentation.dto.RegisterResponse;

import jakarta.validation.Valid;

/**
 * 身分相關端點（展示層）。
 *
 * <p>對應需求 §1 註冊功能與 §2 登入驗證功能。
 *
 * <p>本控制器的端點免驗證，須列於 {@code SecurityConfig} 白名單——
 * 否則 deny-by-default 會擋成 401。
 *
 * <p>本層不含業務規則：帳密比對與憑證簽發皆在 {@code UserService}，
 * 此處只負責 DTO 轉換與 HTTP 狀態碼的選擇。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 註冊帳號。
     *
     * <p>回 201 Created 而非 200——建立了新資源。
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userService.register(new RegisterCommand(
                request.phone(),
                request.userName(),
                request.email(),
                request.password(),
                request.biography()));

        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(userId));
    }

    /**
     * 登入（需求 §2）。
     *
     * <p>回 200 OK 而非 201 Created——登入沒有建立任何資源，
     * 只是取得一張證明身分的憑證。
     *
     * <p>登入失敗回 401，由 {@code GlobalExceptionHandler} 統一處理
     * {@code InvalidCredentialsException}，理由見該處註解。
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = userService.login(new LoginCommand(request.phone(), request.password()));

        return ResponseEntity.ok(new LoginResponse(result.userId(), result.userName(), result.token()));
    }
}

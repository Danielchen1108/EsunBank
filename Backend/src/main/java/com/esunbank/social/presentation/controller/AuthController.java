package com.esunbank.social.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.esunbank.social.business.service.RegisterCommand;
import com.esunbank.social.business.service.UserService;
import com.esunbank.social.presentation.dto.RegisterRequest;
import com.esunbank.social.presentation.dto.RegisterResponse;

import jakarta.validation.Valid;

/**
 * 身分相關端點（展示層）。
 *
 * <p>對應題目 §1 註冊功能。登入端點將於 F003 加入本類別。
 *
 * <p>本控制器的端點免驗證，須列於 {@code SecurityConfig} 白名單——
 * 否則 deny-by-default 會擋成 401。
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
}

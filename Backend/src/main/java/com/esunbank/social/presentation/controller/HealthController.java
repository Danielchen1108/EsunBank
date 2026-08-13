package com.esunbank.social.presentation.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.esunbank.social.business.service.HealthService;

/**
 * 健康檢查（展示層）。
 *
 * <p>回報題目 §5 三層式架構中 Application Server 與資料庫之間是否連通。
 *
 * <p>本控制器不持有 {@code DataSource}——依 {@code presentation/package-info.java}
 * 的宣告，展示層不得直接存取資料層。連線檢查經由
 * {@link HealthService} → {@code HealthRepository} 逐層下達。
 *
 * <p>此端點在 {@code SecurityConfig} 白名單內（免驗證）：它只回報連通與否，
 * 不含任何使用者資料。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "application", healthService.applicationStatus(),
                "database", healthService.databaseStatus()));
    }
}

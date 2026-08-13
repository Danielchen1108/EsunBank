package com.esunbank.social.presentation.controller;

import java.sql.Connection;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康檢查（展示層）。
 *
 * <p>R101 專案骨架的驗證端點：確認應用程式可啟動，且能連上 MySQL。
 *
 * <p>非題目要求的功能，僅用於骨架階段驗證三層式架構（題目 §5）中
 * Application Server 與資料庫之間確實連通。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        String database;
        try (Connection connection = dataSource.getConnection()) {
            database = connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            database = "DOWN: " + e.getClass().getSimpleName();
        }
        return ResponseEntity.ok(Map.of(
                "application", "UP",
                "database", database));
    }
}

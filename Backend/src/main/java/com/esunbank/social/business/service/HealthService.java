package com.esunbank.social.business.service;

import org.springframework.stereotype.Service;

import com.esunbank.social.data.repository.HealthRepository;

/**
 * 系統健康狀態（業務層）。
 *
 * <p>對應題目 §5 的三層式架構：回報 Application Server 與資料庫之間是否連通。
 *
 * <p>本服務不含業務規則，僅編排——但仍保留這一層，因為
 * {@code presentation/package-info.java} 明文禁止展示層直接存取資料層。
 * 為了少一個類別而讓 Controller 直接注入 {@code DataSource}，
 * 破壞的是整個四層分層的可信度：一處例外之後，就沒有理由拒絕下一處。
 */
@Service
public class HealthService {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    private final HealthRepository healthRepository;

    public HealthService(HealthRepository healthRepository) {
        this.healthRepository = healthRepository;
    }

    /**
     * 資料庫狀態。
     *
     * @return {@code "UP"}，或 {@code "DOWN: 原因"}
     */
    public String databaseStatus() {
        String failure = healthRepository.checkConnection();
        return failure == null ? STATUS_UP : STATUS_DOWN + ": " + failure;
    }

    /**
     * 應用程式狀態。
     *
     * <p>能執行到這裡就代表應用程式活著，故恆為 {@code "UP"}——
     * 這個欄位的意義在於讓呼叫端區分「應用程式掛了（連不上）」與
     * 「應用程式活著但資料庫掛了」。
     */
    public String applicationStatus() {
        return STATUS_UP;
    }
}

package com.esunbank.social;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 應用程式啟動測試。
 *
 * <p>驗證 Spring 應用程式脈絡可正常載入——即四層分層的 Bean 佈線無誤。
 *
 * <p>注意：本測試會嘗試建立資料來源連線，需先依 {@code DB/} 下的腳本建立資料庫。
 */
@SpringBootTest
class SocialApplicationTests {

    @Test
    void contextLoads() {
    }

}

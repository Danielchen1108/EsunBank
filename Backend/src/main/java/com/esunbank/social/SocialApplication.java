package com.esunbank.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 社群媒體系統 — 應用程式進入點。
 *
 * <p>Application Server 進入點（三層式架構）。
 *
 * <p>後端分為四層，各層職責見對應套件的 {@code package-info.java}：
 * <ul>
 *   <li>{@code presentation} — 展示層</li>
 *   <li>{@code business} — 業務層</li>
 *   <li>{@code data} — 資料層</li>
 *   <li>{@code common} — 共用層</li>
 * </ul>
 */
@SpringBootApplication
public class SocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialApplication.class, args);
    }

}

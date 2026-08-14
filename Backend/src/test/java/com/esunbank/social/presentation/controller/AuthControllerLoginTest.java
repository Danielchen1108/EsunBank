package com.esunbank.social.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.esunbank.social.business.service.LoginCommand;
import com.esunbank.social.business.service.LoginResult;
import com.esunbank.social.business.service.UserService;
import com.esunbank.social.common.config.SecurityConfig;
import com.esunbank.social.common.exception.InvalidCredentialsException;

/**
 * 登入端點。
 *
 * <p>匯入 {@link SecurityConfig} 一併驗證：登入端點確實在白名單內。
 * 若未加入白名單，deny-by-default 會使登入本身需要先登入——死結。
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static final String VALID_BODY = """
            {
              "phone": "0912345678",
              "password": "Test1234"
            }
            """;

    @Test
    @DisplayName("登入成功回 200 與憑證")
    void logsInSuccessfully() throws Exception {
        when(userService.login(any(LoginCommand.class)))
                .thenReturn(new LoginResult(7L, "陳大文", "issued.jwt.token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.token").value("issued.jwt.token"));
    }

    @Test
    @DisplayName("登入端點免驗證——未帶憑證仍可存取，不應回 401")
    void loginEndpointIsPublic() throws Exception {
        when(userService.login(any(LoginCommand.class)))
                .thenReturn(new LoginResult(7L, "陳大文", "issued.jwt.token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("密碼錯誤回 401")
    void rejectsWrongPassword() throws Exception {
        when(userService.login(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("手機號碼或密碼錯誤"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("手機號碼不存在時回相同的 401 與訊息——不透露帳號是否存在")
    void unknownPhoneIsIndistinguishableFromWrongPassword() throws Exception {
        when(userService.login(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("0912345678", "0900000000")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("手機號碼或密碼錯誤"));
    }

    @Test
    @DisplayName("缺少必填欄位回 400 並指出欄位")
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "phone": "0912345678" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("SQL Injection 字串當作一般帳號處理，交由資料層參數綁定")
    void treatsInjectionAttemptAsOrdinaryInput() throws Exception {
        when(userService.login(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());

        // 刻意用 10 碼以內的注入字串，才會真的走到資料層而非被長度驗證擋下
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "phone": "' OR 1=1", "password": "'; DROP TABLE `user`; --" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("手機號碼超過 10 碼回 400——避免傳入 CHAR(10) 參數而由資料庫報錯")
    void rejectsPhoneLongerThanColumn() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "phone": "0912345678'; DROP TABLE `user`; --", "password": "x" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists());
    }
}

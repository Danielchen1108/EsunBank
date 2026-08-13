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

import com.esunbank.social.business.service.RegisterCommand;
import com.esunbank.social.business.service.UserService;
import com.esunbank.social.common.config.SecurityConfig;
import com.esunbank.social.common.exception.DuplicatePhoneException;

/**
 * 註冊端點（F002 AC-1、AC-2、AC-4）。
 *
 * <p>匯入 {@link SecurityConfig} 一併驗證：註冊端點確實在白名單內。
 * 若未加入白名單，deny-by-default 會使本測試回 401 而非 201。
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static final String VALID_BODY = """
            {
              "phone": "0912345678",
              "userName": "陳大文",
              "email": "alice@example.com",
              "password": "Test1234",
              "biography": "喜歡寫程式"
            }
            """;

    @Test
    @DisplayName("註冊成功回 201 與 userId")
    void registersSuccessfully() throws Exception {
        when(userService.register(any(RegisterCommand.class))).thenReturn(4L);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(4));
    }

    @Test
    @DisplayName("註冊端點免驗證——未帶憑證仍可存取，不應回 401")
    void registerEndpointIsPublic() throws Exception {
        when(userService.register(any(RegisterCommand.class))).thenReturn(4L);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("手機號碼非 10 碼回 400 並指出欄位")
    void rejectsInvalidPhoneLength() throws Exception {
        String body = VALID_BODY.replace("0912345678", "091234567");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    @DisplayName("手機號碼已註冊回 409")
    void rejectsDuplicatePhone() throws Exception {
        when(userService.register(any(RegisterCommand.class)))
                .thenThrow(new DuplicatePhoneException("0912345678"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("XSS 輸入被當作純文字接收，不在寫入時改寫")
    void storesUserInputVerbatim() throws Exception {
        String body = VALID_BODY.replace("陳大文", "<script>alert(1)</script>");
        when(userService.register(any(RegisterCommand.class))).thenReturn(4L);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // XSS 的防護位置在輸出端（Vue 插值自動跳脫），不在寫入端改寫使用者資料。
        // 見 F002-API.md § 安全考量。
    }
}

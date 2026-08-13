package com.esunbank.social.business.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.esunbank.social.common.exception.DuplicatePhoneException;
import com.esunbank.social.data.repository.UserRepository;

/**
 * 使用者業務邏輯（業務層）。
 *
 * <p>對應題目 §1 註冊功能。
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 註冊使用者。
     *
     * <p><b>密碼處理（題目第 2 頁）：</b>「密碼請加鹽(salt)並經雜湊(Hash)後儲存，
     * 避免明碼外洩」。{@link PasswordEncoder} 的實作為 BCrypt，每次編碼自動產生
     * 隨機 salt 並內嵌於輸出，因此無須另建 salt 欄位。
     * <b>明碼在此結束，不會流入資料層。</b>
     *
     * <p>未使用 {@code @Transactional}：僅異動 {@code user} 單表，
     * 未觸發題目 §6「同時異動多個資料表」的條件（見 {@code F002-REQ.md} OQ-3）。
     */
    public Long register(RegisterCommand command) {
        String passwordHash = passwordEncoder.encode(command.password());
        String biography = command.biography() == null ? "" : command.biography();

        try {
            return userRepository.register(
                    command.phone(),
                    command.userName(),
                    command.email(),
                    passwordHash,
                    biography);
        } catch (DuplicateKeyException e) {
            // uk_user_phone 唯一約束——轉譯為領域例外，避免資料層細節外洩至展示層
            throw new DuplicatePhoneException(command.phone());
        }
    }
}

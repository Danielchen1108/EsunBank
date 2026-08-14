package com.esunbank.social.business.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.esunbank.social.common.exception.DuplicatePhoneException;
import com.esunbank.social.common.exception.InvalidCredentialsException;
import com.esunbank.social.common.security.AuthenticatedUser;
import com.esunbank.social.common.security.JwtTokenService;
import com.esunbank.social.data.repository.UserRepository;

/**
 * 使用者業務邏輯（業務層）。
 *
 * <p>對應註冊與登入驗證功能。兩者共用同一個服務：
 * 都以手機號碼為帳號、都圍繞密碼雜湊，拆開反而讓「加密與比對必須成對」這件事變得不明顯。
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 註冊使用者。
     *
     * <p><b>密碼處理（需求規格）：</b>「密碼請加鹽(salt)並經雜湊(Hash)後儲存，
     * 避免明碼外洩」。{@link PasswordEncoder} 的實作為 BCrypt，每次編碼自動產生
     * 隨機 salt 並內嵌於輸出，因此無須另建 salt 欄位。
     * <b>明碼在此結束，不會流入資料層。</b>
     *
     * <p>未使用 {@code @Transactional}：僅異動 {@code user} 單表，
     * 未觸發需求「同時異動多個資料表」的條件。
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

    /**
     * 登入。
     *
     * <p><b>密碼比對方式：</b>資料庫存的是 BCrypt 雜湊值，為單向函式，
     * 無法還原成明碼。故驗證是「把使用者輸入的明碼以同一組 salt 重新雜湊後比較」——
     * 這正是 {@link PasswordEncoder#matches(CharSequence, String)} 的作用，
     * salt 由雜湊值本身攜帶。**任何「解密後比字串」的寫法都是錯的。**
     *
     * <p><b>兩種失敗共用同一個例外：</b>查無此手機號碼與密碼不符都拋
     * {@link InvalidCredentialsException}，使登入端點無法被用來探測帳號是否存在。
     *
     * <p>驗證通過後簽發 JWT。<b>不設有效期</b>——需求未提及，後來決定不實作。
     * 同理不提供更新與登出。
     *
     * <p>未使用 {@code @Transactional}：純唯讀單表查詢，未觸發需求
     * 「同時異動多個資料表」的條件，因此不需要交易。
     */
    public LoginResult login(LoginCommand command) {
        UserRepository.UserCredentials credentials = userRepository.findByPhone(command.phone())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(command.password(), credentials.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtTokenService.issue(
                new AuthenticatedUser(credentials.userId(), credentials.phone()));

        return new LoginResult(credentials.userId(), credentials.userName(), token);
    }
}

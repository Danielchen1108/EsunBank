package com.esunbank.social.business.service;

import org.springframework.stereotype.Service;

import com.esunbank.social.data.repository.CommentRepository;

/**
 * 留言業務邏輯（業務層）。
 *
 * <p>對應需求 §4「使用者可以針對發文新增留言」。
 *
 * <p><b>範圍：</b>需求 §4 原文只有新增這一句，故本服務只有一個方法。
 * 編輯與刪除留言依 {@code SCOPE-BOUNDARY.md} 仍為 Out of Scope。
 *
 * <p><b>讀取留言不在本服務：</b>留言隨發文列表一併帶出（D-13），該讀取歸屬 F004，
 * 由 {@code PostService} 直接呼叫 {@code CommentRepository} 完成——
 * {@code business/package-info.java} 明訂業務層僅能呼叫 data 層，
 * 若改由 {@code PostService} 呼叫本服務，會形成業務層互相呼叫而牴觸該宣告。
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    /**
     * 新增留言。
     *
     * <p>未使用 {@code @Transactional}：僅異動 {@code comment} 單表，
     * 未觸發需求 §6「同時異動多個資料表」的條件
     * （{@code F001-DB.md} §「交易邊界」已盤點，全案唯一的跨表寫入是刪除發文）。
     * <b>刻意不加上以求保險</b>——多餘的交易邊界會讓讀者誤以為此處有跨表異動。
     *
     * <p>目標發文的存在性檢查刻意不在此重做：{@code sp_comment_create} 在單次呼叫內
     * 就完成檢查與寫入，在此層再查一次只是多一次往返，且會讓同一條規則出現兩份實作。
     * 本層只負責把資料層拋出的領域例外原樣往上傳遞。
     *
     * <p><b>已知限制（TECH_DEBT TD-002）：</b>「單次呼叫」不等於「單一交易」——
     * SP 內的檢查與寫入未包在交易中、檢查也未加鎖，兩者之間存在競態窗口；
     * 若窗口內目標發文被軟刪除，會留下一則未刪除的留言掛在已刪除的發文下。
     * 這不是本層加 {@code @Transactional} 能解決的（問題在 SP 內部），
     * 已由 owner 裁決暫不修，記於 {@code memory/TECH_DEBT.md} TD-002。
     *
     * @return 新增的 comment_id
     * @throws com.esunbank.social.common.exception.CommentTargetPostNotFoundException
     *         目標發文不存在或已被軟刪除（ADR-004）
     */
    public Long create(CommentCreateCommand command) {
        return commentRepository.create(
                command.userId(),
                command.postId(),
                command.content());
    }
}

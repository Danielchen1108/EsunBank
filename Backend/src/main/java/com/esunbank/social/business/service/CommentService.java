package com.esunbank.social.business.service;

import org.springframework.stereotype.Service;

import com.esunbank.social.data.repository.CommentRepository;

/**
 * 留言業務邏輯（業務層）。
 *
 * <p>對應題目 §4「使用者可以針對發文新增留言」。
 *
 * <p><b>範圍：</b>題目 §4 原文只有新增這一句，故本服務只有一個方法。
 * 列出／編輯／刪除留言依 {@code SCOPE-BOUNDARY.md} 為 Out of Scope，
 * 「只有新增而沒有列出」是題目的選擇，不是遺漏。
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
     * 未觸發題目 §6「同時異動多個資料表」的條件
     * （{@code F001-DB.md} §「交易邊界」已盤點，全案唯一的跨表寫入是刪除發文）。
     * <b>刻意不加上以求保險</b>——多餘的交易邊界會讓讀者誤以為此處有跨表異動。
     *
     * <p>目標發文的存在性檢查刻意不在此重做：檢查與寫入若分成兩次資料庫往返，
     * 兩者之間發文可能被軟刪除。{@code sp_comment_create} 在單次呼叫內完成兩者，
     * 本層只負責把資料層拋出的領域例外原樣往上傳遞。
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

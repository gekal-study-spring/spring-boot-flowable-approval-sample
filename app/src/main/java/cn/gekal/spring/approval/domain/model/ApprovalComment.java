package cn.gekal.spring.approval.domain.model;

import java.time.LocalDateTime;

/**
 * 承認タスクに残されたコメント。
 *
 * <p>プロセス変数ではなくタスクに紐づけて残すため、多段承認や差戻しがあっても各段階のコメントが上書きされない。
 *
 * @param author 記入者
 * @param message 本文
 * @param at 記入日時
 */
public record ApprovalComment(String author, String message, LocalDateTime at) {}

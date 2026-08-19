package cn.gekal.spring.approval.domain.model;

import java.time.LocalDateTime;

/**
 * 承認者に割り当てられた承認タスク。
 *
 * @param taskId タスクID
 * @param name タスク名（課長承認 / 部長承認）
 * @param role 承認者ロール
 * @param processInstanceId 対象の経費精算申請
 * @param assignee 引き受け済みの担当者。未引き受けなら {@code null}
 * @param createdAt タスク生成日時
 * @param title 申請の件名
 * @param amount 申請金額
 * @param applicantId 申請者ID
 */
public record ApprovalTask(
    String taskId,
    String name,
    ApproverRole role,
    String processInstanceId,
    String assignee,
    LocalDateTime createdAt,
    String title,
    ExpenseAmount amount,
    String applicantId) {

  /** 指定ユーザーがこのタスクを処理できるか。未引き受けか、自分が引き受けている場合のみ許す。 */
  public boolean isOperableBy(String userId) {
    return assignee == null || assignee.equals(userId);
  }
}

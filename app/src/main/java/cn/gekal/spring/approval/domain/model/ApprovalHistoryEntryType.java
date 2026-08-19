package cn.gekal.spring.approval.domain.model;

/** 承認履歴の1件が何を表すか。 */
public enum ApprovalHistoryEntryType {
  /** 申請の起票。 */
  APPLICATION,
  /** 人手の承認タスク（課長承認 / 部長承認）。 */
  APPROVAL_TASK,
  /** 自動処理（基幹システム連携・却下通知・リマインド送信）。 */
  SYSTEM_TASK
}

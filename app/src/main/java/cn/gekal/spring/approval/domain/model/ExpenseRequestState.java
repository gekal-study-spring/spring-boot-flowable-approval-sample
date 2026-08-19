package cn.gekal.spring.approval.domain.model;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 経費精算申請とワークフローの現在状態。
 *
 * @param request 申請内容
 * @param status 進行状況
 * @param currentTaskName 承認待ちのタスク名。完了済みなら {@code null}
 * @param approverId 承認・却下を行ったユーザーID
 * @param approvalComment 承認者コメント
 * @param erpVoucherNo 基幹システムの伝票番号。承認・連携完了後に設定される
 * @param reminderCount 送信済みリマインド回数
 * @param startedAt 申請日時
 * @param endedAt 完了日時。実行中なら {@code null}
 */
public record ExpenseRequestState(
    ExpenseRequest request,
    ExpenseRequestStatus status,
    String currentTaskName,
    String approverId,
    String approvalComment,
    String erpVoucherNo,
    int reminderCount,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {

  public Optional<String> erpVoucherNoOptional() {
    return Optional.ofNullable(erpVoucherNo);
  }
}
